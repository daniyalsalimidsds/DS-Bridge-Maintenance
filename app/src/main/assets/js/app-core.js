'use strict';

const ENTITY_KINDS = ['users', 'bridges', 'standards', 'inspections', 'defects', 'audit', 'settings', 'reminders',
  'reviews', 'criticalFindings', 'inspectionVoids', 'reportRevisions', 'inspectionPrograms'];
window.ENTITY_KINDS = ENTITY_KINDS;

const pageNames = {
  dashboard: 'داشبورد',
  bridges: 'لیست پل‌ها',
  bridgeForm: 'شناسنامه فنی پل',
  inspectionForm: 'ثبت بازدید',
  drafts: 'پیش‌نویس‌ها و بازگشتی‌ها', inspections: 'گزارش‌های رسمی',
  reviews: 'صف کنترل کیفیت مستقل',
  criticalFindings: 'یافته‌های بحرانی',
  programs: 'برنامه بازرسی و موعدها',
  defects: 'آسیب‌ها و اقدامات اصلاحی',
  reports: 'گزارش‌ها',
  users: 'هویت‌ها، نقش‌ها و صلاحیت‌ها',
  master: 'منابع و حدود فنی',
  settings: 'تنظیمات',
  guide: 'راهنمای استفاده',
  academy: 'راهنمای میدانی و مبانی ارزیابی',
};
window.pageNames = pageNames;

const faDigits = '۰۱۲۳۴۵۶۷۸۹';
const enDigits = '0123456789';
const $ = idValue => document.getElementById(idValue);

function id(prefix) {
  return `${prefix}-${Date.now().toString(36)}-${crypto.getRandomValues(new Uint32Array(1))[0].toString(36)}`;
}

function faNum(value) {
  return String(value ?? '').replace(/[0-9]/g, digit => faDigits[digit]);
}

function enNum(value) {
  return String(value ?? '')
    .replace(/[۰-۹]/g, digit => enDigits[faDigits.indexOf(digit)])
    .replace(/[٠-٩]/g, digit => String('٠١٢٣٤٥٦٧٨٩'.indexOf(digit)))
    .replace(/٫/g, '.');
}

function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  })[char]);
}

function toast(message) {
  const host = $('toast');
  if (!host) return;
  host.textContent = message;
  host.classList.add('show');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => host.classList.remove('show'), 2700);
}

window.id = id;
window.faNum = faNum;
window.enNum = enNum;
window.esc = esc;
window.toast = toast;

function getActiveUser() {
  if (window.Governance?.initialized) return window.Governance.currentUser();
  const settings = dbList('settings').find(item => item.id === 'main') || {};
  const users = dbList('users');
  return users.find(user => user.id === settings.activeUser) || users.find(user => user.active) || users[0] || null;
}

function audit(action, kind, entityId, detail = '') {
  const user = getActiveUser();
  if (!user) return Promise.resolve({ ok: false, error: 'authentication-required' });
  if (Native?.appendAudit) return Native.appendAudit({ action, kind, entityId, detail, reason: detail });
  return Promise.resolve({ ok: false, error: 'audit-service-unavailable' });
}

window.getActiveUser = getActiveUser;
window.audit = audit;

/* Jalali calendar */
function div(a, b) { return ~~(a / b); }
function mod(a, b) { return a - ~~(a / b) * b; }
function jalCal(jy) {
  const breaks = [-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178];
  const bl = breaks.length;
  const gy = jy + 621;
  let leapJ = -14;
  let jp = breaks[0];
  let jm;
  let jump;
  let leap;
  let n;
  let index;
  if (jy < jp || jy >= breaks[bl - 1]) throw new Error('سال شمسی خارج از دامنه است');
  for (index = 1; index < bl; index += 1) {
    jm = breaks[index];
    jump = jm - jp;
    if (jy < jm) break;
    leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4);
    jp = jm;
  }
  n = jy - jp;
  leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4);
  if (mod(jump, 33) === 4 && jump - n === 4) leapJ += 1;
  const leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150;
  const march = 20 + leapJ - leapG;
  if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33;
  leap = mod(mod(n + 1, 33) - 1, 4);
  if (leap === -1) leap = 4;
  return { leap, gy, march };
}

function g2d(gy, gm, gd) {
  let value = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) + div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408;
  value = value - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752;
  return value;
}

function d2g(jdn) {
  let j = 4 * jdn + 139361631;
  j = j + div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908;
  const i = div(mod(j, 1461), 4) * 5 + 308;
  const gd = div(mod(i, 153), 5) + 1;
  const gm = mod(div(i, 153), 12) + 1;
  const gy = div(j, 1461) - 100100 + div(8 - gm, 6);
  return { gy, gm, gd };
}

function j2d(jy, jm, jd) {
  const result = jalCal(jy);
  return g2d(result.gy, 3, result.march) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1;
}

function d2j(jdn) {
  const gregorian = d2g(jdn);
  let jy = gregorian.gy - 621;
  const result = jalCal(jy);
  const firstFarvardin = g2d(gregorian.gy, 3, result.march);
  let k = jdn - firstFarvardin;
  let jm;
  let jd;
  if (k >= 0) {
    if (k <= 185) return { jy, jm: 1 + div(k, 31), jd: mod(k, 31) + 1 };
    k -= 186;
  } else {
    jy -= 1;
    k += 179;
    if (result.leap === 1) k += 1;
  }
  jm = 7 + div(k, 30);
  jd = mod(k, 30) + 1;
  return { jy, jm, jd };
}

function toJalali(gy, gm, gd) { return d2j(g2d(gy, gm, gd)); }
function toGregorian(jy, jm, jd) { return d2g(j2d(jy, jm, jd)); }
function jfmt(jalali) { return faNum(`${jalali.jy}/${String(jalali.jm).padStart(2, '0')}/${String(jalali.jd).padStart(2, '0')}`); }
function jfmtLong(jalali) {
  const date = jalali || nowDates().j;
  const g = toGregorian(date.jy, date.jm, date.jd);
  const weekdays = ['یکشنبه', 'دوشنبه', 'سه‌شنبه', 'چهارشنبه', 'پنجشنبه', 'جمعه', 'شنبه'];
  return `${weekdays[new Date(g.gy, g.gm - 1, g.gd).getDay()]} ${faNum(date.jd)} ${jMonths[date.jm - 1]} ${faNum(date.jy)}`;
}
function iso(gregorian) { return `${gregorian.gy}-${String(gregorian.gm).padStart(2, '0')}-${String(gregorian.gd).padStart(2, '0')}`; }
function nowDates() {
  const date = new Date();
  return {
    d: date,
    j: toJalali(date.getFullYear(), date.getMonth() + 1, date.getDate()),
    g: { gy: date.getFullYear(), gm: date.getMonth() + 1, gd: date.getDate() },
  };
}

const jMonths = ['فروردین', 'اردیبهشت', 'خرداد', 'تیر', 'مرداد', 'شهریور', 'مهر', 'آبان', 'آذر', 'دی', 'بهمن', 'اسفند'];
let selectedJ = nowDates().j;
let reportJ = nowDates().j;
let calState = { target: 'inspection', ...selectedJ, sel: selectedJ };

function renderCal() {
  const { jy, jm } = calState;
  $('calTitle').textContent = `${jMonths[jm - 1]} ${faNum(jy)}`;
  const first = toGregorian(jy, jm, 1);
  const offset = (new Date(first.gy, first.gm - 1, first.gd).getDay() + 1) % 7;
  const days = jm <= 6 ? 31 : (jm <= 11 ? 30 : (jalCal(jy).leap === 0 ? 30 : 29));
  const today = nowDates().j;
  let html = ['ش', 'ی', 'د', 'س', 'چ', 'پ', 'ج'].map(value => `<div class="dow">${value}</div>`).join('');
  html += '<div></div>'.repeat(offset);
  for (let day = 1; day <= days; day += 1) {
    const selected = calState.sel?.jy === jy && calState.sel?.jm === jm && calState.sel?.jd === day;
    const current = today.jy === jy && today.jm === jm && today.jd === day;
    html += `<div class="day ${selected ? 'sel' : ''} ${current ? 'today' : ''}" onclick="calSelect(${day})">${faNum(day)}</div>`;
  }
  $('calGrid').innerHTML = html;
}

function openCalendar(target = 'inspection') {
  const date = target === 'report' ? reportJ : selectedJ;
  calState = { target, jy: date.jy, jm: date.jm, sel: date };
  renderCal();
  $('calendarModal').classList.add('open');
}

function calMove(amount) {
  calState.jm += amount;
  if (calState.jm < 1) { calState.jm = 12; calState.jy -= 1; }
  if (calState.jm > 12) { calState.jm = 1; calState.jy += 1; }
  renderCal();
}

function applyCal() {
  const jalali = calState.sel;
  const gregorian = toGregorian(jalali.jy, jalali.jm, jalali.jd);
  if (calState.target === 'report') {
    reportJ = jalali;
    $('reportDateBtn').textContent = jfmt(jalali);
    renderReportStats();
  } else {
    selectedJ = jalali;
    $('jdateBtn').textContent = jfmt(jalali);
    $('gdate').value = iso(gregorian);
  }
}

