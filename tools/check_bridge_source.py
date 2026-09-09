#!/usr/bin/env python3
"""Fail-fast release checks for the Bridge Maintenance Android source tree."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def read(relative: str) -> str:
    path = ROOT / relative
    require(path.is_file(), f"missing {relative}")
    return path.read_text(encoding="utf-8")


def main() -> None:
    html = read("app/src/main/assets/index.html")
    core = read("app/src/main/assets/js/app-core.js")
    severity = read("app/src/main/assets/js/severity.js")
    reports = read("app/src/main/assets/js/reports.js")
    checklist = read("app/src/main/assets/data/bridge-checklists.js")
    gradle = read("app/build.gradle")
    manifest = read("app/src/main/AndroidManifest.xml")
    strings = read("app/src/main/res/values/strings.xml")
    proguard = read("app/proguard-rules.pro")
    native_bridge = read("app/src/main/assets/js/bridge.js")
    main_activity = read("app/src/main/java/ir/bridge/maintenance/MainActivity.java")
    finalizer = read("app/src/main/java/ir/bridge/maintenance/TransactionalFinalizer.java")
    exporter = read("app/src/main/java/ir/bridge/maintenance/ReportExporter.java")
    app_db = read("app/src/main/java/ir/bridge/maintenance/AppDb.java")
    media_store = read("app/src/main/java/ir/bridge/maintenance/AppMediaStore.java")

    require("applicationId 'ir.bridge.maintenance'" in gradle, "wrong applicationId")
    require("ir.railway.maintenance" not in proguard, "legacy package remains in ProGuard rules")
    require("RailNative" not in native_bridge and "RailNative" not in main_activity, "legacy native bridge identity remains")
    require("versionCode 10600" in gradle and "versionName '1.6.0'" in gradle, "wrong version identity")
    require("<string name=\"app_name\">بازرسی پل</string>" in strings, "wrong Persian app name")
    require("android.permission.INTERNET" not in manifest, "runtime internet permission must be absent")
    require("شرکت مهندسین مشاور هگزا" in html and "شرکت مهندسین مشاور هگزا" in core, "organization name is inconsistent")
    require(html.count("Powered By Daniyal Salimi") >= 2, "creator attribution must appear on splash and About")
    splash = ROOT / "app/src/main/assets/veresk_loading.jpg"
    require(splash.is_file() and splash.stat().st_size > 100_000, "Veresk splash photograph is missing or invalid")
    require(splash.read_bytes()[:2] == b"\xff\xd8", "Veresk splash asset must be a JPEG")

    removed_ids = [
        "region", "fromStation", "toStation", "lineType", "locationType", "kmStart",
        "kmEnd", "kmDir", "speed", "lineClass", "weather",
    ]
    for field_id in removed_ids:
        require(f'id="{field_id}"' not in html, f"removed field still exists: {field_id}")

    require('id="bridges"' in html and 'id="bridgeForm"' in html, "bridge registry pages are missing")
    require('id="inspectionBridge"' in html, "bridge selector is missing from inspection form")
    require("data/bridge-profile-schema.js" in html and "data/bridge-checklists.js" in html, "dynamic schema scripts are not loaded")

    for value in ["none", "low", "medium", "emergency", "ندارد", "کم", "متوسط", "اضطراری"]:
        require(value in severity, f"severity scale is incomplete: {value}")
    require("very_severe: {" not in severity, "obsolete five-level severity definition remains")

    schema_path = ROOT / "app/src/main/assets/data/bridge-profile-schema.js"
    schema_text = schema_path.read_text(encoding="utf-8").strip()
    prefix = "window.BRIDGE_PROFILE_SCHEMA = Object.freeze("
    require(schema_text.startswith(prefix) and schema_text.endswith(");"), "profile schema wrapper is invalid")
    schema = json.loads(schema_text[len(prefix):-2])
    fields = [field for section in schema["sections"] for field in section["fields"]]
    require(len(schema["sections"]) == 20, "Excel-derived profile must have 20 sections")
    require(len(fields) == 60, "Excel-derived profile must have 60 fields")
    require(all(not field.get("default") for field in fields), "sample workbook values must not become form defaults")
    require(next(field for field in fields if field["label"] == "نام پل")["required"], "bridge name must be required")
    require(next(field for field in fields if field["label"] == "کاربری اصلی")["required"], "bridge use must be required")

    workbook = ROOT / "reference/شناسنامه_فنی_پل_نشریه_367.xlsx"
    require(workbook.is_file(), "reference Excel workbook is missing")
    require(hashlib.sha256(workbook.read_bytes()).hexdigest() == schema["sourceSha256"], "profile schema does not match the bundled Excel workbook")

    require(checklist.count("category('") >= 35, "engineering checklist catalog is not sufficiently broad")
    for category_id in ["concrete", "steel", "road_pavement", "rail", "rail_weld", "rail_fasteners", "rail_ballast", "rail_geometry", "rail_safety"]:
        require(f"category('{category_id}'" in checklist, f"dynamic checklist category missing: {category_id}")
    require("checklistsForBridge" in checklist and "bridgeProfileTags" in checklist, "profile-dependent checklist selection is missing")
    require("bridgeProfileEntryActive" in checklist, "stale cable-only profile values are not suppressed")
    for token in ["materialScopes", "ballasted-track", "direct-track", "road-pavement", "itemApplies"]:
        require(token in checklist, f"fine-grained dynamic checklist rule is missing: {token}")
    for token in ["VERSIONED_ITEM_CATEGORY_IDS", "LEGACY_V11_CATEGORIES", "bridgeLegacyChecklistDefinition"]:
        require(token in checklist, f"v1.1 checklist compatibility guard is missing: {token}")
    require("checklistSchemaVersion: '1.6.0'" in core, "new inspections must carry a checklist schema version")
    require("scheduleMarqueeRefresh" in core, "responsive checklist Marquee refresh is missing")
    require("signatureAttachment" in finalizer and "Inspector signature is required" in finalizer, "native finalizer must require the inspector signature")
    require("does not match signature signer" in finalizer, "manual inspector/signature consistency check is missing")
    require("Incomplete emergency defect finalization" not in finalizer, "removed visit actions still block emergency finalization")
    require("DeleteResult deleteMany" in app_db and "deleteFiles(List<AppDb.MediaRecord>" in media_store, "transactional deletion/media cleanup is missing")
    require('<receiver android:name=".DateStatusNotifier" android:exported="false">' in manifest, "date receiver must accept only system/same-app delivery")

    for header in ["نام پل", "کد پل", "کاربری پل", "موقعیت مکانی پل", "مختصات آسیب", "سطح آسیب", "نام تصویر"]:
        require(header in reports, f"report output is missing: {header}")
    require("'مختصات پل'" not in reports.split("const CORE_HEADERS",1)[1].split("];",1)[0], "bridge GPS must not remain in checklist columns")
    for artifact in ["data/bridges.json", "bridge-inspection-report.csv", "bridge-inspection-report.xlsx", "bridge-inspection-report.pdf"]:
        require(artifact in exporter, f"ZIP/export artifact missing: {artifact}")
    require("xl/styles.xml" in exporter and "sheet3.xml" in exporter and "ReportLayout" in exporter, "professional adaptive report formatting is missing")
    require('"GPS پل"' not in exporter and '"اقدام / مسئول"' not in exporter, "removed legacy report columns remain in exporter")
    for removed in ["'نام پل'", "'کد پل'", "'کاربری پل'", "'نوع بازدید'"]:
        require(removed not in reports.split("const CORE_HEADERS", 1)[1].split("];", 1)[0], f"repeated checklist column remains: {removed}")
    require("function checklistHasEnteredData" in core and "چک‌لیست هنوز ورودی ندارد" in core, "empty inspections must not become drafts")
    require("case\"deleteBatch\"" in main_activity and "send('deleteBatch'" in native_bridge, "deletion must use the reliable primary WebMessage channel")
    require("WebChromeClient" in main_activity and "onJsConfirm" in main_activity, "native WebView confirmation dialog is missing")

    forbidden_suffixes = {".jks", ".keystore", ".p12", ".pfx"}
    require(not any(path.suffix.lower() in forbidden_suffixes for path in ROOT.rglob("*")), "a private signing key is committed in source")
    require(not (ROOT / "app/src/main/assets/regions").exists(), "obsolete railway regional assets remain")
    require(not (ROOT / "app/src/main/assets/js/regions.js").exists(), "obsolete railway region script remains")

    print("BRIDGE SOURCE GATE PASS")


if __name__ == "__main__":
    main()
