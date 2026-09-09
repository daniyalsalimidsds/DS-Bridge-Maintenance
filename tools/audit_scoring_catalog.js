#!/usr/bin/env node
// Reproducible review artifact: no workbook or application records are changed.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const root=path.resolve(__dirname,'..'),ctx={window:null};ctx.window=ctx;vm.createContext(ctx);
for(const file of ['js/severity.js','data/municipal-catalog.js','js/scoring.js','data/bridge-profile-schema.js','data/bridge-checklists.js'])
  vm.runInContext(fs.readFileSync(path.join(root,'app/src/main/assets',file),'utf8'),ctx);
const catalog=ctx.BRIDGE_MUNICIPAL_CATALOG, scoring=ctx.BridgeScoring;
const material=new Set(['concrete','steel','prestress','masonry','timber','composite']);
const escape=s=>String(s??'').replaceAll('|',' / ').replaceAll('\n',' ');
const audit=ctx.BRIDGE_CHECKLIST_CATALOG.flatMap(group=>group.items.map(item=>{
  const rows=catalog.rows.filter(row=>row.existing.some(id=>id===item.id||id.startsWith(item.id+'--'))||'municipal-'+row.number===item.id);
  const proposed=material.has(group.id)||!rows.length?scoring.supplementalWeight(item):null;
  return {id:item.id,name:item.name,group:group.name,sourceRows:rows.map(r=>r.id),
    mappedIds:rows.flatMap(r=>r.existing.filter(id=>id===item.id||id.startsWith(item.id+'--')).map(id=>({id,key:r.id,weight:r.weight}))),
    proposedWeight:proposed?.weight??null,proposedReason:proposed?.reason??null,
    proposedScope:proposed?(material.has(group.id)?'فقط اجزایی که شناسه دامنه آن‌ها در نگاشت مرجع وجود ندارد':'تمام رخدادهای این مورد'):null};
}));
const doc=['# ممیزی فهرست و ضرایب ۱.۶.۰','',
  'تولید با `node tools/audit_scoring_catalog.js` از همان کاتالوگ و منطق داخل برنامه. اعداد تکمیلی پیشنهاد نرم‌افزارند؛ مبنای تفسیر در [روش محاسبه](SCORING_METHODS.md) آمده است.','',
  `منبع: ${catalog.sourceFile} — SHA-256: \`${catalog.sourceSha256}\``, '',
  `فهرست مرجع: ${catalog.rows.length} ردیف؛ افزوده: ${catalog.rows.filter(r=>r.addTo).length}؛ نام معادل اصلاح‌شده: ${catalog.rows.filter(r=>r.rename).length}؛ کاتالوگ پایه: ${audit.length} مورد.`, '',
  '## ردیف‌های مرجع و تصمیم تطبیق','',
  '| کلید | عنوان عین فایل | ضریب | سلول عنوان | تصمیم | تعداد شناسه نگاشت |',
  '|---|---|---:|---|---|---:|',
  ...catalog.rows.map(r=>`| ${r.id} | ${escape(r.name)} | ${r.weight} | ${escape(r.sourceCell)} | ${r.addTo?'افزودن':r.rename?'نام معادل عین جدول':'حفظ نام و تفکیک موجود'} | ${r.existing.length} |`),'',
  'شناسه‌های دقیق هر نگاشت و نشانی سلول ضریب در `reference/municipal-catalog.json` قرار دارد. ردیف‌های مصالح دارای شناسه دامنه‌اند؛ یک عنوان در روسازه و زیرسازه ممکن است به دو ردیف متفاوت تعلق داشته باشد. پی و سایر اجزایی که در نگاشت آن ردیف نیستند، خودکار به همان ردیف تحمیل نمی‌شوند.','',
  '## همه موارد نرم‌افزار و مبنای ضریب','',
  'ستون پیشنهادی در مورد مصالح فقط برای دامنه‌های بدون نگاشت مرجع است. اگر شناسه دقیق در مرجع نگاشت شده باشد، ضریب همان ردیف مرجع حاکم است. `reference/scoring-audit.json` شناسه دامنه، کلید، ضریب و دلیل را به صورت ماشینی نگه می‌دارد.','',
  '| شناسه پایه | آسیب | ردیف مرجع | ضریب پیشنهادی برای دامنه بدون مرجع | علت قیاس پیشنهادی |',
  '|---|---|---|---:|---|',
  ...audit.map(i=>`| ${i.id} | ${escape(i.name)} | ${i.sourceRows.join('، ')||'ندارد'} | ${i.proposedWeight??'—'} | ${escape(i.proposedReason||'ضریب عین مرجع')} |`),''];
fs.writeFileSync(path.join(root,'docs/CATALOG_AUDIT.md'),doc.join('\n'));
fs.writeFileSync(path.join(root,'reference/scoring-audit.json'),JSON.stringify({version:'1.6.0',sourceSha256:catalog.sourceSha256,items:audit},null,2)+'\n');
console.log(`Catalog audit: ${audit.length} preserved/current items, ${catalog.rows.length} reference rows.`);
