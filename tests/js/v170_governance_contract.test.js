const fs = require('fs');
const assert = require('assert');

const read = path => fs.readFileSync(path, 'utf8');
const html = read('app/src/main/assets/index.html');
const bridge = read('app/src/main/assets/js/bridge.js');
const core = read('app/src/main/assets/js/app-core.js');
const reports = read('app/src/main/assets/js/reports.js');
const main = read('app/src/main/java/ir/bridge/maintenance/MainActivity.java');
const db = read('app/src/main/java/ir/bridge/maintenance/AppDb.java');
const credentials = read('app/src/main/java/ir/bridge/maintenance/CredentialManager.java');
const governance = read('app/src/main/java/ir/bridge/maintenance/InspectionGovernance.java');
const audit = read('app/src/main/java/ir/bridge/maintenance/AuditLog.java');
const exporter = read('app/src/main/java/ir/bridge/maintenance/ReportExporter.java');

assert(html.includes('id="authModal"') && html.includes('id="sessionButton"'));
assert(html.includes('id="reviews"') && html.includes('id="criticalFindings"') && html.includes('id="programs"'));
assert(html.indexOf('js/governance.js') > html.indexOf('js/engineering.js'));
assert(!html.includes("deleteSelected('inspections')") && !html.includes("deleteSelected('users')"));
assert(core.includes('ارسال برای کنترل کیفیت') || html.includes('ارسال برای کنترل کیفیت مستقل'));
assert(core.includes('window.isOfficialInspection?.(item)'));
assert(reports.includes('خروجی رسمی فقط برای گزارش تأییدشده و باطل‌نشده'));

assert(bridge.includes("request('submitInspection'") && bridge.includes("request('reviewInspection'"));
assert(main.includes('legacy-finalization-disabled'));
assert(main.includes('BRIDGE_ENTITY_KINDS') && main.includes('bridgeSaveAllowed'));
assert(main.includes('credentialManager.requireAuthenticated();') && main.includes('sameJsonField(existing,value,key)'));
assert(db.includes('DB_VERSION = 3'));
assert(db.includes('report_no TEXT NOT NULL UNIQUE'));
assert(db.includes('Protected record cannot be physically deleted'));
assert(db.includes('sql.delete("credentials"'));
assert(credentials.includes('PBKDF2WithHmacSHA256') && credentials.includes('310_000'));
assert(credentials.includes('MessageDigest.isEqual'));
assert(governance.includes('independent-reviewer-required'));
assert(governance.includes('critical-case-required-for-every-emergency'));
assert(governance.includes('duplicate-critical-case') && governance.includes('critical-overdue-reason-required'));
assert(governance.includes('signature-must-be-recaptured-after-return'));
assert(governance.includes('sameEntitySet(suppliedCritical'));
assert(governance.includes('snapshotBundle'));
assert(audit.includes('previous_hash') && audit.includes('event_hash'));
assert(audit.includes('static boolean verify'));
assert(audit.includes('audit_events_no_update') && audit.includes('audit_events_no_delete'));
assert(exporter.includes('data/critical-findings.json') && exporter.includes('زنجیره هویت و تأیید'));

console.log('v170_governance_contract.test.js PASS');
