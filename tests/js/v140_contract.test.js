const fs=require('fs'),assert=require('assert');
const bridge=fs.readFileSync('app/src/main/assets/js/bridge.js','utf8');
const reports=fs.readFileSync('app/src/main/assets/js/reports.js','utf8');
assert(bridge.includes('const primarySent=send')&&bridge.includes('const transferSent=sendTransfer'));
assert(bridge.includes('BridgeAndroid.deleteBatch')&&bridge.includes('transactional from the page'));
assert(!reports.match(/CORE_HEADERS[\s\S]*?['"]تاریخ['"]/));
assert(!reports.match(/PDF_HEADERS[\s\S]*?['"]تاریخ['"]/));
console.log('v1.4.0 contract OK');
