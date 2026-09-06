(function () {
  'use strict';
  const catalog = window.BRIDGE_MUNICIPAL_CATALOG;
  const VERSION = '1.6.0';
  const sourceByItem = new Map();
  (catalog?.rows || []).forEach(row => {
    row.existing.forEach(id => sourceByItem.set(id, row));
    if (row.addTo) sourceByItem.set('municipal-' + row.number, row);
  });
  function sourceRow(itemId) {
    const key = String(itemId || '').split('::')[0];
    return sourceByItem.get(key) || sourceByItem.get(key.split('--')[0]) || null;
  }
  function supplementalWeight(item) {
    const key = String(item.id || item.itemId || '');
    const name = String(item.name || item.itemName || '');
    if (/^incomplete-|^general-(9|10)$/.test(key)) return { weight: 1, reason: 'نقص اطلاعات و قابلیت ردیابی؛ مشابه ردیف ۲ مرجع با کمترین ضریب.' };
    if (/سقوط|شکست|پارگی|کمانش کلی|خستگی|برشی|آب.?شستگی|زیرشویی|ناپایدار|خطر فوری|برق|گاباری|بارکوبی/.test(name)) return { weight: 5, reason: 'پیامد بالقوه برای ایمنی یا مسیر انتقال بار؛ قیاس با ردیف‌های ۱۲، ۱۷، ۵۳، ۵۵ و ۶۶ مرجع.' };
    if (/خوردگی|ترک|نشست|چرخش|جابجایی|جابه|خیز|کمانش|لهیدگی|خردشدگی|مقطع|ریل|مهار|پیش.?تنید/.test(name)) return { weight: 4, reason: 'آسیب عضو یا عملکرد سازه‌ای؛ قیاس با ردیف‌های ۴۸ تا ۵۴ و ۶۵ مرجع.' };
    if (/رنگ|علائم|برچسب|پلاک|آلودگی سطح/.test(name)) return { weight: 2, reason: 'خدمت‌رسانی و خوانایی؛ قیاس با ردیف‌های ۱۳ و ۴۱ مرجع.' };
    return { weight: 3, reason: 'دوام، زهکشی یا عملکرد موضعی؛ قیاس با ردیف‌های ۲۳ تا ۲۵ و ۶۰ مرجع.' };
  }
  function definition(item, category) {
    const id = item.id || item.itemId;
    const row = sourceRow(id);
    const resolvedName=item.name || item.itemName || window.bridgeLegacyChecklistDefinition?.(id)?.item || id;
    const estimate = row ? null : supplementalWeight({...item,name:resolvedName});
    return {
      key: row ? row.id : 'X:' + id,
      weight: row ? row.weight : estimate.weight,
      source: row ? 'VELAYAT.xlsx ' + row.weightCell : 'پیشنهادی نرم‌افزار ۱.۶.۰؛ نیازمند تأیید سازمان',
      sourceType: row ? (row.customInSource ? 'workbook-custom' : 'workbook') : 'proposed',
      rationale: row ? 'ضریب عین فایل ارسالی؛ ' + row.sourceCell : estimate.reason,
      sourceName: row ? row.name : resolvedName,
      sourceNumber: row?.number || null,
      category: category || item.categoryName || '',
    };
  }
  function scoreItem(item) {
    const d = item.scoring || definition(item);
    const status = window.severityId(item.statusId || item.status);
    const knownStatus = window.severityKnown(item.statusId || item.status);
    const applicable = item.applicable !== false;
    const assessed = applicable && item.assessed !== false && knownStatus;
    const base = assessed ? catalog.scores[status] : null;
    return { ...d, status, applicable, assessed, base, penalty: base == null ? null : base * d.weight };
  }
  function municipal(record) {
    const groups = new Map();
    let emergencyCount=0, uninspectableCount=0, pendingCount=0, excludedCount=0, observedCount=0;
    const legacy = record.checklistSchemaVersion !== VERSION && !record.scoringVersion;
    (record.items || []).forEach(item => {
      const data = scoreItem(item);
      if (!data.applicable) { excludedCount++; return; }
      const observed = data.assessed && (!legacy || data.status !== 'none' || item.assessed === true);
      if (!observed) pendingCount++;
      else if (data.status === 'uninspectable') uninspectableCount++;
      else observedCount++;
      if (data.status === 'emergency' && observed) emergencyCount++;
      if (!groups.has(data.key)) groups.set(data.key, { ...data, penalty: null, count: 0, inspected: false, pending: false });
      const group=groups.get(data.key); group.count++;
      group.pending = group.pending || !observed;
      group.inspected = group.inspected || (observed && data.status !== 'uninspectable');
      // A split checklist row or repeated location must not multiply a source
      // row's weight. Retain the worst penalty once for each scoring key.
      if (observed && (group.penalty == null || data.penalty < group.penalty)) {
        group.penalty=data.penalty; group.status=data.status;
      }
    });
    const list=[...groups.values()];
    const sum=xs=>xs.reduce((v,x)=>v+(x.penalty || 0),0);
    const total=sum(list), weightTotal=list.reduce((v,x)=>v+x.weight,0);
    const applicable=observedCount+uninspectableCount+pendingCount;
    return {
      version: VERSION, sourceSha256: catalog.sourceSha256,
      method: 'minimum-per-scoring-key', total: list.some(x=>x.penalty!=null) ? total : null,
      referenceTotal: list.some(x=>x.penalty!=null && x.sourceType!=='proposed') ? sum(list.filter(x=>x.sourceType!=='proposed')) : null,
      supplementalTotal: list.some(x=>x.penalty!=null && x.sourceType==='proposed') ? sum(list.filter(x=>x.sourceType==='proposed')) : null,
      weightTotal, normalizedDeficit: weightTotal && list.some(x=>x.penalty!=null) ? Math.abs(total)/weightTotal : null,
      groups:list, emergencyCount, uninspectableCount, pendingCount, excludedCount, observedCount,
      coverage: applicable ? observedCount/applicable*100 : null,
      complete: applicable>0 && pendingCount===0 && uninspectableCount===0,
      legacy, assessed: applicable-pendingCount,
    };
  }
  const RATINGS = [
    ['0','ازکارافتاده','بسته به علت وضعیت جزء؛ اصلاح‌پذیر نیست و جایگزینی لازم است.'],
    ['1','در آستانه گسیختگی','پل به علت وضعیت جزء بسته است؛ تعمیر یا بهسازی ممکن است بازگشایی را ممکن کند.'],
    ['2','بحرانی','آسیب عمده و اختلال شدید؛ پایش مکرر، محدودیت بار جدی یا اقدام اصلاحی لازم است.'],
    ['3','وخیم','آسیب عمده؛ مقاومت یا عملکرد به‌شدت متأثر است.'],
    ['4','ضعیف','آسیب متوسط گسترده یا عمده موضعی؛ مقاومت یا عملکرد متأثر است.'],
    ['5','متوسط','آسیب متوسط در چند ناحیه؛ مقاومت و عملکرد متأثر نشده است.'],
    ['6','رضایت‌بخش','آسیب جزئی گسترده یا آسیب متوسط موضعی.'],
    ['7','خوب','آسیب جزئی در چند ناحیه.'],
    ['8','بسیار خوب','برخی نواقص ذاتی مصالح یا ساخت؛ نشانه زوال محسوب نمی‌شوند.'],
    ['9','عالی','صرفاً نواقص ذاتی موضعی.'],
  ];
  const COMPONENTS = [
    { id:'deck', code:'B.C.01', name:'دال و کف سازه‌ای عرشه', hint:'سطح بالا، زیر و لبه‌ها؛ خرابی روکش آسفالت به‌تنهایی نمره عرشه را تغییر نمی‌دهد.' },
    { id:'superstructure', code:'B.C.02', name:'روسازه؛ تیرها و سیستم باربر', hint:'وضعیت اعضای باربر؛ برای پل دالی، نمره عرشه و روسازه یکسان است.' },
    { id:'substructure', code:'B.C.03', name:'زیرسازه؛ پایه، کوله و پی', hint:'وضعیت پایه‌ها، کوله‌ها و شالوده؛ اثر آب‌شستگی نیز بررسی شود.' },
    { id:'culvert', code:'B.C.04', name:'آبرو و کالورت', hint:'برای سازه مدفون یا آبرو؛ شامل بدنه و اجزای پی در صورت وجود.' },
    { id:'railing', code:'B.C.05', name:'جان‌پناه و حفاظ سواره‌رو', hint:'حفاظ عبور خودرو؛ نرده عابر مستقل از آن است.' },
    { id:'transition', code:'B.C.06', name:'اتصال حفاظ راه به پل', hint:'ناحیه گذار گاردریل راه به جان‌پناه پل.' },
    { id:'bearings', code:'B.C.07', name:'دستگاه‌های تکیه‌گاهی', hint:'دستگاه و عملکرد آن؛ ع.ا.ب معادل N نیست.' },
    { id:'joints', code:'B.C.08', name:'درزهای عرشه', hint:'توصیف اختصاصی درز؛ برای درز بازِ طراحی‌شده، نبود درزبند به‌خودی‌خود عیب نیست.', descriptions:{
      '5':'چند ناحیه آسیب متوسط.','4':'آسیب متوسط گسترده یا عمده موضعی.','3':'چند ناحیه آسیب عمده.','2':'آسیب عمده گسترده.','1':'درز از کار افتاده و بی‌اثر است.','0':'درز از کار افتاده و خطر ایمنی ایجاد کرده است.'} },
    { id:'channel', code:'B.C.09', name:'آبراهه', hint:'بالادست و پایین‌دست در حد اثر بر پل و راه دسترسی؛ N برای پل غیرآبی.', descriptions:{
      '9':'بدون عیب.','8':'فقط نواقص ذاتی.','5':'آسیب متوسط؛ پل و راه دسترسی تهدید نشده‌اند.','4':'آسیب متوسط گسترده یا عمده موضعی؛ پل یا راه دسترسی تهدید شده است.','3':'آسیب عمده با تهدید جدی پل یا راه دسترسی.','2':'آسیب عمده با تهدید شدید پل یا راه دسترسی؛ پایش و اقدام فوری لازم است.','1':'پل به علت وضعیت آبراهه بسته است؛ اصلاح آبراهه ممکن است بازگشایی را ممکن کند.','0':'پل به علت وضعیت آبراهه بسته و اصلاح‌پذیر نیست؛ جایگزینی پل لازم است.'} },
    { id:'channelProtection', code:'B.C.10', name:'حفاظت آبراهه', hint:'وضعیت و اثربخشی سنگ‌چین، گابیون و سایر حفاظت‌ها؛ مستقل از وضعیت خود آبراهه.', descriptions:{
      '5':'آسیب متوسط؛ عملکرد حفاظت متأثر نیست.','4':'آسیب متوسط گسترده یا عمده موضعی؛ عملکرد حفاظت متأثر است.','3':'آسیب عمده و اختلال جدی عملکرد حفاظت.','2':'آسیب عمده و اختلال شدید حفاظت؛ پایش و اقدام اصلاحی لازم است.','1':'حفاظت از کار افتاده؛ قابل اصلاح است.','0':'حفاظت قابل تعمیر نیست و باید جایگزین شود.'} },
    { id:'scour', code:'B.C.11', name:'آب‌شستگی', hint:'آب‌شستگی مشاهده یا اندازه‌گیری‌شده با تراز بحرانی و سوابق مهندسی مقایسه شود؛ N برای پل غیرآبی.', descriptions:{
      '9':'آب‌شستگی وجود ندارد.','8':'آب‌شستگی ناچیز.','7':'آب‌شستگی جزئی در چند ناحیه.','6':'آب‌شستگی جزئی گسترده یا متوسط موضعی.','5':'آب‌شستگی متوسط؛ مقاومت و پایداری متأثر نیست.','4':'آب‌شستگی متوسط گسترده یا عمده موضعی؛ مقاومت یا پایداری متأثر است.','3':'آب‌شستگی عمده؛ مقاومت یا پایداری به‌شدت متأثر است.','2':'آب‌شستگی عمده با اختلال شدید مقاومت یا پایداری؛ پایش و اقدام فوری لازم است.','1':'پل به علت آب‌شستگی بسته؛ بازگشایی پس از اصلاح ممکن است.','0':'پل به علت آب‌شستگی بسته و اصلاح‌پذیر نیست؛ جایگزینی لازم است.'} },
  ];
  function international(record) {
    const assessment=record.internationalAssessment || {};
    const ratings=assessment.components || {};
    const mode=['bridge','culvert','mixed'].includes(assessment.mode)?assessment.mode:'bridge';
    const required=mode==='culvert'?['culvert']:mode==='mixed'?['deck','superstructure','substructure','culvert']:['deck','superstructure','substructure'];
    // A rating entered outside the selected mode must still govern the result.
    ['deck','superstructure','substructure','culvert'].forEach(key=>{
      const raw=String(ratings[key]?.rating ?? '');
      if(raw && raw!=='U' && raw!=='N' && !required.includes(key))required.push(key);
    });
    const values=[],missing=[];
    required.forEach(key=>{
      const item=ratings[key] || {}, raw=String(item.rating ?? '');
      if (/^[0-9]$/.test(raw) && String(item.note || '').trim()) values.push(Number(raw));
      else if (raw==='N' && String(item.note || '').trim()) { /* Explicitly absent component. */ }
      else missing.push(key);
    });
    const minimum=values.length ? Math.min(...values) : null;
    const complete=missing.length===0 && minimum!==null && Boolean(assessment.reviewer?.trim());
    const condition=minimum==null ? null : minimum>=7?'خوب':minimum>=5?'متوسط':'ضعیف';
    return { version:'FHWA-SNBI-2022', minimum, condition:complete?condition:null, complete, missing,
      reviewer:assessment.reviewer || '', componentRatings:ratings, mode,
      critical:COMPONENTS.some(c=>/^[0-2]$/.test(String(ratings[c.id]?.rating ?? ''))), applicableTo: 'چارچوب پل‌های جاده‌ای آمریکا؛ استفاده مقایسه‌ای در ایران و پل راه‌آهن' };
  }
  function elementCondition(element) {
    const total=Number(element.total), values=['cs1','cs2','cs3','cs4'].map(k=>Number(element[k]));
    if (!Number.isFinite(total) || !(total>0) || values.some((n,i)=>!Number.isFinite(n)||n<0||String(element[['cs1','cs2','cs3','cs4'][i]] ?? '').trim()==='')) return {valid:false};
    const sum=values.reduce((a,b)=>a+b,0);
    if (Math.abs(sum-total)>Math.max(1e-6,total*1e-6)) return {valid:false};
    return {valid:true,total,quantities:values,percentages:values.map(x=>100*x/total),cs34Percent:100*(values[2]+values[3])/total};
  }
  function snapshot(record) {
    return { version:VERSION, calculatedAt:new Date().toISOString(), municipal:municipal(record), international:international(record),
      elements:(record.elementAssessments || []).map(e=>({...e,result:elementCondition(e)})) };
  }
  window.BridgeScoring = { VERSION, sourceRow, definition, supplementalWeight, scoreItem, municipal, international, elementCondition, snapshot, RATINGS, COMPONENTS };
})();
