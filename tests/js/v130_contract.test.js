const fs=require('fs'),assert=require('assert');
const core=fs.readFileSync('app/src/main/assets/js/app-core.js','utf8');
const resilience=fs.readFileSync('app/src/main/assets/js/resilience.js','utf8');
const bridge=fs.readFileSync('app/src/main/assets/js/bridge.js','utf8');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const notifier=fs.readFileSync('app/src/main/java/ir/bridge/maintenance/DateStatusNotifier.java','utf8');

assert(core.includes("checklistSchemaVersion: '1.7.0'"));
assert(core.includes('function checklistHasEnteredData'));
assert(core.includes('چک‌لیست هنوز ورودی ندارد؛ پیش‌نویسی ذخیره نشد'));
assert(resilience.includes('checklistHasEnteredData(items)')&&resilience.includes('emptyDraftDeleteInFlight'));
assert(bridge.includes("send('deleteBatch'"));
assert(html.includes("deleteSelected('defects')"));
assert(!html.includes("deleteSelected('users')"));
assert(notifier.includes('String.format(java.util.Locale.US, "%d", jalali[2])'));
assert(notifier.includes('.setStyle(new Notification.BigTextStyle().bigText(rtlFull))'));
assert(!notifier.includes('تاریخ شمسی " + fullDate'));
console.log('v130_contract.test.js PASS');
