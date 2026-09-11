const fs = require('fs');
const vm = require('vm');
const assert = require('assert');

global.window = global;
vm.runInThisContext(fs.readFileSync('app/src/main/assets/js/severity.js', 'utf8'));

assert.strictEqual(severityId('بحرانی'), 'emergency');
assert.strictEqual(severityId('خیلی شدید'), 'emergency');
assert.strictEqual(severityId('شدید'), 'unknown', 'ambiguous legacy severity must fail closed');
assert.strictEqual(severityId('not-a-real-level'), 'unknown');
assert.strictEqual(severityLabel('emergency'), 'اضطراری');
assert.strictEqual(severityLabel('unknown'), 'نیازمند بازبینی شدت');
assert.strictEqual(severityRank('none'), 0);
assert.strictEqual(severityRank('low'), 1);
assert.strictEqual(severityRank('medium'), 2);
assert.strictEqual(severityRank('emergency'), 3);
assert.strictEqual(severityId(''), 'none');
assert.deepStrictEqual(Object.keys(BRIDGE_SEVERITIES), ['none', 'low', 'medium', 'emergency', 'uninspectable']);
assert.deepStrictEqual(Object.values(BRIDGE_SEVERITIES).map(value => value.label),
  ['ندارد', 'کم', 'متوسط', 'اضطراری', 'عدم امکان بازرسی (ع.ا.ب)']);

const migrated = { severity: 'شدید' };
migrateSeverityRecord(migrated);
assert.strictEqual(migrated.severityId, 'unknown');
assert.strictEqual(migrated.legacySeverityValue, 'شدید');
assert.strictEqual(migrated.needsSeverityReview, true);
assert.strictEqual(migrated.severityMigrationVersion, '1.7.0-failsafe-v1');

const overall = inspectionOverall([{ applicable: true, assessed: true, statusId: 'unknown', needsSeverityReview: true }]);
assert.strictEqual(overall.id, 'unknown');
console.log('severity.test.js PASS');