function calSelect(day) {
  calState.sel = { jy: calState.jy, jm: calState.jm, jd: day };
  applyCal();
  closeModal('calendarModal');
}

function calToday() {
  calState.sel = nowDates().j;
  applyCal();
  closeModal('calendarModal');
}

function currentJCompact() { return enNum(jfmt(selectedJ)).replace(/\D/g, '').slice(0, 8); }

Object.assign(window, { openCalendar, calMove, calSelect, calToday, currentJCompact, jfmt, jfmtLong, toGregorian, toJalali });

/* Persistent initial state and compatibility migration */
function seed() {
  if (!dbList('users').length) {
    const admin = { id: 'user-admin', name: 'مدیر سیستم', role: 'مدیر سیستم', active: true };
    dbSave('users', admin);
    dbSave('settings', {
      id: 'main', activeUser: admin.id, orgName: 'شرکت مهندسین مشاور هگزا', theme: 'day',
    });
  } else {
    const settings = dbList('settings').find(item => item.id === 'main') || { id: 'main' };
    if (!settings.orgName || settings.orgName === 'راه‌آهن جمهوری اسلامی ایران') {
      dbSave('settings', { ...settings, orgName: 'شرکت مهندسین مشاور هگزا' });
    }
  }
  dbList('defects').forEach(record => {
    const before = record.severityId;
    migrateSeverityRecord(record);
    if (record.severityId !== before) dbSave('defects', record);
  });
  dbList('inspections').forEach(record => {
    let changed = false;
    (record.items || []).forEach(item => {
      const before = item.statusId;
      migrateSeverityRecord(item);
      changed = changed || before !== item.statusId;
    });
    const migratedOverall = severityId(record.overall);
    if (record.overall !== migratedOverall) { record.overall = migratedOverall; changed = true; }
    if (changed) dbSave('inspections', record);
  });
}

function renderHeader() {
  const user = getActiveUser();
  const now = nowDates();
  if ($('activeUserLine')) $('activeUserLine').textContent = `کاربر فعال: ${user?.name || '—'}${user?.role ? ` • ${user.role}` : ''}`;
  if ($('drawerUser')) $('drawerUser').textContent = user?.name || 'کاربر فعال';
  if ($('hello')) $('hello').textContent = `سلام${user?.name ? `، ${user.name}` : ''}`;
  if ($('todayChip')) $('todayChip').textContent = jfmtLong(now.j);
}

/* Dynamic inspection checklist */
let currentInspection = null;
let currentPhotos = [];
let repairPhotos = [];
let activeRepairDefect = null;
let checklistCache = { bridge: null, updatedAt: null, inspectionKey: '', categories: [], items: new Map() };
window.currentInspection = currentInspection;
window.currentPhotos = currentPhotos;
window.repairPhotos = repairPhotos;

function occurrenceKey(itemId, index = 1) { return `${itemId}::${Math.max(1, Number(index) || 1)}`; }

function checklistBridge() {
  return window.selectedInspectionBridge?.() || window.currentInspection?.bridgeSnapshot || null;
}

function sortedChecklistCategories() {
  const bridge = checklistBridge();
  if (!bridge) return [];
  const updatedAt = bridge.updatedAt || bridge.profileSchemaVersion || '';
  const inspectionKey = `${currentInspection?.id || ''}|${(currentInspection?.items || []).length}`;
  if (checklistCache.bridge === bridge && checklistCache.updatedAt === updatedAt && checklistCache.inspectionKey === inspectionKey) return checklistCache.categories;
  const categories = (window.checklistsForBridge?.(bridge) || []).map(category => ({
    ...category,
    items: [...(category.items || [])].sort((a, b) => compareChecklistCodes(a.code, b.code)),
  })).sort((a, b) => compareChecklistCodes(a.code, b.code));
  const items = new Map();
  categories.forEach(category => (category.items || []).forEach(item => items.set(item.id, item)));
  const legacyGroups = new Map();
  (currentInspection?.items || []).forEach(stored => {
    if (!stored?.itemId || items.has(stored.itemId)) return;
    const definition = window.bridgeLegacyChecklistDefinition?.(stored.itemId) || {category:stored.categoryName || 'موارد محفوظ از نسخه یا شناسنامه قبلی',item:stored.itemName || stored.itemId};
    if (!definition) return;
    if (!legacyGroups.has(definition.category)) legacyGroups.set(definition.category, new Map());
    const group = legacyGroups.get(definition.category);
    if (!group.has(stored.itemId)) {
      const storedCode = String(stored.itemCode || stored.code || '').replace(/\.\d+$/, '');
      group.set(stored.itemId, { id: stored.itemId, name: definition.item, code: storedCode || `قدیمی-${group.size + 1}`, unit: '' });
    }
  });
  legacyGroups.forEach((legacyItems, name) => {
    const category = {
      id: `legacy-${categories.length + 1}`,
      name: `${name} (سوابق محفوظ)`,
      code: `L${categories.length + 1}`,
      refs: [],
      items: [...legacyItems.values()],
      legacy: true,
    };
    categories.push(category);
    category.items.forEach(item => items.set(item.id, item));
  });
  checklistCache = { bridge, updatedAt, inspectionKey, categories, items };
  return categories;
}

function itemDefinition(itemId) {
  const baseId = String(itemId || '').split('::')[0];
  sortedChecklistCategories();
  const cached = checklistCache.items.get(baseId);
  if (cached) return cached;
  for (const category of window.BRIDGE_CHECKLIST_CATALOG || []) {
    const found = (category.items || []).find(item => item.id === baseId);
    if (found) return found;
  }
  return null;
}

function itemName(itemId) { return itemDefinition(itemId)?.name || String(itemId || '').split('::')[0] || ''; }
function itemCode(itemId) { return String(itemDefinition(itemId)?.code || ''); }
function occurrenceCode(itemId, index = 1) {
  const base = itemCode(itemId);
  return base ? `${base}.${Math.max(1, Number(index) || 1)}` : String(Math.max(1, Number(index) || 1));
}
function measurementUnitFor(itemId) {
  const unit = String(itemDefinition(itemId)?.unit || '').trim();
  return unit && unit !== 'چشمی' ? unit : '';
}

function itemPhotosFor(key, index) {
  if (!currentInspection) return [];
  const resolvedKey = index == null ? String(key) : occurrenceKey(key, index);
  currentInspection.itemPhotos = currentInspection.itemPhotos || {};
  return currentInspection.itemPhotos[resolvedKey] || (currentInspection.itemPhotos[resolvedKey] = []);
}

function refreshItemPhotos(key, index) {
  const resolvedKey = index == null ? String(key) : occurrenceKey(key, index);
  const host = document.querySelector(`[data-item-photos="${CSS.escape(resolvedKey)}"]`);
  if (!host) return;
  if (typeof window.renderItemPhotoGrid === 'function') window.renderItemPhotoGrid(resolvedKey, host);
}

Object.assign(window, {
  occurrenceKey, itemDefinition, itemName, itemCode, occurrenceCode, measurementUnitFor,
  itemPhotosFor, refreshItemPhotos, sortedChecklistCategories,
});

function blankInspection(bridgeId = '') {
  const bridge = dbList('bridges').find(item => item.id === bridgeId && !item.archived) || null;
  return {
    id: id('ins'), status: 'پیش‌نویس', bridgeId: bridge?.id || '', bridgeName: bridge ? bridgeName(bridge) : '',
    bridgeCode: bridge ? bridgeCode(bridge) : '', bridgeSnapshot: bridge ? JSON.parse(JSON.stringify(bridge)) : null,
    workflowStatus: 'draft', checklistSchemaVersion: '1.7.0', items: [], itemPhotos: {}, itemLocations: {},
  };
}

function resetInspectionControls() {
  if ($('visitType')) $('visitType').selectedIndex = 0;
  if ($('shift')) $('shift').selectedIndex = 0;
  if ($('inspectorName')) {
    $('inspectorName').value = getActiveUser()?.name || '';
    $('inspectorName').readOnly = true;
  }
}

function newInspection(bridgeId = '') {
  if (window.Governance?.initialized && !window.Governance.requireSession('برای شروع بازدید وارد شوید.')) return;
  window.flushInspectionAutosave?.();
  const bridges = dbList('bridges').filter(item => !item.archived);
  if (!bridges.length) {
    go('bridges');
    toast('ابتدا یک پل و شناسنامه فنی آن را ثبت کنید.');
    return;
  }
  selectedJ = nowDates().j;
  currentPhotos = [];
  currentInspection = blankInspection(bridgeId);
  window.currentPhotos = currentPhotos;
  window.currentInspection = currentInspection;
  go('inspectionForm');
  $('jdateBtn').textContent = jfmt(selectedJ);
  $('gdate').value = iso(nowDates().g);
  $('inspectionNo').textContent = 'پیش‌نویس';
  resetInspectionControls();
  window.renderInspectionBridgeSelector?.(currentInspection.bridgeId);
  renderChecklist();
  window.renderPhotos?.();
}

