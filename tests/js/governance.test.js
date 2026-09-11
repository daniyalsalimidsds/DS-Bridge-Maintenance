const fs = require('fs');
const vm = require('vm');
const assert = require('assert');

global.window = global;
global.onRailReady = () => {};
let voids = [];
global.dbList = kind => kind === 'inspectionVoids' ? voids : [];
vm.runInThisContext(fs.readFileSync('app/src/main/assets/js/governance.js', 'utf8'));

const qualified = {
  id: 'inspector-1',
  qualification: {
    status: 'approved', approvedBy: 'manager-2', expiresAt: '2030-12-31',
    canInspect: true, canReview: false, allowedInspectionTypes: ['بازدید ویژه'],
  },
};
assert.deepStrictEqual(Governance.qualificationStatus(qualified, 'بازدید ویژه', false, new Date('2029-01-01T00:00:00Z')), { ok: true });
assert.strictEqual(Governance.qualificationStatus(qualified, 'پس از خرابی', false, new Date('2029-01-01T00:00:00Z')).error, 'inspection-type-not-authorized');
assert.strictEqual(Governance.qualificationStatus({ ...qualified, qualification: { ...qualified.qualification, approvedBy: 'inspector-1' } }, '*').error, 'independent-qualification-approval-required');
assert.strictEqual(Governance.qualificationStatus(qualified, '*', true, new Date('2029-01-01T00:00:00Z')).error, 'review-qualification-required');
assert.strictEqual(Governance.qualificationStatus(qualified, '*', false, new Date('2031-01-01T00:00:00Z')).error, 'qualification-expired');

const critical = {
  discoveredAt: '2029-01-01T00:00:00.000Z', immediateAction: 'مسیر ایمن‌سازی شد',
  operatingRestriction: 'closure', restrictionRationale: 'خطر ناپایداری عضو اصلی',
  notifiedContact: 'مرکز کنترل', notifiedAt: '2029-01-01T00:30:00.000Z',
  ownerId: 'owner-1', dueAt: '2029-01-02T00:00:00.000Z',
};
assert.strictEqual(Governance.criticalCompleteness(critical).ok, true);
const late = { ...critical, notifiedAt: '2029-01-03T02:00:00.000Z' };
assert.strictEqual(Governance.criticalCompleteness(late).ok, false);
assert(Governance.criticalCompleteness(late).missing.includes('notificationLateReason'));
assert.strictEqual(Governance.criticalCompleteness({ ...late, notificationLateReason: 'کوتاه' }).ok, false);
assert.strictEqual(Governance.criticalCompleteness({ ...late, notificationLateReason: 'قطع ارتباط مرکز کنترل در زمان بحران' }).ok, true);
assert.strictEqual(Governance.criticalCompleteness({ ...critical, notifiedAt: '2028-12-31T23:59:00.000Z' }).ok, false);
assert.strictEqual(Governance.criticalCompleteness({ ...critical, dueAt: '2028-12-31T23:59:00.000Z' }).ok, false);
const overdue = { ...critical, discoveredAt: '2020-01-01T00:00:00.000Z', notifiedAt: '2020-01-01T00:30:00.000Z', dueAt: '2020-01-02T00:00:00.000Z' };
assert.strictEqual(Governance.criticalCompleteness(overdue).ok, false);
assert(Governance.criticalCompleteness(overdue).missing.includes('overdueReason'));
assert.strictEqual(Governance.criticalCompleteness({ ...overdue, overdueReason: 'تأخیر مستند و ارجاع فوری به مدیریت بحران' }).ok, true);

assert.strictEqual(Governance.programStatus({ dueDate: '2029-01-01' }, new Date('2029-01-02T00:00:00Z')).id, 'overdue');
assert.strictEqual(Governance.programStatus({ dueDate: '2029-01-20' }, new Date('2029-01-01T00:00:00Z')).id, 'due-soon');
assert.strictEqual(Governance.programStatus({ dueDate: '2029-12-01' }, new Date('2029-01-01T00:00:00Z')).id, 'scheduled');

const approved = { id: 'inspection-1', workflowStatus: 'approved', officialReport: true };
assert.strictEqual(Governance.isOfficialInspection(approved), true);
voids = [{ inspectionId: 'inspection-1', status: 'void' }];
assert.strictEqual(Governance.isOfficialInspection(approved), false);
assert.strictEqual(Governance.workflowLabel(approved), 'باطل‌شده');
assert.strictEqual(Governance.isOfficialInspection({ workflowStatus: 'submitted' }), false);

console.log('governance.test.js PASS');
