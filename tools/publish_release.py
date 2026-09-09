#!/usr/bin/env python3
"""Publish the already signed 1.6.0 delivery; never rebuild or re-sign it."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from urllib.parse import quote
from zipfile import ZipFile

REPOSITORY = "daniyalsalimidsds/DS-Bridge-Maintenance"
TAG = "v1.6.0"
ROOT = Path(__file__).resolve().parents[1]
RELEASE = ROOT / "releases" / TAG
TESTED_COMMIT = "080656b25b5efa971541d966acc792693f4ee187"
TESTED_RUN = 34063041622


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def command(args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, **kwargs).stdout.strip()


def api(endpoint, method="GET", payload=None):
    args = ["gh", "api", endpoint, "--method", method]
    if payload is not None:
        args += ["--input", "-"]
    output = command(args, input=json.dumps(payload) if payload is not None else None)
    return json.loads(output) if output else {}


def validate():
    meta = json.loads((RELEASE / "RELEASE_VERIFICATION.json").read_text())
    assert meta["source_commit"] == TESTED_COMMIT
    assert meta["application_id"] == "ir.bridge.maintenance" and meta["version_code"] == 10600
    apk = RELEASE / "Bridge_Maintenance_v1.6.0.apk"
    bundle = RELEASE / "Bridge_Maintenance_v1.6.0_INSTALL.zip"
    assert sha256(apk) == meta["signed_apk"]["apk_sha256"] == "18896e2b5b1e7a50e110cc953170d58d18076538e59e9c04bb758c551522e1e4"
    assert sha256(bundle) == "d21fb6a36f072eab710ac86847715f5e4f34d712053d5f42fdba191a5bb2538a"
    for line in (RELEASE / "APPLICATION_SHA256SUMS.txt").read_text().splitlines():
        digest, name = line.split("  ", 1)
        path = ROOT / name
        assert path.resolve().is_relative_to(ROOT) and sha256(path) == digest, name
    with ZipFile(bundle) as archive:
        assert archive.testzip() is None
        for name in archive.namelist():
            p = Path(name)
            assert not p.is_absolute() and ".." not in p.parts
            assert p.suffix.lower() not in {".jks", ".keystore", ".p12", ".pfx"}
            assert p.name.lower() not in {"credentials.txt", "keystore.properties", "signing.properties"}
        assert archive.read("Bridge_Maintenance_v1.6.0_INSTALL/" + apk.name) == apk.read_bytes()
    print("Signed delivery hashes, installation ZIP and tested application sources verified.")


def publish():
    assert os.environ.get("GITHUB_REPOSITORY") == REPOSITORY
    assert os.environ.get("GITHUB_REF") == "refs/heads/main", "Publish only from main"
    head = command(["git", "rev-parse", "HEAD"], cwd=ROOT)
    assert head == os.environ["GITHUB_SHA"], "Unexpected checkout"
    run = api(f"repos/{REPOSITORY}/actions/runs/{TESTED_RUN}")
    assert run["head_sha"] == TESTED_COMMIT and run["conclusion"] == "success"
    validate()
    # Check current repository identity and list existing releases explicitly;
    # access errors are never interpreted as an absent release.
    assert api(f"repos/{REPOSITORY}")["full_name"] == REPOSITORY
    releases = json.loads(command(["gh", "api", "--paginate", "--slurp", f"repos/{REPOSITORY}/releases?per_page=100"]))
    existing = next((r for page in releases for r in page if r["tag_name"] == TAG), None)
    if existing is None:
        tag_refs = api(f"repos/{REPOSITORY}/git/matching-refs/tags/{TAG}")
        exact = next((r for r in tag_refs if r["ref"] == f"refs/tags/{TAG}"), None)
        assert exact is None or (exact["object"]["type"] == "commit" and exact["object"]["sha"] == head), "Existing tag points elsewhere"
        existing = api(f"repos/{REPOSITORY}/releases", "POST", {
            "tag_name": TAG, "target_commitish": head,
            "name": "Bridge Maintenance 1.6.0 — بازرسی و نگهداری پل",
            "body": (RELEASE / "RELEASE_NOTES_FA.md").read_text(),
            "draft": True, "prerelease": False,
        })
    # An existing version is never retagged or overwritten. A retry is only
    # accepted for exactly the same published source snapshot and asset bytes.
    assert existing["target_commitish"] == head, "Existing release belongs to another commit"
    rid = existing["id"]
    with tempfile.TemporaryDirectory(prefix="bridge-release-") as directory:
        out = Path(directory)
        for name in ["Bridge_Maintenance_v1.6.0.apk", "Bridge_Maintenance_v1.6.0_INSTALL.zip", "README_INSTALL_FA.html", "RELEASE_VERIFICATION.json", "APPLICATION_SHA256SUMS.txt"]:
            shutil.copyfile(RELEASE / name, out / name)
        source = out / "Bridge_Maintenance_v1.6.0_Source.zip"
        command(["git", "archive", "--format=zip", "--prefix=DS-Bridge-Maintenance-1.6.0/", "--output=" + str(source), head], cwd=ROOT)
        files = sorted(out.iterdir())
        (out / "SHA256SUMS.txt").write_text("".join(sha256(p) + "  " + p.name + "\n" for p in files))
        files = sorted(out.iterdir())
        assets = {a["name"]: a for a in api(f"repos/{REPOSITORY}/releases/{rid}/assets?per_page=100")}
        for path in files:
            if path.name not in assets:
                endpoint = f"https://uploads.github.com/repos/{REPOSITORY}/releases/{rid}/assets?name={quote(path.name)}"
                result = command(["gh", "api", endpoint, "--method", "POST", "--input", str(path), "-H", "Content-Type: application/octet-stream"])
                assets[path.name] = json.loads(result)
        verify = out / "downloaded"
        verify.mkdir()
        # gh handles authenticated private-release downloads and redirects.
        command(["gh", "release", "download", TAG, "--repo", REPOSITORY, "--dir", str(verify)])
        for path in files:
            assert (verify / path.name).is_file() and sha256(verify / path.name) == sha256(path), "Uploaded bytes differ: " + path.name
        if existing["draft"]:
            existing = api(f"repos/{REPOSITORY}/releases/{rid}", "PATCH", {"draft": False, "prerelease": False, "make_latest": "true"})
        result = {
            "release_url": existing["html_url"], "tag": TAG,
            "repository_commit": head, "application_tested_commit": TESTED_COMMIT,
            "application_test_run": TESTED_RUN,
            "assets": [{"name": p.name, "size_bytes": p.stat().st_size, "sha256": sha256(p), "download_url": assets[p.name]["browser_download_url"]} for p in files],
            "download_verification": "All uploaded assets downloaded and SHA256 matched",
        }
        result_path = ROOT / "release-publication" / "publication.json"
        result_path.parent.mkdir(exist_ok=True)
        result_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
        print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    validate() if args.check_only else publish()