function occurrenceTemplate(item, index, data = {}) {
  const unit = measurementUnitFor(item.id);
  const key = occurrenceKey(item.id, index);
  const severity = severityId(data.statusId || data.status || 'none');
  const code = occurrenceCode(item.id, index);
  return `<div class="item-occurrence" data-item="${esc(item.id)}" data-occurrence="${index}" data-photo-key="${esc(key)}" data-severity="${esc(severity)}" data-assessed="${data.assessed === true ? 'true' : 'false'}" data-applicable="${data.applicable === false ? 'false' : 'true'}">
    <div class="occurrence-head"><span class="occurrence-code">${esc(code)}</span><span class="muted">مورد ${faNum(index)}</span>${index > 1 ? `<button class="btn danger occurrence-remove" type="button" onclick="removeChecklistOccurrence('${esc(item.id)}',${index})">حذف</button>` : ''}</div>
    ${window.occurrenceLocationUi ? window.occurrenceLocationUi(key, data.location) : ''}
    <div class="itemrow ${unit ? 'has-measurement' : 'qualitative-only'}">
      <div class="severity-group">${Object.values(BRIDGE_SEVERITIES).map(option => `<button type="button" class="sev-choice ${data.assessed === true && option.id === severity ? `selected ${option.css}` : ''}" data-sev="${option.id}" aria-pressed="${data.assessed === true && option.id === severity}" title="${option.id === 'uninspectable' ? 'عدم امکان بازرسی؛ علت را بنویسید' : option.label}" onclick="chooseSeverity(this,'${esc(item.id)}','${option.id}')">${esc(option.label)}</button>`).join('')}</div>
      ${unit ? `<input class="item-value" inputmode="decimal" aria-label="مقدار اندازه‌گیری ${esc(item.name)}" placeholder="مقدار (${esc(unit)})" value="${esc(data.value || '')}">` : ''}
      <textarea class="item-note" rows="2" aria-label="شرح محل، گستره و نشانه‌های آسیب" placeholder="محل عضو، گستره، اندازه و شواهد؛ برای ع.ا.ب علت دسترسی‌نداشتن">${esc(data.note || '')}</textarea>
    </div>
    <div class="assessment-state">${data.assessed === true ? 'ارزیابی ثبت شده' : 'هنوز ارزیابی نشده'} • ضریب ${faNum(item.scoring?.weight || 3)}</div>
    <label class="not-applicable"><input type="checkbox" ${data.applicable === false ? 'checked' : ''} onchange="setOccurrenceApplicability(this)"> این مورد در این پل کاربرد ندارد (علت در توضیحات)</label>
    <div class="btnline"><button class="btn" type="button" onclick="pickItemPhoto('${esc(key)}')">انتخاب تصویر</button><button class="btn" type="button" onclick="takeItemPhoto('${esc(key)}')">عکس گرفتن</button></div>
    <div class="photo-grid" data-item-photos="${esc(key)}"></div>
  </div>`;
}

function occurrencesForItem(itemId) {
  const occurrences = (currentInspection?.items || [])
    .filter(item => item.itemId === itemId)
    .map((item, index) => ({ ...item, occurrenceIndex: Number(item.occurrenceIndex) || index + 1 }));
  return occurrences.length ? occurrences : [{ itemId, occurrenceIndex: 1, statusId: 'none' }];
}

function codeParts(value) {
  const raw = enNum(String(value ?? '')).trim();
  if (!raw) return [Number.MAX_SAFE_INTEGER];
  return raw.split('.').map(part => Number((part.match(/\d+/) || [Number.MAX_SAFE_INTEGER])[0]));
}

function compareChecklistCodes(first, second) {
  const a = codeParts(first);
  const b = codeParts(second);
  const count = Math.max(a.length, b.length);
  for (let index = 0; index < count; index += 1) {
    const av = a[index] ?? -1;
    const bv = b[index] ?? -1;
    if (av !== bv) return av - bv;
  }
  return String(first ?? '').localeCompare(String(second ?? ''), 'fa', { numeric: true });
}

function setChecklistCategoryExpanded(category, expanded) {
  if (!category) return;
  category.classList.toggle('collapsed', !expanded);
  const body = category.querySelector('.checkcat-body');
  const button = category.querySelector('.checkcat-title');
  const chevron = category.querySelector('.checkcat-chevron');
  if (body) body.hidden = !expanded;
  if (button) button.setAttribute('aria-expanded', expanded ? 'true' : 'false');
  if (chevron) chevron.textContent = expanded ? '⌃' : '⌄';
}

function toggleChecklistCategory(button) {
  const category = button?.closest?.('.checkcat');
  if (!category) return;
  const open = category.classList.contains('collapsed');
  if (open) document.querySelectorAll('#checklistHost .checkcat').forEach(other => { if (other !== category) setChecklistCategoryExpanded(other, false); });
  if (open) ensureChecklistCategoryBody(category);
  setChecklistCategoryExpanded(category, open);
  if (open) requestAnimationFrame(() => initMarquees(category));
}

function ensureChecklistCategoryBody(categoryElement) {
  const body = categoryElement?.querySelector('.checkcat-body');
  if (!body || body.dataset.loaded === '1') return;
  const category = sortedChecklistCategories().find(entry => String(entry.id || entry.name) === categoryElement.dataset.category);
  if (!category) return;
  body.innerHTML = (category.items || []).map(item => {
    const occurrences = occurrencesForItem(item.id).sort((a, b) => a.occurrenceIndex - b.occurrenceIndex);
    return `<div class="checkitem" data-item="${esc(item.id)}" data-code="${esc(item.code || '')}">
      <div class="checkitem-title"><h4><span class="item-base-code">${esc(item.code || '')}</span>${marqueeLabel(item.name)}</h4><button class="occurrence-add" type="button" aria-label="افزودن رخداد دیگر" onclick="addChecklistOccurrence('${esc(item.id)}')">＋</button></div>
      <div class="occurrences">${occurrences.map(itemOccurrence => occurrenceTemplate(item, itemOccurrence.occurrenceIndex, itemOccurrence)).join('')}</div>
    </div>`;
  }).join('');
  body.dataset.loaded = '1';
  body.querySelectorAll('.item-occurrence').forEach(row => refreshItemPhotos(row.dataset.photoKey));
}

function marqueeLabel(text) {
  const safe = esc(text);
  return `<span class="marquee-text" title="${safe}"><span class="marquee-track"><span>${safe}</span><span aria-hidden="true">${safe}</span></span></span>`;
}

function initMarquees(root = document) {
  root.querySelectorAll?.('.marquee-text').forEach(box => {
    const first = box.querySelector('.marquee-track > span');
    box.classList.toggle('is-overflowing', Boolean(first && first.scrollWidth > box.clientWidth + 2));
  });
}

let marqueeRefreshFrame = 0;
function scheduleMarqueeRefresh(root = document) {
  cancelAnimationFrame(marqueeRefreshFrame);
  marqueeRefreshFrame = requestAnimationFrame(() => initMarquees(root));
}
window.addEventListener('resize', () => scheduleMarqueeRefresh(document), { passive: true });
document.fonts?.ready?.then(() => scheduleMarqueeRefresh(document));

function renderChecklist() {
  const host = $('checklistHost');
  if (!host) return;
  const categories = sortedChecklistCategories();
  if (!categories.length) {
    host.innerHTML = '<div class="empty">برای ساخت چک‌لیست اختصاصی، ابتدا پل را انتخاب کنید.</div>';
    return;
  }
  host.innerHTML = categories.map(category => `<div class="checkcat collapsed" data-category="${esc(category.id || category.name)}" data-sort-code="${esc(category.code || '')}">
    <button class="checkcat-title" type="button" aria-expanded="false" onclick="toggleChecklistCategory(this)">
      <span class="checkcat-title-main"><span class="category-code">${esc(category.code || '')}</span>${marqueeLabel(category.name)}</span>
      <span class="checkcat-meta"><small>${faNum((category.items || []).length)} مورد</small><span class="checkcat-chevron" aria-hidden="true">⌄</span></span>
    </button>
    <div class="checkcat-body" hidden></div>
  </div>`).join('');
  scheduleMarqueeRefresh(host);
  window.filterInspectionChecklist?.();
  window.renderEngineeringAssessment?.();
  window.scheduleScorePreview?.();
}

function addChecklistOccurrence(itemIdValue) {
  const wrap = document.querySelector(`.checkitem[data-item="${CSS.escape(itemIdValue)}"] .occurrences`);
  const item = itemDefinition(itemIdValue);
  if (!wrap || !item) return;
  currentInspection.occurrenceCounters=currentInspection.occurrenceCounters || {};
  const index = Math.max(currentInspection.occurrenceCounters[itemIdValue] || 0,...[...wrap.querySelectorAll('.item-occurrence')].map(row=>Number(row.dataset.occurrence)||0)) + 1;
  currentInspection.occurrenceCounters[itemIdValue]=index;
  wrap.insertAdjacentHTML('beforeend', occurrenceTemplate(item, index, { occurrenceIndex: index, statusId: 'none' }));
  refreshItemPhotos(occurrenceKey(itemIdValue, index));
  window.autosaveCurrentInspection?.();
  window.scheduleScorePreview?.();
}

