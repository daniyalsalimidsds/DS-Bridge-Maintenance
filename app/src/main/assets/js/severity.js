(function () {
  'use strict';

  const DEFINITIONS = Object.freeze({
    none:      { id: 'none',      label: 'ندارد',    rank: 0, css: 'sev-normal' },
    low:       { id: 'low',       label: 'کم',       rank: 1, css: 'sev-mild' },
    medium:    { id: 'medium',    label: 'متوسط',    rank: 2, css: 'sev-medium' },
    emergency: { id: 'emergency', label: 'اضطراری', rank: 3, css: 'sev-critical' },
    uninspectable: { id: 'uninspectable', label: 'ع.ا.ب', rank: -1, css: 'sev-uninspectable' },
  });

  const ALIASES = Object.freeze({
    none: 'none', 'ندارد': 'none', normal: 'none', 'عادی': 'none', 'مطابق': 'none', 'خارج از دامنه': 'none',
    uninspectable: 'uninspectable', 'ع.ا.ب': 'uninspectable', 'عدم امکان بازرسی': 'uninspectable',
    low: 'low', 'کم': 'low', mild: 'low', 'خفیف': 'low', 'نقص جزئی': 'low',
    medium: 'medium', 'متوسط': 'medium', moderate: 'medium', 'نیازمند اقدام': 'medium', severe: 'medium', 'شدید': 'medium',
    emergency: 'emergency', 'اضطراری': 'emergency', very_severe: 'emergency', 'خیلی شدید': 'emergency',
    'بحرانی': 'emergency', 'فوری/بحرانی': 'emergency',
  });

  function severityId(value) {
    if (value && typeof value === 'object') value = value.severityId || value.statusId || value.severity || value.status;
    return ALIASES[String(value == null ? '' : value).trim()] || 'none';
  }

  function severityLabel(value) { return DEFINITIONS[severityId(value)].label; }
  function severityRank(value) { return DEFINITIONS[severityId(value)].rank; }
  function severityClass(value) { return DEFINITIONS[severityId(value)].css; }
  function severityKnown(value) {
    return Object.prototype.hasOwnProperty.call(ALIASES, String(value ?? '').trim());
  }
  function inspectionOverall(items) {
    const applicable=(items || []).filter(item=>item.applicable!==false);
    const observed=applicable.filter(item=>item.assessed!==false && severityKnown(item.statusId || item.status));
    const worst=observed.reduce((max,item)=>Math.max(max,severityRank(item.statusId || item.status)),0);
    if(worst>0){const d=Object.values(DEFINITIONS).find(x=>x.rank===worst);return {id:d.id,label:d.label};}
    if(observed.some(item=>severityId(item.statusId || item.status)==='uninspectable'))return {id:'uninspectable',label:'ع.ا.ب؛ پوشش ناقص'};
    if(!applicable.length || observed.length<applicable.length)return {id:'none',label:'بررسی کامل نشده'};
    return {id:'none',label:'آسیب مشاهده نشد'};
  }

  function migrateSeverityRecord(record) {
    if (!record || typeof record !== 'object') return record;
    if ('severity' in record || 'severityId' in record) record.severityId = severityId(record.severityId || record.severity);
    if ('status' in record || 'statusId' in record) record.statusId = severityId(record.statusId || record.status);
    return record;
  }

  window.BRIDGE_SEVERITIES = DEFINITIONS;
  window.RAIL_SEVERITIES = DEFINITIONS;
  window.severityId = severityId;
  window.severityLabel = severityLabel;
  window.severityRank = severityRank;
  window.severityClass = severityClass;
  window.severityKnown = severityKnown;
  window.inspectionOverall = inspectionOverall;
  window.migrateSeverityRecord = migrateSeverityRecord;
  window.v4NormalizeSeverity = severityLabel;
  window.V4_SEVERITIES = Object.values(DEFINITIONS).map(item => item.label);
  window.V4_SEVERITY_RANK = Object.freeze({
    none: 0, uninspectable: -1, 'ع.ا.ب': -1, low: 1, medium: 2, emergency: 3,
    'ندارد': 0, 'کم': 1, 'متوسط': 2, 'اضطراری': 3,
  });
})();
