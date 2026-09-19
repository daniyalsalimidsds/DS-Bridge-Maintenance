# Bridge Maintenance 1.6.0

Bridge Maintenance is an open-source, offline-first Android application for structured bridge inspection, defect documentation, condition assessment, maintenance follow-up, and engineering reporting.

The project is maintained by **Daniyal Salimi** and released under the **MIT License**.

## Why this project exists

Bridge inspection data is most useful when observations, evidence, scoring, review history, and reports remain traceable. This project provides an inspectable open-source implementation of that workflow, with an emphasis on offline use, Persian right-to-left field operation, reproducible releases, and documented engineering assumptions.

The repository is intentionally transparent about its scope and limitations. It is a young public open-source project and does not claim broad community adoption or institutional deployment without public evidence.

## Main capabilities

- Bridge inventory and identification.
- Structured inspection workflow with a large defect catalog.
- Five inspection states, including an explicit “unable to inspect” state.
- Photos, GPS/location evidence, notes, and measurements.
- Weighted scoring with documented calculation logic.
- Independent component assessment inspired by FHWA/SNBI concepts, kept separate from the local scoring method.
- PDF, Excel, CSV, and ZIP reporting.
- Persian RTL user interface, dark mode, scalable text, and field guidance.
- Offline Android operation.
- Automated JavaScript, Java, and Android tests.
- CI-based build and QA workflow.
- Versioned releases with APK/source packages, verification metadata, and checksums.

## Engineering transparency

The repository documents scoring behavior, catalog mapping, known limitations, data structure, security assumptions, build/release procedures, and engineering references.

Important: this software is an engineering support tool. It does not replace qualified professional inspection, organizational procedures, or jurisdiction-specific requirements.

Useful references:

- [Scoring methods](docs/SCORING_METHODS.md)
- [Catalog audit](docs/CATALOG_AUDIT.md)
- [Engineering references](docs/ENGINEERING_REFERENCES.md)
- [Known limitations](docs/KNOWN_LIMITATIONS.md)
- [Data dictionary](docs/DATA_DICTIONARY.md)
- [Release QA](docs/RELEASE_QA.md)
- [Roadmap](docs/ROADMAP.md)

## Current release

The current published release is **v1.6.0**.

- [Latest release](https://github.com/daniyalsalimidsds/DS-Bridge-Maintenance/releases/latest)
- [v1.6.0 release](https://github.com/daniyalsalimidsds/DS-Bridge-Maintenance/releases/tag/v1.6.0)

The main branch also contains active development work toward v1.7.0. See the [changelog](docs/CHANGELOG.md) and [roadmap](docs/ROADMAP.md).

## Build and test

Typical checks include:

```bash
node --test tests/js/*.test.js
python3 tools/check_bridge_source.py
./gradlew testDebugUnitTest lintRelease assembleDebug assembleDebugAndroidTest assembleRelease
bash tools/run_direct_instrumentation.sh
```

The instrumentation command requires an Android device or emulator. Exact release evidence should be checked against the CI run associated with the relevant commit.

## Contributing

Contributions are welcome, especially:

- reproducible bug reports,
- Android/device compatibility findings,
- accessibility and RTL improvements,
- test coverage,
- engineering review of inspection logic,
- documentation improvements,
- field-validation feedback using non-sensitive data.

Please read [CONTRIBUTING.md](CONTRIBUTING.md) or the [English contribution guide](CONTRIBUTING.en.md) before opening a pull request.

## Maintainer

The current primary maintainer is **Daniyal Salimi**.

See [MAINTAINERS.md](MAINTAINERS.md) for maintainer responsibilities and project-governance status.

## Security and privacy

Do not publish real bridge backups, sensitive location data, signatures, credentials, signing keys, or private operational information in public issues.

See [SECURITY.md](SECURITY.md).

## License

Original project code and documentation are licensed under the [MIT License](LICENSE). Third-party fonts, libraries, assets, and reference material retain their own licenses and rights; see the repository's dependency and third-party notices.