function removeChecklistOccurrence(itemIdValue, index) {
  const row = document.querySelector(`.checkitem[data-item="${CSS.escape(itemIdValue)}"] .item-occurrence[data-occurrence="${Number(index)}"]`);
  if (!row) return;
  const wrap = row.parentElement;
  if (wrap.querySelectorAll('.item-occurrence').length <= 1) {
    toast('حداقل یک رخداد برای هر مورد باید باقی بماند.');
    return;
  }
  const key = row.dataset.photoKey;
  currentInspection.occurrenceCounters=currentInspection.occurrenceCounters || {};
  currentInspection.occurrenceCounters[itemIdValue]=Math.max(currentInspection.occurrenceCounters[itemIdValue] || 0,Number(index));
  window.cancelOccurrenceLocation?.(key);
  row.remove();
  if (currentInspection?.itemPhotos) delete currentInspection.itemPhotos[key];
  if (currentInspection?.itemLocations) delete currentInspection.itemLocations[key];
  // Occurrence indices are stable identities: never renumber photos, GPS or callbacks.
  if(currentInspection) currentInspection.items=(currentInspection.items || []).filter(item=>!(item.itemId===itemIdValue && Number(item.occurrenceIndex)===Number(index)));
  window.autosaveCurrentInspection?.();
  window.scheduleScorePreview?.();
}

function chooseSeverity(button, itemIdValue, severity) {
  const row = button.closest('.item-occurrence');
  if (!row) return;
  row.querySelectorAll('.sev-choice').forEach(choice => choice.classList.remove('selected', 'sev-normal', 'sev-mild', 'sev-medium', 'sev-severe', 'sev-critical', 'sev-uninspectable'));
  button.classList.add('selected', severityClass(severity));
  row.dataset.severity = severityId(severity);
  row.dataset.assessed = 'true';
  row.dataset.applicable = 'true';
  const excluded=row.querySelector('.not-applicable input'); if(excluded) excluded.checked=false;
  row.querySelectorAll('.sev-choice').forEach(choice=>choice.setAttribute('aria-pressed',String(choice===button)));
  const state=row.querySelector('.assessment-state'); if(state)state.textContent='ارزیابی ثبت شده • '+severityLabel(severity);
  window.scheduleScorePreview?.();
  if (typeof window.scheduleInspectionAutosave === 'function') window.scheduleInspectionAutosave();
  else window.autosaveCurrentInspection?.();
}

function collectRenderedItems() {
  const priorItems=new Map((currentInspection?.items || []).map(item=>[occurrenceKey(item.itemId,item.occurrenceIndex),item]));
  return [...document.querySelectorAll('.item-occurrence')].map(row => {
    const itemIdValue = row.dataset.item;
    const occurrenceIndex = Number(row.dataset.occurrence) || 1;
    const severity = severityId(row.dataset.severity || 'none');
    const input = row.querySelector('.item-value');
    const prior = priorItems.get(occurrenceKey(itemIdValue,occurrenceIndex));
    const key = row.dataset.photoKey || occurrenceKey(itemIdValue, occurrenceIndex);
    return {
      itemId: itemIdValue,
      itemName:itemName(itemIdValue), categoryName:itemDefinition(itemIdValue)?.categoryName || prior?.categoryName || '',
      scoring:itemDefinition(itemIdValue)?.scoring || prior?.scoring || window.BridgeScoring?.definition({itemId:itemIdValue,itemName:itemName(itemIdValue)}),
      assessed:row.dataset.assessed==='true', applicable:row.dataset.applicable!=='false',
      itemCode: itemCode(itemIdValue),
      occurrenceIndex,
      code: occurrenceCode(itemIdValue, occurrenceIndex),
      statusId: severity,
      status: severityLabel(severity),
      unit: measurementUnitFor(itemIdValue),
      value: input ? enNum(input.value.trim()) : (prior?.value || ''),
      note: row.querySelector('.item-note')?.value.trim() || '',
      location: window.occurrenceLocationFor?.(key, prior?.location) || null,
      photos: itemPhotosFor(key),
    };
  });
}

function collectItems() {
  const rendered = collectRenderedItems();
  const byItem = new Map();
  rendered.forEach(item => {
    if (!byItem.has(item.itemId)) byItem.set(item.itemId, []);
    byItem.get(item.itemId).push(item);
  });
  return sortedChecklistCategories().flatMap(category => (category.items || []).flatMap(definition => {
    const live = byItem.get(definition.id);
    if (live?.length) return live;
    const stored = (currentInspection?.items || []).filter(item => item.itemId === definition.id);
    const source = stored.length ? stored : [{ itemId: definition.id, occurrenceIndex: 1, statusId: 'none' }];
    return source.map((item, index) => {
      const occurrenceIndex = Number(item.occurrenceIndex) || index + 1;
      const key = occurrenceKey(definition.id, occurrenceIndex);
      const statusId = severityId(item.statusId || item.status || 'none');
      return {
        ...item, itemName:definition.name,categoryName:definition.categoryName || category.name,scoring:definition.scoring || window.BridgeScoring?.definition(definition,category.name),assessed:item.assessed===true,applicable:item.applicable!==false, itemId: definition.id, itemCode: definition.code || '', occurrenceIndex,
        code: `${definition.code}.${occurrenceIndex}`, statusId, status: severityLabel(statusId),
        unit: measurementUnitFor(definition.id), location: typeof window.occurrenceLocationFor==='function'?window.occurrenceLocationFor(key,item.location):(item.location || null),
        photos: itemPhotosFor(key), note: item.note || '', value: item.value || '',
      };
    });
  }));
}

// A draft represents actual checklist work, not merely opening a bridge or
// entering report metadata. Severity «ندارد» is the default and therefore does
// not count as input; notes, measurements, photos and locations do.
function checklistHasEnteredData(items) {
  return (items || []).some(item => {
    if (item.assessed===true || item.applicable===false || severityId(item.statusId || item.status)==='uninspectable' || severityRank(item?.statusId || item?.status || 'none') > 0) return true;
    if (String(item?.value ?? '').trim() || String(item?.note ?? '').trim()) return true;
    if (Array.isArray(item?.photos) && item.photos.length) return true;
    const location = item?.location || item?.gpsLocation;
    return Boolean(location && Number.isFinite(Number(location.lat)) && Number.isFinite(Number(location.lon)));
  });
}

Object.assign(window, {
  newInspection, occurrenceTemplate, occurrencesForItem, compareChecklistCodes,
  setChecklistCategoryExpanded, toggleChecklistCategory, renderChecklist,
  addChecklistOccurrence, removeChecklistOccurrence, chooseSeverity,
  collectInspectionItems: collectItems, checklistHasEnteredData, initMarquees, scheduleMarqueeRefresh, ensureChecklistCategoryBody,
});

function inspectionFormValues() {
  return {
    visitType: $('visitType')?.value || '',
    shift: $('shift')?.value || '',
    inspector: $('inspectorName')?.value.trim() || '',
  };
}

