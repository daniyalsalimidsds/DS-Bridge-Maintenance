# v1.7.0 Phase A — P0 Safety and Data-Integrity QA Record

## Scope and release status

Phase A resolves the nine P0 findings in the 65-page audit. It is a development checkpoint, not a v1.7.0 release. The package remains at the v1.6.0 build identity until the final release gate, and P1/P2/P3 work remains open in the master matrix.

## Implemented controls

| Control | Implementation | Acceptance evidence |
|---|---|---|
| Independent QC | Native `draft/returned → submitted → approved/returned` transitions; inspector and reviewer must differ; approval alone allocates an official number | `GovernanceInstrumentationTest.independentQcIssuesUniqueNumberAndOfficialRecordsCannotBeDeleted` |
| Authentication and RBAC | Offline PIN/passphrase enrollment, PBKDF2-HMAC-SHA256 with per-user salt, constant-time verification, lockout, 15-minute session, role checks and lifecycle lock; native entity writes/deletes, media capture, report/bridge export and backup operations require an authenticated and, where applicable, authorized session | `credentialsAreSaltedLockedAndRoleChecked`; UI bootstrap-login and unauthenticated-write persistence regressions |
| Inspector/signature identity | Read-only inspector name from the authenticated session; signer ID/name, inspection-owned PNG, field hash and time binding; returned-record identity fields cannot be overwritten through the generic bridge, reassignment requires a recorded reason, and resubmission requires a newly captured signature | `signatureMustBelongToAuthenticatedInspectorAndInspection`; `returnedInspectionReassignmentRequiresReasonAndIsAudited` |
| Critical Finding | Exactly one complete case for every emergency occurrence; immediate action, restriction/rationale, notification, owner, deadline, acknowledgment, interim/resolution evidence, overdue reason/status and independent closure | `emergencySubmissionRequiresCompleteLinkedCriticalCaseAndIndependentClosure`; JavaScript timeline/overdue tests |
| Record retention | Submitted, returned and approved inspections, related defects, reviews, voids, revisions, critical cases, programs, users and audit events cannot be physically deleted; the generic bridge cannot downgrade/overwrite submitted or approved inspections; correction creates a new linked draft | governance deletion/void/correction assertions, bridge source contract and delete persistence regression |
| Inspection program | Persistent interval basis, due date, risk override/reason, overdue/late reason and reminder; approval fulfills the current override and calculates the next cycle | program assertions in the independent-QC test; `Governance.programStatus` tests |
| Qualification | Discipline, degree, experience, training, certificate, expiry, allowed inspection types, inspect/review flags and independent approver; enforced natively for both inspection and QC scope | qualification/expiry/RBAC and limited-scope-reviewer instrumentation assertions |
| Fail-safe severity | Unknown legacy or mistyped values map to `unknown`, preserve the source value, set `needsSeverityReview`, and block submission; ambiguous `شدید` is not guessed | `severity.test.js`; v2→v3 migration test |
| Unique report identity | UUID remains internal identity; `report_registry.report_no` and `inspection_id` are UNIQUE; the app's single native writer serializes allocation inside the approval transaction; duplicate/missing legacy numbers receive non-colliding migration numbers while retaining the legacy value | governance two-report assertion; v2→v3 duplicate migration test |
| Audit integrity | Native append-only table with authenticated actor, reason/revision/time and SHA-256 predecessor chain; SQLite UPDATE/DELETE triggers; verified full-restore exception | `auditChainRejectsMutationAndSurvivesVerifiedRestore` |
| Official exports | Native gate accepts only exact, approved, registered, non-void inspection records and exact linked Critical Finding set; exporter injects identity/QC/hash and safety-case details into PDF/XLSX/CSV and the complete ZIP | governance tamper/omission tests; `ReportExportInstrumentationTest` |

## Database migration and compatibility

Schema version advances from 2 to 3 without deleting the generic entity or media stores. Migration adds credentials, report sequences/registry and the append-only audit table. It then:

1. moves legacy audit rows into the verified hash chain;
2. normalizes only unambiguous severity aliases and preserves unknown source text;
3. registers legacy final reports, resolving duplicate or missing display numbers with `B-MIG-*` identifiers while retaining `legacyReportNo`;
4. marks pre-governance final reports `legacy-unverified`, so they can be reproduced only from an exact stored record;
5. preserves existing bridge, inspection, defect, settings, reminder and media content.

Credentials are intentionally excluded from backup and restore. After a restore, the database retains user identities but requires local PIN enrollment again. This avoids exporting password verifiers and prevents restored credentials from silently authenticating on another device.

## Verification run

| Check | Result at local checkpoint |
|---|---|
| `node --test tests/js/*.test.js` | PASS — 25 tests, 0 failed |
| JavaScript syntax (`node --check`) | PASS — all app asset/data JavaScript files |
| `python3 tools/check_bridge_source.py` | PASS |
| `node tools/audit_scoring_catalog.js` | PASS — 497 current items and 79 reference rows |
| whitespace/patch validation | PASS — `git diff --check` |
| Local Gradle/Android build | Not run: Gradle 8.9 is not cached and this runtime cannot reach the distribution host |
| GitHub Actions Android build, lint and API 30 emulator | Pending until this checkpoint is pushed; the run URL and result must be added before Phase A is closed |

No APK or release is produced by this phase. A failed CI build or emulator test reopens the affected matrix rows and must be fixed before starting Phase B.
