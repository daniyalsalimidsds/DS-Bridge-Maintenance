const fs=require('fs'),vm=require('vm'),assert=require('assert');
const ctx={console,Date,Set,Blob:function(){},URL:{},document:{},window:null};ctx.window=ctx;ctx.dbList=()=>[];ctx.bridgeName=b=>b.name||'';ctx.bridgeCode=b=>b.code||'';ctx.bridgeUse=b=>b.use||'';vm.createContext(ctx);
for(const file of ['js/severity.js','data/municipal-catalog.js','js/scoring.js','data/bridge-profile-schema.js','data/bridge-checklists.js','js/reports.js'])vm.runInContext(fs.readFileSync('app/src/main/assets/'+file,'utf8'),ctx);
const fields=ctx.BRIDGE_PROFILE_SCHEMA.sections.flatMap(s=>s.fields),id=label=>fields.find(f=>f.label===label).id;
const bridge={id:'b1',name:'پل نمونه',code:'BR-01',use:'راه',location:{lat:35.7,lon:51.4},profile:{[id('نام پل')]:'پل نمونه',[id('کد پل')]:'BR-01',[id('کاربری اصلی')]:'راه',[id('مصالح اَبَرسازه')]:'بتن درجا - بتن مسلح'}};
const record={id:'i1',no:'B-14050101-001',jdate:'۱۴۰۵/۰۱/۰۱',bridgeId:'b1',bridgeName:'پل نمونه',bridgeCode:'BR-01',bridgeUse:'راه',bridgeSnapshot:bridge,inspector:'بازرس',visitType:'بازدید چشمی مستمر',overall:'low',items:[{itemId:'concrete-1',code:'3.1.1',statusId:'low',note:'ترک مویی'}]};
const rows=ctx.bridgeReportRows([record]);
assert(rows.length>10);assert.strictEqual(rows[0].length,10);assert(rows.every(row=>row.length===rows[0].length));
const data=rows.find(row=>typeof row[0]==='number');assert(data);
const summary=rows.find(row=>row[3]==='نمره منفی کلی پل');assert.equal(summary[7],-40);
assert(!rows[0].includes('نام تصویر'));
for(const header of ['گروه بازرسی','سطح آسیب','ضریب اهمیت','نمره منظورشده'])assert(rows[0].includes(header),header);
for(const removed of ['نام پل','کد پل','کاربری پل','نوع بازدید'])assert(!rows[0].includes(removed),removed);
assert(!rows[0].includes('مختصات پل'));assert(!rows[0].includes('اقدام اصلاحی'));assert(!rows[0].includes('مسئول پیگیری'));assert(!rows[0].includes('شماره گزارش'));assert(!rows[0].includes('بازرس'));
for(const removed of ['ناحیه راه‌آهن','ایستگاه مبدا','ایستگاه مقصد','کیلومتر شروع','کیلومتر پایان','آب و هوا'])assert(!rows[0].includes(removed),removed);
assert(!data.includes('پل نمونه'));assert(data.includes('کم'));
const payload=ctx.bridgeReportPayload([record]);assert.strictEqual(payload.meta.organization,'شرکت مهندسین مشاور هگزا');assert.strictEqual(payload.bridges.length,1);assert.deepStrictEqual(Array.from(payload.criticalFindings),[]);assert(payload.profileSchema.sourceSha256);assert(payload.pdfRows[0].length===10);assert(payload.profileRows.some(r=>r[0]==='موقعیت مکانی پل'));assert(payload.reportRows.some(r=>r[0]==='نام بازرس'&&r[1]==='بازرس'));assert(payload.reportRows.some(r=>r[0]==='نام پل'&&r[1]==='پل نمونه'));assert(payload.reportRows.some(r=>r[0]==='کد پل'&&r[1]==='BR-01'));assert(payload.reportRows.some(r=>r[0]==='کاربری پل'&&r[1]==='راه'));assert(payload.reportRows.some(r=>r[0]==='نوع بازدید'&&r[1]==='بازدید چشمی مستمر'));assert(payload.reportRows.some(r=>r[0]==='کل موارد دارای آسیب'&&r[1]==='1'));
const sorted=ctx.bridgeReportRows([{...record,items:[
  {itemId:'concrete-1',code:'1.1',statusId:'none',assessed:true},
  {itemId:'concrete-2',code:'1.2',statusId:'low'},
  {itemId:'concrete-3',code:'1.3',statusId:'emergency'},
  {itemId:'concrete-4',code:'1.4',statusId:'medium'},
]}]).filter(row=>typeof row[0]==='number').map(row=>row[4]);
assert.strictEqual(sorted.join('|'),'اضطراری|متوسط|کم|ندارد');
const historical=ctx.bridgeReportRows([{...record,items:[{itemId:'concrete-1',statusId:'none'}]}]);
assert.equal(historical.find(row=>typeof row[0]==='number')[7],'');
assert.equal(rows.at(-1)[3],'نمره منفی کلی پل');
const zero=ctx.bridgeReportRows([{...record,internationalAssessment:{components:{deck:{rating:0,note:'پل بسته؛ جایگزینی لازم است'}}}}]);
assert(zero.find(row=>row[3].startsWith('B.C.01'))[7].startsWith('0 •'));
console.log('report_payload.test.js PASS');