async function saveInspection(submitForQc) {
  if (!currentInspection || ['submitted', 'approved'].includes(currentInspection.workflowStatus)) return;
  if (submitForQc && window.pendingFinalization) { toast('ارسال برای کنترل کیفیت در حال انجام است.'); return; }
  if (submitForQc && window.Governance?.initialized && !window.Governance.requireSession('برای ارسال بازدید وارد شوید.')) return;
  const bridge = window.selectedInspectionBridge?.();
  if (!bridge) { toast('ابتدا پل مورد بازدید را انتخاب کنید.'); $('inspectionBridge')?.focus(); return; }
  const items = collectItems();
  window.captureEngineeringAssessment?.();
  if (submitForQc && !window.validateEngineeringFinalization?.(items)) return;
  if (!submitForQc && !checklistHasEnteredData(items) && !currentInspection.signatureAttachment && !currentInspection.internationalAssessment?.reviewer && !currentInspection.elementAssessments?.length) {
    const alreadySaved = dbList('inspections').some(item => item.id === currentInspection.id && item.status === 'پیش‌نویس');
    if (alreadySaved) {
      dbDeleteBatch(inspectionDeleteEntries(currentInspection.id)).then(result => {
        if (result?.ok) { renderDrafts(); renderDashboard(); }
      });
    }
    $('inspectionNo').textContent = 'پیش‌نویس';
    toast('چک‌لیست هنوز ورودی ندارد؛ پیش‌نویسی ذخیره نشد.');
    return;
  }
  const overallResult = inspectionOverall(items);
  const overall = overallResult.id;
  const user = getActiveUser();
  if (submitForQc && !user) { toast('نشست احراز هویت فعال نیست.'); window.Governance?.openAuthentication(); return; }
  let inspectorReassignmentReason = '';
  if (submitForQc && currentInspection.inspectorId && currentInspection.inspectorId !== user?.id) {
    inspectorReassignmentReason = await window.Governance?.collectInspectorReassignment(currentInspection, user);
    if (!inspectorReassignmentReason) return;
  }
  const values = inspectionFormValues();
  values.inspector = user?.name || values.inspector;
  if (submitForQc && overall === 'unknown') {
    toast('حداقل یک مقدار شدت ناشناخته است؛ پیش از ارسال آن را بازبینی کنید.');
    return;
  }
  if (submitForQc && !currentInspection.signatureAttachment?.mediaId) {
    toast('برای ارسال رسمی، امضای بازرس احراز‌شده الزامی است.');
    return;
  }
  if (submitForQc && currentInspection.signatureAttachment?.signerId !== user?.id) {
    toast('امضا به هویت نشست فعلی متصل نیست؛ امضا را دوباره ثبت کنید.');
    return;
  }
  const now = Date.now();
  const priorWorkflow = currentInspection.workflowStatus || 'draft';
  const draftInspectorId = priorWorkflow === 'returned' ? currentInspection.inspectorId : (user?.id || currentInspection.inspectorId || '');
  const draftInspectorName = priorWorkflow === 'returned' ? currentInspection.inspector : (user?.name || values.inspector);
  const inspection = {
    ...currentInspection,
    draftReference: currentInspection.draftReference || ('D-' + currentInspection.id.slice(-12).toUpperCase()),
    jdate: jfmt(selectedJ),
    gdate: $('gdate')?.value || '',
    bridgeId: bridge.id,
    bridgeName: bridgeName(bridge),
    bridgeCode: bridgeCode(bridge),
    bridgeUse: bridgeUse(bridge),
    bridgeLocation: bridge.location ? { ...bridge.location } : null,
    bridgeSnapshot: JSON.parse(JSON.stringify(bridge)),
    checklistSchemaVersion: '1.7.0',
    ...values,
    items,
    overall,
    overallLabel: overallResult.label,
    photos: currentPhotos,
    inspector: submitForQc ? (user?.name || values.inspector) : draftInspectorName,
    inspectorId: submitForQc ? (user?.id || currentInspection.inspectorId || '') : draftInspectorId,
    inspectorReassignmentReason,
    createdByUserId: currentInspection.createdByUserId || user?.id || '',
    status: submitForQc ? 'ارسال‌شده' : (priorWorkflow === 'returned' ? 'بازگشت برای اصلاح' : 'پیش‌نویس'),
    workflowStatus: submitForQc ? 'submitted' : (priorWorkflow === 'returned' ? 'returned' : 'draft'),
    officialReport: false,
    finalizedAt: null,
    updatedAt: now,
  };
  delete inspection.no;
  delete inspection.reportNo;
  ['action', 'note', 'responsible', 'priority', 'deadline', 'nextInspection', 'restriction', 'restrictionNo', 'materials']
    .forEach(field => delete inspection[field]);
  if (!submitForQc) {
    dbSave('inspections', inspection);
    currentInspection = inspection;
    window.currentInspection = inspection;
    audit('inspection-draft-saved', 'inspections', inspection.id, inspection.draftReference + ' • ' + inspection.bridgeName);
    $('inspectionNo').textContent = 'پیش‌نویس ذخیره‌شده';
    toast('پیش‌نویس ذخیره شد.');
    return;
  }
  inspection.scoringVersion = window.BridgeScoring?.VERSION || '1.6.0';
  inspection.scores = window.BridgeScoring?.snapshot(inspection);
  const defects = items.filter(item => item.applicable !== false && (severityRank(item.statusId) > 0 || item.statusId === 'uninspectable')).map(item => {
    const occurrenceIndex = Number(item.occurrenceIndex) || 1;
    const existing = dbList('defects').find(defect => defect.inspectionId === inspection.id && defect.itemId === item.itemId && (Number(defect.occurrenceIndex) || 1) === occurrenceIndex);
    const gps = item.location && Number.isFinite(Number(item.location.lat)) ? 'GPS ' + Number(item.location.lat).toFixed(6) + ', ' + Number(item.location.lon).toFixed(6) : '';
    return {
      ...(existing || {}),
      id: existing?.id || id('def'),
      inspectionId: inspection.id,
      bridgeId: bridge.id,
      bridgeName: inspection.bridgeName,
      bridgeCode: inspection.bridgeCode,
      itemId: item.itemId,
      itemCode: item.itemCode,
      occurrenceIndex,
      code: item.code,
      title: item.code + ' — ' + itemName(item.itemId),
      location: [inspection.bridgeName, inspection.bridgeCode, gps].filter(Boolean).join(' • '),
      gpsLocation: item.location || null,
      findingType: item.statusId === 'uninspectable' ? 'inspection-gap' : 'damage',
      severityId: item.statusId,
      severity: severityLabel(item.statusId),
      workflow: existing?.workflow || 'باز',
      responsible: existing?.responsible || '',
      deadline: existing?.deadline || '',
      photos: item.photos || [],
      archived: false,
      updatedAt: now,
    };
  });
  const criticalFindings = await window.Governance?.collectCriticalCases(items, inspection, defects);
  if (criticalFindings == null) return;
  window.pendingFinalization = { inspectionId: inspection.id };
  window.setFinalizationPending?.(true);
  const result = await Native.submitInspection({ inspection, defects, criticalFindings });
  window.setFinalizationPending?.(false);
  window.pendingFinalization = null;
  if (!result?.ok) {
    toast(window.Governance?.errorMessage(result?.error) || 'ارسال انجام نشد؛ پیش‌نویس حفظ شد.');
    return;
  }
  const committed = dbList('inspections').find(item => item.id === inspection.id) || inspection;
  currentInspection = committed;
  window.currentInspection = committed;
  document.dispatchEvent(new CustomEvent('bridge-inspection-submitted', { detail: { inspection: committed } }));
  renderDashboard();
  window.renderReviews?.();
  go('reviews');
  toast('بازدید قفل و برای کنترل کیفیت مستقل ارسال شد.');
}
function restoreInspectionControls(record) {
  ['visitType', 'shift'].forEach(elementId => {
    const element = $(elementId);
    if (element && record[elementId] && [...element.options].some(option => option.value === record[elementId])) element.value = record[elementId];
  });
  if ($('inspectorName')) {
    $('inspectorName').value = getActiveUser()?.name || record.inspector || '';
    $('inspectorName').readOnly = true;
  }
}

function editInspection(recordId) {
  window.flushInspectionAutosave?.();
  const record = dbList('inspections').find(item => item.id === recordId);
  if (!record || !['draft', 'returned'].includes(record.workflowStatus || (record.status === 'پیش‌نویس' ? 'draft' : ''))) { toast('فقط پیش‌نویس یا گزارش بازگشتی قابل ویرایش است.'); return; }
  currentInspection = JSON.parse(JSON.stringify(record));
  currentPhotos = [...(record.photos || [])];
  window.currentInspection = currentInspection;
  window.currentPhotos = currentPhotos;
  currentInspection.items.forEach(item=>{if(item.assessed==null)item.assessed=severityId(item.statusId || item.status)!=='none';});
  currentInspection.itemPhotos = currentInspection.itemPhotos || {};
  currentInspection.itemLocations = currentInspection.itemLocations || {};
  (record.items || []).forEach(item => {
    const key = occurrenceKey(item.itemId, Number(item.occurrenceIndex) || 1);
    if ((item.photos || []).length && !currentInspection.itemPhotos[key]) currentInspection.itemPhotos[key] = item.photos;
    if (item.location && !currentInspection.itemLocations[key]) currentInspection.itemLocations[key] = item.location;
  });
  const parts = enNum(record.jdate).split('/').map(Number);
  if (parts.length === 3 && parts.every(Number.isFinite)) selectedJ = { jy: parts[0], jm: parts[1], jd: parts[2] };
  go('inspectionForm');
  $('jdateBtn').textContent = record.jdate || jfmt(selectedJ);
  $('gdate').value = record.gdate || '';
  $('inspectionNo').textContent = record.no || 'پیش‌نویس';
  window.renderInspectionBridgeSelector?.(record.bridgeId);
  restoreInspectionControls(record);
  renderChecklist();
  window.renderPhotos?.();
}

window.setCurrentInspection=record=>{currentInspection=record;window.currentInspection=record;};
window.saveInspection = saveInspection;
window.editInspection = editInspection;

/* Lists and detail views */
function recordCheckbox(scope, idValue) { return `<input class="record-check" type="checkbox" data-scope="${scope}" value="${esc(idValue)}" onclick="event.stopPropagation()" aria-label="انتخاب رکورد">`; }
function recordDeleteButton(scope,idValue){if(['recent','inspections','drafts'].includes(scope)){const record=dbList('inspections').find(item=>item.id===idValue);if(record?.submittedAt||!['draft',''].includes(record?.workflowStatus||''))return '';}return `<button class="btn danger compact-btn" type="button" onclick="event.stopPropagation();deleteSingleRecord('${esc(scope)}','${esc(idValue)}')" aria-label="حذف این مورد">حذف</button>`;}

function renderDashboard() {
  renderHeader();
  const inspections = dbList('inspections').filter(item => !item.archived);
  const defects = dbList('defects').filter(item => !item.archived);
  const open = defects.filter(item => !['رفع‌شده', 'تأیید نهایی'].includes(item.workflow));
  const emergency = dbList('criticalFindings').filter(item => item.status !== 'closed');
  $('stIns').textContent = faNum(inspections.length);
  $('stBridges').textContent = faNum(dbList('bridges').filter(item => !item.archived).length);
  $('stCritical').textContent = faNum(emergency.length);
  $('stResolved').textContent = faNum(defects.filter(item => ['رفع‌شده', 'تأیید نهایی'].includes(item.workflow)).length);
  $('recentList').innerHTML = inspections.slice(0, 5).map(record => `<div class="listitem" onclick="${['draft','returned'].includes(record.workflowStatus || (record.status==='پیش‌نویس'?'draft':''))?`editInspection('${esc(record.id)}')`:`viewInspection('${esc(record.id)}')`}"><div class="listmain"><b>${esc(record.no || window.Governance?.workflowLabel(record) || 'پیش‌نویس')} — ${esc(record.bridgeName || 'پل نامشخص')}</b><small>${esc(record.jdate || '')} • ${esc(record.inspector || '')} • ${esc(window.Governance?.workflowLabel(record) || record.status || '')}</small></div><span class="severity-badge ${severityClass(record.overall)}">${esc(severityLabel(record.overall))}</span></div>`).join('') || '<div class="empty">هنوز بازدیدی ثبت نشده است.</div>';
}

