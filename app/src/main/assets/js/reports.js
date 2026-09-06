(function () {
  'use strict';

  // Inspector and report identity intentionally live in «مشخصات گزارش» and are
  // not repeated as checklist columns. Bridge GPS lives only in «مشخصات پل».
  // Bridge identity and visit metadata live in the report/bridge specification
  // sections. Keeping them out of the checklist table makes exported files
  // compact and prevents the same value being repeated on every defect row.
  const CORE_HEADERS = [
    'ردیف', 'کد رخداد', 'گروه بازرسی', 'مورد بازرسی',
    'سطح آسیب', 'ضریب اهمیت', 'کلید امتیاز', 'نمره منظورشده', 'مختصات آسیب', 'توضیحات', 'نام تصویر',
  ];
  const PDF_HEADERS = [
    'ردیف', 'کد', 'گروه', 'مورد بازرسی',
    'سطح آسیب', 'ضریب', 'کلید امتیاز', 'نمره', 'GPS آسیب', 'توضیحات', 'نام تصویر',
  ];

  function profileFields() {
    return (window.BRIDGE_PROFILE_SCHEMA?.sections || []).flatMap(section => (section.fields || []).map(field => ({
      ...field, sectionId: section.id, sectionTitle: section.title, exportLabel: `${section.title} — ${field.label}`,
    })));
  }
  function imageNames(list) { return (list || []).map(photo => photo?.name || photo?.fileName || '').filter(Boolean).join('، '); }
  function safePathPart(value) { return String(value || '').trim().replace(/[\\/:*?"<>|\r\n]/g, '_').replaceAll('..', '_').slice(0, 100); }
  function itemOccurrenceCode(item) {
    if (item?.code) return String(item.code);
    return typeof occurrenceCode === 'function' ? occurrenceCode(item?.itemId, item?.occurrenceIndex || 1) : String(item?.occurrenceIndex || 1);
  }
  function imageCell(record, item, list) {
    const photo = (list || [])[0], name = photo?.name || photo?.fileName || '';
    if (!name) return '';
    const visit = safePathPart(`${record.no || 'visit'}_${record.jdate || ''}_${record.bridgeCode || ''}_${record.bridgeName || ''}`);
    return `LINK|../photos/${visit}/${safePathPart(itemOccurrenceCode(item))}/${safePathPart(name)}|${imageNames(list)}`;
  }
  function gpsLocation(location) {
    if (!location || location.lat==null || location.lon==null || !Number.isFinite(Number(location.lat)) || !Number.isFinite(Number(location.lon))) return '';
    const base = `${Number(location.lat).toFixed(6)}, ${Number(location.lon).toFixed(6)}`;
    return location.accuracyM!=null && Number.isFinite(Number(location.accuracyM)) ? `${base} | ±${Number(location.accuracyM).toFixed(1)} m` : base;
  }
  function compareCodes(first, second) {
    return typeof window.compareChecklistCodes === 'function'
      ? window.compareChecklistCodes(first, second)
      : String(first || '').localeCompare(String(second || ''), 'fa', { numeric: true });
  }
  function bridgeForRecord(record) {
    return record.bridgeSnapshot || dbList('bridges').find(bridge => bridge.id === record.bridgeId) || {
      id: record.bridgeId || '', name: record.bridgeName || '', code: record.bridgeCode || '', use: record.bridgeUse || '',
      location: record.bridgeLocation || null, profile: {},
    };
  }
  function definitionsFor(record) {
    const categories = window.checklistsForBridge?.(bridgeForRecord(record)) || [];
    const map = new Map();
    categories.forEach(category => (category.items || []).forEach(item => map.set(item.id, { category: category.name, item: item.name })));
    return map;
  }
  function legacyDefinition(itemId) {
    const legacy = window.bridgeLegacyChecklistDefinition?.(itemId);
    if (legacy) return legacy;
    for (const category of window.BRIDGE_CHECKLIST_CATALOG || []) {
      const item = (category.items || []).find(entry => entry.id === itemId);
      if (item) return { category: category.name, item: item.name };
    }
    return { category: '', item: itemId || '' };
  }
  function severitySort(a, b) {
    const rank = { emergency: 0, uninspectable: 1, medium: 2, low: 3, none: 4 };
    const ar = rank[severityId(a?.statusId || a?.status || 'none')] ?? 3;
    const br = rank[severityId(b?.statusId || b?.status || 'none')] ?? 3;
    return ar - br || compareCodes(itemOccurrenceCode(a), itemOccurrenceCode(b));
  }

  function profileRowsFor(records) {
    const rows = [['مشخصات پل', 'مقدار']], seen = new Set();
    (records || []).forEach(record => {
      const bridge = bridgeForRecord(record);
      const key = (bridge?.id || `${record.bridgeName}|${record.bridgeCode}`)+'|'+JSON.stringify(bridge.profile || {})+'|'+JSON.stringify(bridge.location || null);
      if (!key || seen.has(key)) return;
      seen.add(key);
      rows.push(
        ['نام پل', record.bridgeName || bridgeName(bridge)],
        ['کد پل', record.bridgeCode || bridgeCode(bridge)],
        ['کاربری پل', record.bridgeUse || bridgeUse(bridge)],
        ['موقعیت مکانی پل', gpsLocation(record.bridgeLocation || bridge.location)],
      );
      profileFields().forEach(field => {
        const active = window.bridgeProfileSectionActive?.(bridge, field.sectionId) !== false
          && window.bridgeProfileEntryActive?.(bridge, { label: field.label, value: bridge.profile?.[field.id] }) !== false;
        const value = active || /وجود/.test(field.label) ? String(bridge.profile?.[field.id] ?? '') : '';
        rows.push([field.exportLabel, value]);
      });
      rows.push(['', '']);
    });
    return rows;
  }

  function reportRowsFor(records, settings) {
    const counts = { emergency: 0, medium: 0, low: 0, uninspectable:0 };
    (records || []).flatMap(record => record.items || []).forEach(item => {
      if(item.applicable===false || item.assessed===false)return;
      const id = severityId(item.statusId || item.status || 'none');
      if (Object.prototype.hasOwnProperty.call(counts, id)) counts[id] += 1;
    });
    const inspectors = [...new Set((records || []).map(record => String(record.inspector || '').trim()).filter(Boolean))];
    const bridges = [...new Set((records || []).map(record => record.bridgeName || bridgeName(bridgeForRecord(record))).filter(Boolean))];
    const bridgeCodes = [...new Set((records || []).map(record => record.bridgeCode || bridgeCode(bridgeForRecord(record))).filter(Boolean))];
    const bridgeUses = [...new Set((records || []).map(record => record.bridgeUse || bridgeUse(bridgeForRecord(record))).filter(Boolean))];
    const visitTypes = [...new Set((records || []).map(record => String(record.visitType || '').trim()).filter(Boolean))];
    const generatedJ = typeof nowDates === 'function' && typeof jfmt === 'function' ? jfmt(nowDates().j) : '';
    const damaged = counts.emergency + counts.medium + counts.low;
    return [
      ['مشخصات گزارش', 'مقدار'],
      ['نام گزارش', 'گزارش نگهداری و بازرسی چشمی پل'],
      ['تاریخ ثبت گزارش', generatedJ],
      ['نام پل', bridges.join('، ')],
      ['کد پل', bridgeCodes.join('، ')],
      ['کاربری پل', bridgeUses.join('، ')],
      ['نوع بازدید', visitTypes.join('، ')],
      ['تعداد بازدیدهای گزارش', String((records || []).length)],
      ['کل موارد دارای آسیب', String(damaged)],
      ['آسیب اضطراری', String(counts.emergency)],
      ['آسیب متوسط', String(counts.medium)],
      ['آسیب کم', String(counts.low)],
      ['عدم امکان بازرسی',String(counts.uninspectable)],
      ['نسخه روش محاسبه','۱.۶.۰؛ کمترین امتیاز هر کلید، بدون چندبارشماری رخدادها'],
      ['نام بازرس', inspectors.join('، ')],
      ['نام سازمان', settings.orgName || 'شرکت مهندسین مشاور هگزا'],
      ['واحد سازمانی', settings.unitName || ''],
      ['مقیاس شدت', 'اضطراری | متوسط | کم | ندارد | ع.ا.ب'],
    ];
  }

  function scoreSummaryRows(record, width) {
    const result=record.scores || window.BridgeScoring?.snapshot(record);
    if(!result)return [];
    const m=result.municipal, f=result.international;
    const pairs=[
      ['نمره منفی کلی پل',m.total ?? 'محاسبه نشده'],
      ['سهم فهرست مرجع شهرداری',m.referenceTotal],['سهم آسیب‌های تکمیلی؛ ضرایب پیشنهادی',m.supplementalTotal],
      ['کسری وزنی سفارشی (۰ تا ۱۰۰؛ درصد سلامت نیست)',m.normalizedDeficit==null?'نامشخص':Number(m.normalizedDeficit.toFixed(2))],
      ['پوشش مشاهده (درصد رخدادها)',m.coverage==null?'نامشخص':Number(m.coverage.toFixed(2))],
      ['عدم امکان بازرسی / بررسی‌نشده',m.uninspectableCount+' / '+m.pendingCount],
      ['وضعیت داده‌ها',m.legacy?'بازتحلیل سوابق قدیمی؛ پوشش نامعلوم':m.complete?'پوشش کامل موارد قابل اعمال':'پوشش ناقص؛ نیازمند بازدید تکمیلی'],
      ['FHWA/SNBI — جمع‌بندی کمی و کیفی',f.complete?f.minimum+' از ۹ — '+f.condition:'ارزیابی ناقص؛ جمع‌بندی صادر نشده'],
      ['FHWA/SNBI — ارزیاب',f.reviewer || 'ثبت نشده'],
    ];
    window.BridgeScoring.COMPONENTS.forEach(c=>{
      const x=f.componentRatings[c.id];
      pairs.push([c.code+' — '+c.name,(x?.rating && x.rating!=='U'?x.rating:'ارزیابی نشده')+(x?.note?' • '+x.note:'')]);
    });
    (result.elements || []).forEach(e=>{
      pairs.push(['عنصر: '+e.name+' — '+e.unit,e.result.valid?'کل: '+e.total+' | CS1: '+e.cs1+' | CS2: '+e.cs2+' | CS3: '+e.cs3+' | CS4: '+e.cs4+' | CS3+CS4: '+e.result.cs34Percent.toFixed(2)+'٪':'ارزیابی مقدار نامعتبر']);
      pairs.push(['مرجع و شواهد عنصر',e.reference || 'ثبت نشده']);
    });
    pairs.push(['قاعده جمع','هر کلید امتیاز یک‌بار و با شدیدترین رخداد محاسبه می‌شود. ع.ا.ب معادل سالم نیست.']);
    pairs.push(['شناسایی روش','۱.۶.۰ | VELAYAT.xlsx | '+window.BRIDGE_MUNICIPAL_CATALOG?.sourceSha256]);
    return pairs.map(([label,value])=>{
      const row=Array(width).fill('');row[3]=label;row[7]=value;return row;
    });
  }
  function groupedRows(records, headers) {
    const rows=[headers.slice()];let rowNumber=0;
    (records || []).forEach(record=>{
      const definitions=definitionsFor(record), charged=new Set();
      const result=record.scores || window.BridgeScoring?.snapshot(record);
      const penalties=new Map((result?.municipal.groups || []).map(g=>[g.key,g.penalty]));
      const title=Array(headers.length).fill('');
      title[2]='بازدید '+(record.no || 'پیش‌نویس');title[3]=[record.bridgeName,record.bridgeCode,record.jdate,record.inspector].filter(Boolean).join(' • ');rows.push(title);
      const sorted=[...(record.items || [])].sort((a,b)=>{
        const ad=definitions.get(a.itemId)||legacyDefinition(a.itemId),bd=definitions.get(b.itemId)||legacyDefinition(b.itemId);
        return (a.categoryName || ad.category).localeCompare(b.categoryName || bd.category,'fa') || severitySort(a,b);
      });
      let previousGroup=null;
      sorted.forEach(item=>{
        const d=definitions.get(item.itemId)||legacyDefinition(item.itemId);
        const group=item.categoryName || d.category;
        const score=window.BridgeScoring?.scoreItem(item);
        const key=score?.key || '';
        const penalty=score?.penalty;
        const counted=score?.applicable && score.assessed && penalties.get(key)!=null && penalty===penalties.get(key) && !charged.has(key);
        if(counted)charged.add(key);
        const legacyPending=result?.municipal.legacy && severityId(item.statusId || item.status)==='none' && item.assessed!==true;
        const status=item.applicable===false?'کاربرد ندارد':item.assessed===false || legacyPending || !window.severityKnown(item.statusId || item.status)?'بررسی نشده':severityLabel(item.statusId || item.status || 'none');
        rows.push([++rowNumber,itemOccurrenceCode(item),group===previousGroup?'':group,item.itemName || d.item,
          status,score?.weight ?? '',key,counted?penalty:score?.applicable && score.assessed?0:'',
          gpsLocation(item.location || item.gpsLocation),item.note || '',imageCell(record,item,item.photos || [])]);
        previousGroup=group;
      });
      rows.push(...scoreSummaryRows(record,headers.length));
    });
    // An empty picture column and unnamed placeholder columns have no report value.
    const keep=headers.map((h,i)=>i).filter(i=>headers[i].trim() && !/^[-—]+$/.test(headers[i].trim()) &&
      (headers[i]!=='نام تصویر' || rows.slice(1).some(row=>String(row[i] ?? '').trim())));
    return rows.map(row=>keep.map(i=>row[i] ?? ''));
  }

  function safeCsv(value) {
    const text=String(value ?? '');
    if(typeof value==='number')return String(value);
    return /^[\s]*[=+\-@]/.test(text)?"'"+text:text;
  }
  function browserCsv(name, reportRows, profileRows, rows) {
    const all = [...reportRows, [], ...profileRows, [], ...rows];
    const text = '\ufeff' + all.map(row => row.map(value => `"${safeCsv(value).replace(/"/g, '""')}"`).join(',')).join('\r\n');
    const blob = new Blob([text], { type: 'text/csv;charset=utf-8' }), link = document.createElement('a');
    link.href = URL.createObjectURL(blob); link.download = name; link.click();
    setTimeout(() => URL.revokeObjectURL(link.href), 1000);
  }

  function reportPayload(records) {
    const bridges = [], seen = new Set(), settings = dbList('settings').find(item => item.id === 'main') || {};
    (records || []).forEach(record => {
      const bridge = bridgeForRecord(record);
      if (bridge?.id && !seen.has(bridge.id)) { seen.add(bridge.id); bridges.push(bridge); }
    });
    const reportRows = reportRowsFor(records, settings), profileRows = profileRowsFor(records);
    return {
      meta: {
        organization: settings.orgName || 'شرکت مهندسین مشاور هگزا', unit: settings.unitName || '',
        title: 'گزارش نگهداری و بازرسی چشمی پل', generatedAt: new Date().toISOString(),
        generatedJalali: reportRows.find(row => row[0] === 'تاریخ ثبت گزارش')?.[1] || '',
        severityScale: 'اضطراری | متوسط | کم | ندارد | ع.ا.ب',
        profileSource: window.BRIDGE_PROFILE_SCHEMA?.source || '', profileSourceSha256: window.BRIDGE_PROFILE_SCHEMA?.sourceSha256 || '',
      },
      reportRows, profileRows, rows: groupedRows(records, CORE_HEADERS), pdfRows: groupedRows(records, PDF_HEADERS),
      records: records || [], bridges, profileSchema: window.BRIDGE_PROFILE_SCHEMA || {},
      engineeringReferences: {...(window.BRIDGE_ENGINEERING_REFERENCES || {}), municipal:window.BRIDGE_MUNICIPAL_CATALOG?.sourceSha256,snbi:'https://www.fhwa.dot.gov/bridge/snbi/snbi_march_2022_publication.pdf'},
    };
  }

  function exportNative(format, records, filename) {
    if (!records?.length) { toast('رکوردی برای خروجی وجود ندارد.'); return; }
    const report = reportPayload(records);
    if (Native?.isNative?.()) {
      Native.exportReport(format, filename, report);
      toast(format === 'zip' ? 'در حال آماده‌سازی بسته ZIP کامل…' : 'پنجره ذخیره فایل باز می‌شود.');
    } else if (format === 'csv') browserCsv(filename, report.reportRows, report.profileRows, report.rows);
    else toast('PDF، Excel و ZIP بومی در نسخه اندروید تولید می‌شوند.');
  }

  window.bridgeReportRows = records => groupedRows(records, CORE_HEADERS);
  window.bridgePdfRows = records => groupedRows(records, PDF_HEADERS);
  window.bridgeProfileRows = profileRowsFor;
  window.bridgeReportMetaRows = reportRowsFor;
  window.bridgeReportPayload = reportPayload;
  window.makeCSV = (records, filename) => exportNative('csv', records, filename || 'bridge-inspection-report.csv');
  window.makeXLSX = (records, filename) => exportNative('xlsx', records, filename || 'bridge-inspection-report.xlsx');
  window.makePDF = (records, filename) => exportNative('pdf', records, filename || 'bridge-inspection-report.pdf');
  window.makeZIP = (records, filename) => exportNative('zip', records, filename || 'bridge-inspection-complete.zip');
  window.exportSingleInspection = recordId => { closeModal('genericModal'); const record = dbList('inspections').find(item => item.id === recordId); if (record) makePDF([record], `${record.no || 'bridge-inspection'}.pdf`); };
  window.exportSingleInspectionZip = recordId => { closeModal('genericModal'); const record = dbList('inspections').find(item => item.id === recordId); if (record) makeZIP([record], `${record.no || 'bridge-inspection'}-complete.zip`); };
  window.exportHistoryZip = () => {
    const records = typeof filteredInspectionRecords === 'function'
      ? filteredInspectionRecords().filter(item => item.status === 'نهایی')
      : dbList('inspections').filter(item => !item.archived && item.status === 'نهایی');
    makeZIP(records, 'bridge-inspection-history.zip');
  };
})();

(function () {
  'use strict';
  window.exportReport = function exportReport(type) {
    const records = typeof reportRecords === 'function' ? reportRecords() : [];
    if (!records.length) { toast('در این بازه گزارشی وجود ندارد.'); return; }
    const period = document.getElementById('reportPeriod')?.value || 'all';
    const suffix = period === 'day' ? enNum(jfmt(reportJ)).replaceAll('/', '-') : (period === 'month' ? enNum(jfmt(reportJ)).split('/').slice(0, 2).join('-') : 'all');
    if (type === 'csv') makeCSV(records, `bridge-inspection-${suffix}.csv`);
    else if (type === 'xlsx') makeXLSX(records, `bridge-inspection-${suffix}.xlsx`);
    else if (type === 'zip') makeZIP(records, `bridge-inspection-${suffix}-complete.zip`);
    else makePDF(records, `bridge-inspection-${suffix}.pdf`);
  };
})();
