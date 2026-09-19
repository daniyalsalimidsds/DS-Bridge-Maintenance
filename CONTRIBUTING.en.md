# Contributing

Thank you for considering a contribution to Bridge Maintenance.

## Ways to contribute

Useful contributions include reproducible bug reports, Android/device compatibility findings, accessibility and RTL improvements, tests, documentation, engineering review, and carefully justified defect-catalog or scoring changes.

## Development workflow

1. Fork the repository and create a branch for your change.
2. Explain the reason for the change and the expected behavior.
3. Preserve stable bridge, inspection, event, and scoring identifiers unless an explicit migration is included.
4. If a defect name, spreadsheet mapping, or weighting rule changes, update the related audit/method documentation.
5. Add or update tests for changes that affect data, scoring, state transitions, reporting, or safety-related behavior.
6. Check Persian RTL layout, large text, and day/night behavior for visual changes.
7. Run the relevant tests and ensure CI passes before merge.

Build and release details are documented in [docs/BUILD_AND_RELEASE.md](docs/BUILD_AND_RELEASE.md).

## Engineering changes

Do not present a proposed engineering weight, threshold, or interpretation as an approved organizational rule unless there is a citable source and the repository documentation clearly states its status.

Changes that affect historical data must preserve recoverability and backward compatibility or provide a documented migration.

## Security and sensitive data

Use synthetic or de-identified data in issues, pull requests, tests, and screenshots.

Do not upload real bridge backups, sensitive location information, personal signatures, credentials, signing keys, or private operational records.

See [SECURITY.md](SECURITY.md).

## License of contributions

Project-authored code and documentation are MIT-licensed. By submitting a contribution for inclusion, you agree to provide your contribution under the same license while retaining your copyright.

Only submit material you have the right to contribute. Preserve third-party notices and identify the source and compatible license of any external code, image, font, or dataset.