function filteredInspectionRecords() {
  const query = normalizeFaSearch($('insSearch')?.value || '');
  return dbList('inspections').filter(item => !item.archived).filter(item => !query || normalizeFaSearch(JSON.stringify(item)).includes(query));
}

function renderInspections() {
  const records=filteredInspectionRecords().filter(record=>record.workflowStatus==='approved' || record.status==='نهایی');
  $('inspectionList').innerHTML = records.map(record => {const voided=window.Governance?.isVoided(record.id);return `<div class="listitem" onclick="viewInspection('${esc(record.id)}')"><div class="listmain"><b>${esc(record.no || '')} — ${esc(record.bridgeName || 'پل نامشخص')}${record.bridgeCode ? ` • ${esc(record.bridgeCode)}` : ''}</b><small>${esc(record.jdate || '')} • ${esc(record.visitType || '')} • بازرس: ${esc(record.inspector || '')} • QC: ${esc(record.qcReviewer || 'میراثی/ثبت‌نشده')}</small></div><span class="pill ${voided?'danger':'green'}">${voided?'باطل‌شده':'تأییدشده'}</span><span class="severity-badge ${severityClass(record.overall)}">${esc(severityLabel(record.overall))}</span></div>`;}).join('') || '<div class="empty">گزارش رسمی یا سابقه باطل‌شده‌ای یافت نشد.</div>';
}

function renderDrafts(){
  const q=normalizeFaSearch($('draftSearch')?.value||'');
  const records=dbList('inspections').filter(x=>!x.archived&&['draft','returned'].includes(x.workflowStatus || (x.status==='پیش‌نویس'?'draft':''))).filter(x=>!q||normalizeFaSearch(JSON.stringify(x)).includes(q));
  $('draftList').innerHTML=records.map(record=>`<div class="listitem" onclick="editInspection('${esc(record.id)}')">${record.workflowStatus==='draft'?recordCheckbox('drafts',record.id):''}<div class="listmain"><b>${esc(record.no||'پیش‌نویس')} — ${esc(record.bridgeName||'پل نامشخص')}</b><small>${esc(record.jdate||'')} • ${esc(record.inspector||'')}${record.qcComment?' • نظر QC: '+esc(record.qcComment):''}</small></div><span class="pill ${record.workflowStatus==='returned'?'warning':'blue'}">${record.workflowStatus==='returned'?'بازگشتی':'پیش‌نویس'}</span>${recordDeleteButton('drafts',record.id)}</div>`).join('')||'<div class="empty">پیش‌نویس یا گزارش بازگشتی وجود ندارد.</div>';
}

function selectAllRecords(scope){document.querySelectorAll(`.record-check[data-scope="${scope}"]`).forEach(x=>x.checked=true);}
function clearRecordSelections(scope){document.querySelectorAll(`.record-check[data-scope="${scope}"]`).forEach(x=>x.checked=false);}
function inspectionDeleteEntries(recordId){return [{kind:'inspections',id:recordId},...dbList('defects').filter(d=>d.inspectionId===recordId).map(d=>({kind:'defects',id:d.id})),...dbList('reminders').filter(r=>r.ownerId===recordId).map(r=>({kind:'reminders',id:r.id}))];}
function bridgeDeleteEntries(bridgeId){const inspections=dbList('inspections').filter(i=>i.bridgeId===bridgeId),inspectionIds=new Set(inspections.map(i=>i.id));return [{kind:'bridges',id:bridgeId},...inspections.map(i=>({kind:'inspections',id:i.id})),...dbList('defects').filter(d=>d.bridgeId===bridgeId||inspectionIds.has(d.inspectionId)).map(d=>({kind:'defects',id:d.id})),...dbList('reminders').filter(r=>inspectionIds.has(r.ownerId)).map(r=>({kind:'reminders',id:r.id}))];}
window.bridgeDeleteEntries=bridgeDeleteEntries;
window.inspectionDeleteEntries=inspectionDeleteEntries;
function deleteEntriesForScope(scope,recordId){
  if(scope==='bridges') return bridgeDeleteEntries(recordId);
  if(scope==='defects') return [{kind:'defects',id:recordId}];
  if(scope==='users') return [{kind:'users',id:recordId}];
  if(scope==='audit') return [{kind:'audit',id:recordId}];
  return inspectionDeleteEntries(recordId);
}
async function deleteInspectionEntity(recordId){return dbDeleteBatch(inspectionDeleteEntries(recordId));}
async function deleteSingleRecord(scope,recordId){
  if(!confirm('این مورد حذف شود؟'))return;
  if(scope==='users'){await deleteUser(recordId,true);return;}
  const result=await dbDeleteBatch(deleteEntriesForScope(scope,recordId));
  if(!result?.ok){toast('حذف انجام نشد؛ داده‌ها بدون تغییر باقی ماندند.');return;}
  audit('حذف تکی',scope,recordId,'حذف دائمی موردی');
  renderDashboard();renderDrafts();renderInspections();renderDefects();window.renderBridges?.();toast('مورد انتخاب‌شده به‌طور کامل حذف شد.');
}
async function deleteSelected(scope){
  const ids=[...document.querySelectorAll(`.record-check[data-scope="${scope}"]:checked`)].map(x=>x.value);
  if(!ids.length){toast('حداقل یک مورد را انتخاب کنید.');return;}
  if(!confirm(`${faNum(ids.length)} مورد حذف شود؟`))return;
  if(scope==='users'){
    const users=dbList('users');
    if(users.length-ids.length<1){toast('حداقل یک کاربر باید باقی بماند.');return;}
    if(!users.some(user=>user.role==='مدیر سیستم'&&!ids.includes(user.id))){toast('حداقل یک مدیر سیستم باید باقی بماند.');return;}
  }
  const entries=ids.flatMap(recordId=>deleteEntriesForScope(scope,recordId));
  const result=await dbDeleteBatch(entries);
  if(!result?.ok){toast('حذف گروهی انجام نشد؛ داده‌ها بدون تغییر باقی ماندند.');return;}
  audit('حذف انتخابی',scope,'bulk',`${ids.length} مورد`);
  if(scope==='bridges')renderBridges();else if(scope==='drafts')renderDrafts();else if(scope==='inspections')renderInspections();else if(scope==='defects')renderDefects();else if(scope==='users')renderUsers();
  renderDashboard();clearRecordSelections(scope);toast('موارد انتخاب‌شده به‌طور کامل حذف شدند.');
}

function renderDefects() {
  const query = normalizeFaSearch($('defSearch')?.value || '');
  const filter = $('defFilter')?.value || 'open';
  const records = dbList('defects').filter(item => !item.archived)
    .filter(item => filter === 'all' || (filter === 'critical' ? severityId(item.severityId || item.severity) === 'emergency' : !['رفع‌شده', 'تأیید نهایی'].includes(item.workflow)))
    .filter(item => !query || normalizeFaSearch(JSON.stringify(item)).includes(query));
  $('defectList').innerHTML = records.map(defect => `<div class="listitem" onclick="viewDefect('${esc(defect.id)}')">${recordCheckbox('defects',defect.id)}<div class="listmain"><b>${esc(defect.title)}</b><small>${esc(defect.bridgeName || '')}${defect.bridgeCode ? ` • ${esc(defect.bridgeCode)}` : ''} • مسئول: ${esc(defect.responsible || '—')}</small></div><span class="severity-badge ${severityClass(defect.severityId || defect.severity)}">${esc(severityLabel(defect.severityId || defect.severity))}</span>${recordDeleteButton('defects',defect.id)}</div>`).join('') || '<div class="empty">آسیبی با این فیلتر وجود ندارد.</div>';
}

