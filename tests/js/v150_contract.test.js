const fs = require('fs');
const assert = require('assert');

const gradle = fs.readFileSync('app/build.gradle', 'utf8');
const activity = fs.readFileSync('app/src/main/java/ir/bridge/maintenance/MainActivity.java', 'utf8');
const bridge = fs.readFileSync('app/src/main/assets/js/bridge.js', 'utf8');

assert(gradle.includes("versionCode 10600"));
assert(gradle.includes("versionName '1.6.0'"));
assert(activity.includes('setWebChromeClient'));
assert(activity.includes('onJsConfirm'));
assert(activity.includes('result.confirm()') && activity.includes('result.cancel()'));
assert(activity.includes('setLayoutDirection(View.LAYOUT_DIRECTION_RTL)'));
assert(bridge.includes('BridgeAndroid.deleteBatch'));
assert(bridge.includes('deleteLocal(x.kind,x.id)'));
console.log('v1.5.0 contract OK');
