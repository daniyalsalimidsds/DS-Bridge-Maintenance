const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const nodes=new Map(),node=id=>{if(!nodes.has(id))nodes.set(id,{value:'',textContent:'',innerHTML:''});return nodes.get(id)};
let target='',message='';
const ctx={window:null,Date,console,faNum:String,esc:String,go(){},setTimeout(){},dbList:()=>[],toast:s=>message=s,
  document:{getElementById:node,querySelector:()=>null},Native:{isNative:()=>true,requestLocation:p=>{target=p.target;return true}}};
ctx.window=ctx;vm.createContext(ctx);
for(const name of ['locations','bridges'])vm.runInContext(fs.readFileSync('app/src/main/assets/js/'+name+'.js','utf8'),ctx);
const result=(token,more={})=>({ok:true,target:token,lat:35,lon:51,capturedAt:Date.now(),...more});
ctx.editBridge('a');ctx.captureBridgeLocation();const stale=target;
ctx.editBridge('b');ctx.captureBridgeLocation();const current=target;
ctx.receiveLocationResult(result(stale));assert.equal(node('bridgeLatitude').value,'');
ctx.receiveLocationResult(result(current));assert.equal(node('bridgeLatitude').value,35);
ctx.captureBridgeLocation();const canceled=target;ctx.clearBridgeLocation();
ctx.receiveLocationResult(result(canceled));assert.equal(node('bridgeLatitude').value,'');
for(const more of [{mock:true},{capturedAt:Date.now()-180000},{lat:null},{lon:''}]){
  ctx.captureBridgeLocation();ctx.receiveLocationResult(result(target,more));
  assert.equal(node('bridgeLatitude').value,'');assert(message.includes('معتبر'));
}
ctx.captureBridgeLocation();const beforeTyping=target;
node('bridgeLatitude').value='34';node('bridgeLatitude').oninput();
ctx.receiveLocationResult(result(beforeTyping));assert.equal(node('bridgeLatitude').value,'34');
ctx.enNum=String;ctx.normalizeFaSearch=String;
ctx.BRIDGE_PROFILE_SCHEMA={sections:[{fields:[{id:'name',label:'نام پل'},{id:'use',label:'کاربری اصلی'}]}]};
ctx.document.querySelectorAll=()=>[{dataset:{bridgeField:'name'},value:'پل آزمون'},{dataset:{bridgeField:'use'},value:'راه'}];
node('bridgeLongitude').value='';ctx.saveBridge();assert(message.includes('هر دو'));
console.log('bridge_gps.test.js PASS');