function viewInspection(recordId) {
  const record = dbList('inspections').find(item => item.id === recordId);
  if (!record) return;
  const editable = ['draft', 'returned'].includes(record.workflowStatus || (record.status === 'پیش‌نویس' ? 'draft' : ''));
  const official = Boolean(window.isOfficialInspection?.(record));
  const damaged = (record.items || []).filter(item => item.applicable!==false && (severityRank(item.statusId || item.status) > 0 || severityId(item.statusId || item.status)==='uninspectable'));
  const bridgeLine = [record.bridgeName, record.bridgeCode, record.bridgeUse].filter(Boolean).map(esc).join(' • ');
  showGeneric('جزئیات بازدید', `<div class="rolebox"><b>${esc(record.no || '')}</b><div class="muted">${esc(record.jdate || '')} • ${bridgeLine} • ${esc(record.inspector || '')}</div></div>
    <div class="workflow-banner"><b>${esc(window.Governance?.workflowLabel(record) || record.status || '')}</b><span>${official ? 'قابل صدور رسمی' : 'فاقد خروجی رسمی'}</span></div>
    ${window.engineeringSummaryHtml?.(record) || ''}
    ${damaged.map(item => `<div class="rolebox"><b>${esc(item.code || occurrenceCode(item.itemId, item.occurrenceIndex || 1))} — ${esc(item.itemName || itemName(item.itemId))}</b><span class="severity-badge ${severityClass(item.statusId || item.status)}">${esc(severityLabel(item.statusId || item.status))}</span><div class="muted">${[item.location && `GPS ${Number(item.location.lat).toFixed(6)}, ${Number(item.location.lon).toFixed(6)}`, item.note].filter(Boolean).map(esc).join(' • ')}</div></div>`).join('') || '<div class="muted">مورد آسیب‌دیده ثبت نشده است.</div>'}
    ${record.signatureAttachment?.url ? `<div class="rolebox signature-attachment"><b>امضای بازرس</b><div class="muted">${esc(record.signatureAttachment.signer || record.inspector || '')} • ${esc(record.signatureAttachment.visitDate || record.jdate || '')}</div><img loading="lazy" src="${esc(record.signatureAttachment.url)}" alt="امضای بازرس"></div>` : ''}`,
  [
    ...(official ? [
      { t: 'PDF رسمی', c: 'primary', fn: `exportSingleInspection('${esc(record.id)}')` },
      { t: 'ZIP کامل رسمی', c: 'primary', fn: `exportSingleInspectionZip('${esc(record.id)}')` },
    ] : []),
    { t: editable ? 'ویرایش' : 'مدیریت رکورد', fn: editable ? `editInspection('${esc(record.id)}')` : `openRecordGovernance('${esc(record.id)}')` },
  ]);
}

async function archiveInspection(recordId) {
  const record = dbList('inspections').find(item => item.id === recordId);
  if (!record) return;
  if (record.submittedAt || !['draft', ''].includes(record.workflowStatus || '')) {
    window.openRecordGovernance?.(recordId);
    return;
  }
  if (!confirm(`بازدید «${record.no || record.bridgeName || ''}» و آسیب‌های وابسته حذف شوند؟`)) return;
  const result = await deleteInspectionEntity(recordId);
  if (!result?.ok) { toast('حذف بازدید انجام نشد؛ داده‌ها حفظ شدند.'); return; }
  audit('حذف دائمی بازدید', 'inspections', recordId, `${record.no} • ${record.bridgeName || ''}`);
  closeModal('genericModal');
  renderInspections();
  renderDashboard();
  toast('بازدید و سوابق وابسته حذف شدند.');
}

function viewDefect(defectId) {
  const defect = dbList('defects').find(item => item.id === defectId);
  if (!defect) return;
  activeRepairDefect = defectId;
  repairPhotos = [...(defect.repairPhotos || [])];
  window.activeRepairDefect = activeRepairDefect;
  window.repairPhotos = repairPhotos;
  showGeneric('گردش کار آسیب', `<div class="rolebox"><b>${esc(defect.title)}</b><div class="muted">${esc(defect.bridgeName || '')}${defect.bridgeCode ? ` • ${esc(defect.bridgeCode)}` : ''}</div><span class="severity-badge ${severityClass(defect.severityId || defect.severity)}">${esc(severityLabel(defect.severityId || defect.severity))}</span></div>
    <div class="field"><label>وضعیت</label><select id="wfSel"><option>باز</option><option>در دست اقدام</option><option>رفع‌شده</option><option>تأیید نهایی</option></select></div>
    <div class="field"><label>نتیجه تعمیر / اقدام</label><textarea id="defAction">${esc(defect.repairAction || '')}</textarea></div>
    <div class="field"><label>مسئول</label><input id="defResp" value="${esc(defect.responsible || '')}"></div>
    <div class="photo-grid" id="repairPhotoGrid"></div><div class="btnline"><button class="btn" onclick="pickRepairPhoto()">انتخاب تصویر بعد از تعمیر</button><button class="btn" onclick="takeRepairPhoto()">عکس گرفتن</button></div>`,
  [{ t: 'ذخیره', c: 'primary', fn: `saveDefectUpdate('${esc(defectId)}')` }]);
  setTimeout(() => { $('wfSel').value = defect.workflow || 'باز'; window.renderRepairPhotos?.(); }, 0);
}

function saveDefectUpdate(defectId) {
  const defect = dbList('defects').find(item => item.id === defectId);
  if (!defect) return;
  const workflow = $('wfSel').value;
  const user = getActiveUser();
  if (workflow === 'تأیید نهایی' && !['سرپرست', 'مدیر سیستم'].includes(user?.role || '')) {
    toast('تأیید نهایی فقط توسط سرپرست یا مدیر سیستم انجام می‌شود.');
    return;
  }
  if (workflow === 'تأیید نهایی' && severityId(defect.severityId || defect.severity) === 'emergency' && (!$('defAction').value.trim() || !$('defResp').value.trim() || !repairPhotos.length)) {
    toast('برای بستن آسیب اضطراری، نتیجه تعمیر، مسئول و تصویر بعد از تعمیر الزامی است.');
    return;
  }
  Object.assign(defect, {
    workflow,
    repairAction: $('defAction').value.trim(),
    responsible: $('defResp').value.trim(),
    repairPhotos,
    resolvedAt: ['رفع‌شده', 'تأیید نهایی'].includes(workflow) ? Date.now() : defect.resolvedAt,
  });
  dbSave('defects', defect);
  audit('تغییر گردش کار', 'defects', defectId, workflow);
  closeModal('genericModal');
  renderDefects();
  toast('وضعیت آسیب ذخیره شد.');
}

Object.assign(window, {
  renderDashboard, filteredInspectionRecords, renderDrafts, renderInspections, renderDefects, selectAllRecords, clearRecordSelections, deleteSelected, deleteSingleRecord,
  viewInspection, archiveInspection, viewDefect, saveDefectUpdate,
});

/* Users, references and settings */
function renderUsers() {
  const activeId = getActiveUser()?.id;
  $('userList').innerHTML = dbList('users').map(user => `<div class="listitem user-row">${recordCheckbox('users',user.id)}<div class="listmain"><b>${esc(user.name)}</b><small>${esc(user.role || 'کاربر')}</small></div>${user.id === activeId ? '<span class="pill green">فعال</span>' : `<button class="btn" type="button" onclick="setActiveUser('${esc(user.id)}')">فعال کردن</button>`}<button class="btn" type="button" onclick="editUser('${esc(user.id)}')">ویرایش</button><button class="btn danger" type="button" onclick="deleteUser('${esc(user.id)}')">حذف</button></div>`).join('') || '<div class="empty">کاربری ثبت نشده است.</div>';
}

function editUser(userId = '') {
  const user = userId ? dbList('users').find(item => item.id === userId) : null;
  showGeneric(user ? 'ویرایش کاربر' : 'افزودن کاربر', `<div class="field"><label>نام</label><input id="userEditorName" maxlength="80" value="${esc(user?.name || '')}"></div><div class="field"><label>نقش</label><select id="userEditorRole"><option ${user?.role === 'بازرس' ? 'selected' : ''}>بازرس</option><option ${user?.role === 'سرپرست' ? 'selected' : ''}>سرپرست</option><option ${user?.role === 'مدیر سیستم' ? 'selected' : ''}>مدیر سیستم</option></select></div>`, [{ t: 'ذخیره', c: 'primary', fn: `saveUserEditor('${esc(userId)}')` }]);
}

function saveUserEditor(userId = '') {
  const name = $('userEditorName')?.value.trim() || '';
  const role = $('userEditorRole')?.value || '';
  if (name.length < 2) { toast('نام کاربر باید حداقل دو نویسه باشد.'); return; }
  if (!['بازرس', 'سرپرست', 'مدیر سیستم'].includes(role)) { toast('نقش کاربر معتبر نیست.'); return; }
  if (dbList('users').some(item => item.id !== userId && normalizeFaSearch(item.name) === normalizeFaSearch(name))) { toast('کاربری با این نام قبلاً ثبت شده است.'); return; }
  const existing = userId ? dbList('users').find(item => item.id === userId) : null;
  const user = { ...(existing || {}), id: existing?.id || id('user'), name, role, active: existing?.active || false, archived: false };
  dbSave('users', user);
  if (!getActiveUser()) setActiveUser(user.id);
  audit(existing ? 'ویرایش کاربر' : 'افزودن کاربر', 'users', user.id, `${user.name} • ${user.role}`);
  closeModal('genericModal');
  renderUsers();
  renderHeader();
  toast(existing ? 'کاربر ویرایش شد.' : 'کاربر ذخیره شد.');
}

function setActiveUser(userId) {
  const users = dbList('users');
  const user = users.find(item => item.id === userId);
  if (!user) return;
  users.forEach(item => { if (!!item.active !== (item.id === userId)) dbSave('users', { ...item, active: item.id === userId }); });
  const settings = dbList('settings').find(item => item.id === 'main') || { id: 'main' };
  dbSave('settings', { ...settings, activeUser: userId });
  audit('تغییر کاربر فعال', 'users', userId, user.name);
  renderUsers();
  renderHeader();
  toast('کاربر فعال تغییر کرد.');
}

async function deleteUser(userId,skipConfirm=false) {
  const users = dbList('users');
  const user = users.find(item => item.id === userId);
  if (!user) return;
  if (!skipConfirm && !confirm(`کاربر «${user.name || ''}» حذف شود؟`)) return;
  if (users.length <= 1) { toast('حداقل یک کاربر باید باقی بماند.'); return; }
  if (user.role === 'مدیر سیستم' && users.filter(item => item.id !== userId && item.role === 'مدیر سیستم').length === 0) { toast('حداقل یک مدیر سیستم باید باقی بماند.'); return; }
  const wasActive = getActiveUser()?.id === userId;
  const result = await dbDeleteBatch([{kind:'users',id:userId}]);
  if (!result?.ok) { toast('حذف کاربر انجام نشد.'); return; }
  audit('حذف کاربر', 'users', userId, user.name);
  if (wasActive) setActiveUser(dbList('users').find(item => item.id !== userId)?.id || '');
  renderUsers();
  renderHeader();
  renderDashboard();
}

function masterTab() {
  const refs = window.BRIDGE_ENGINEERING_REFERENCES || {};
  $('masterList').innerHTML = `<div class="notice">این فهرست برای طبقه‌بندی بازرسی چشمی است. حدود عددی، ارزیابی ظرفیت باربری و تصمیم انسداد باید از اسناد مصوب مالک پل و مهندس واجدصلاحیت اخذ شود.</div><div class="list">${Object.values(refs).map(reference => `<div class="listitem"><div class="listmain"><b>${esc(reference)}</b><small>مرجع مهندسی چک‌لیست</small></div></div>`).join('')}</div>`;
}

function reportRecords() {
  const records = dbList('inspections').filter(item => !item.archived && window.isOfficialInspection?.(item));
  if ($('reportPeriod').value === 'all') return records;
  const key = enNum(jfmt(reportJ));
  if ($('reportPeriod').value === 'day') return records.filter(item => enNum(item.jdate) === key);
  const yearMonth = key.split('/').slice(0, 2).join('/');
  return records.filter(item => enNum(item.jdate).startsWith(`${yearMonth}/`));
}

function renderReportStats() {
  $('reportDateBtn').textContent = jfmt(reportJ);
  const records = reportRecords();
  const defects = dbList('defects').filter(defect => records.some(record => record.id === defect.inspectionId));
  $('reportSummary').innerHTML = `<div class="grid4"><div class="stat"><div class="num">${faNum(records.length)}</div><small>بازدید</small></div><div class="stat warn"><div class="num">${faNum(defects.length)}</div><small>آسیب</small></div><div class="stat danger"><div class="num">${faNum(defects.filter(defect => severityId(defect.severityId || defect.severity) === 'emergency').length)}</div><small>اضطراری</small></div><div class="stat ok"><div class="num">${faNum(defects.filter(defect => ['رفع‌شده', 'تأیید نهایی'].includes(defect.workflow)).length)}</div><small>رفع‌شده</small></div></div>`;
}

function loadSettings() {
  const settings = dbList('settings').find(item => item.id === 'main') || {};
  $('orgName').value = settings.orgName || 'شرکت مهندسین مشاور هگزا';
  $('unitName').value = settings.unitName || '';
  $('showGregorian').value = settings.showGregorian || 'yes';
  const appearance = normalizedAppearance(settings);
  writeAppearanceControls(appearance);
  applyAppearance(appearance);
  $('appVersion').textContent = `نسخه ${Native.appVersion()} • پایگاه داده محلی • بدون مجوز اینترنت`;
}

function saveSettings() {
  const prior = dbList('settings').find(item => item.id === 'main') || {};
  const appearance = appearanceFromControls();
  dbSave('settings', {
    ...prior, id: 'main', orgName: $('orgName').value.trim() || 'شرکت مهندسین مشاور هگزا',
    unitName: $('unitName').value.trim(), showGregorian: $('showGregorian').value,
    theme: appearance.theme, appearance,
  });
  applyAppearance(appearance);
  toast('تنظیمات ذخیره شد.');
}

function setTheme(value) { document.body.classList.toggle('dark', value === 'night' || value === 'dark'); }

Object.assign(window, {
  renderUsers, editUser, saveUserEditor, setActiveUser, deleteUser, masterTab,
  reportRecords, renderReportStats, loadSettings, saveSettings, setTheme,
});

/* Signature */
let sigTarget = '';
let sigInk = false;
function resizeCanvas(canvas) {
  const rect = canvas.getBoundingClientRect();
  const density = Math.min(devicePixelRatio || 1, 2.5);
  canvas.width = Math.max(1, Math.round(rect.width * density));
  canvas.height = Math.max(1, Math.round(rect.height * density));
  const context = canvas.getContext('2d');
  context.setTransform(density, 0, 0, density, 0, 0);
  return context;
}

function openSignature(target) {
  sigTarget = target;
  $('signatureModal').classList.add('open');
  setTimeout(() => {
    const canvas = $('signatureCanvas');
    const context = resizeCanvas(canvas);
    context.fillStyle = '#fff';
    context.fillRect(0, 0, canvas.clientWidth, canvas.clientHeight);
    context.strokeStyle = '#153247';
    context.lineWidth = 1.8;
    context.lineCap = 'round';
    context.lineJoin = 'round';
    sigInk = false;
    let point = null;
    canvas.onpointerdown = event => {
      event.preventDefault();
      canvas.setPointerCapture?.(event.pointerId);
      const rect = canvas.getBoundingClientRect();
      point = { x: event.clientX - rect.left, y: event.clientY - rect.top };
      sigInk = true;
    };
    canvas.onpointermove = event => {
      if (!point) return;
      event.preventDefault();
      const rect = canvas.getBoundingClientRect();
      const next = { x: event.clientX - rect.left, y: event.clientY - rect.top };
      context.beginPath(); context.moveTo(point.x, point.y); context.lineTo(next.x, next.y); context.stroke(); point = next;
    };
    canvas.onpointerup = canvas.onpointercancel = () => { point = null; };
  }, 30);
}

function clearSignature() {
  const canvas = $('signatureCanvas');
  const context = resizeCanvas(canvas);
  context.fillStyle = '#fff';
  context.fillRect(0, 0, canvas.clientWidth, canvas.clientHeight);
  sigInk = false;
}

function saveSignature() {
  if (!sigInk) { toast('ابتدا امضا کنید.'); return; }
  if (sigTarget !== 'inspector' || !currentInspection) { toast('بازدید فعالی برای ثبت امضا وجود ندارد.'); return; }
  const user = getActiveUser();
  if (!user) { toast('برای ثبت امضا، ورود امن الزامی است.'); window.Governance?.openAuthentication(); return; }
  const signer = user.name || '';
  if ($('inspectorName')) $('inspectorName').value = signer;
  const visitDate = $('jdateBtn')?.textContent.trim() || jfmt(selectedJ);
  const dataUri = $('signatureCanvas').toDataURL('image/png');
  if (!Native?.isNative?.() || !Native.saveSignature({ dataUri, signer, signerId: user.id, visitDate, ownerId: currentInspection.id })) {
    toast('ذخیره فایل امضا در دستگاه آغاز نشد.');
    return;
  }
  toast('در حال ذخیره امن تصویر امضا…');
}

window.receiveSignatureSaved = function receiveSignatureSaved(raw) {
  let media = {};
  try { media = typeof raw === 'string' ? JSON.parse(raw) : (raw || {}); } catch (_) { media = {}; }
  if (media.ownerId && media.ownerId!==currentInspection?.id) return;
  if (!media.mediaId || !currentInspection) { toast('ذخیره تصویر امضا ناموفق بود.'); return; }
  currentInspection.signaturePresent = true;
  currentInspection.signatureAttachment = {
    ...media, signer: media.signer || getActiveUser()?.name || 'بازرس', signerId: media.signerId || getActiveUser()?.id || '', visitDate: media.visitDate || $('jdateBtn')?.textContent.trim() || '',
  };
  window.autosaveCurrentInspection?.();
  closeModal('signatureModal');
  toast('امضا به بازدید پیوست شد.');
};

Object.assign(window, { openSignature, clearSignature, saveSignature });

window.onRailReady(() => {
  seed();
  const now = nowDates();
  selectedJ = now.j;
  reportJ = now.j;
  $('jdateBtn').textContent = jfmt(now.j);
  $('gdate').value = iso(now.g);
  $('reportDateBtn').textContent = jfmt(now.j);
  renderHeader();
  const settings = dbList('settings').find(item => item.id === 'main') || {};
  applyAppearance(normalizedAppearance(settings));
  renderDashboard();
  document.addEventListener('toggle',event=>{const opened=event.target;if(!(opened instanceof HTMLDetailsElement)||!opened.open)return;const parent=opened.parentElement;if(!parent)return;parent.querySelectorAll(':scope > details[open]').forEach(other=>{if(other!==opened)other.open=false;});},true);
  setTimeout(() => { $('splash').style.opacity = '0'; $('splash').style.visibility = 'hidden'; }, 650);
});
