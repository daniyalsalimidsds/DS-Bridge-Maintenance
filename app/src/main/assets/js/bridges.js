(function () {
  'use strict';

  let bridgeEditorId = '';
  let pendingBridgeLocation = null;
  const tagLabels = Object.freeze({
    road:'راهی', railway:'راه‌آهن', concrete:'بتنی', prestressed:'پیش‌تنیده', steel:'فولادی', masonry:'بنایی', timber:'چوبی',
    composite:'مختلط/کامپوزیت', frp:'FRP', orthotropic:'عرشه ارتوتروپیک', truss:'خرپایی', arch:'قوسی', cable:'کابلی', box:'جعبه‌ای', girder:'تیری', culvert:'آبرو',
    deck:'عرشه', pier:'پایه میانی', abutment:'کوله', foundation:'پی و شمع', bearing:'تکیه‌گاه', joint:'درز انبساط', drainage:'زهکشی', barrier:'نرده و جان‌پناه',
    sidewalk:'پیاده‌رو', approach:'دال دسترسی', utilities:'تأسیسات عبوری', diaphragm:'دیافراگم', waterway:'آبراهه', repair:'سابقه بهسازی', seismic:'تمهیدات لرزه‌ای',
    'road-pavement':'روسازی راه', 'asphalt-pavement':'روسازی آسفالتی', 'concrete-pavement':'روسازی بتنی', 'masonry-pavement':'روسازی بنایی',
    'ballasted-track':'خط بالاستی', 'direct-track':'اتصال مستقیم ریل', neoprene:'نئوپرنی/الاستومری',
  });

  function bridgeName(bridge) {
    return window.bridgeProfileValue?.(bridge, 'نام پل') || bridge?.name || 'پل بدون نام';
  }

  function bridgeCode(bridge) {
    return window.bridgeProfileValue?.(bridge, 'کد پل') || bridge?.code || '';
  }

  function bridgeUse(bridge) {
    return window.bridgeProfileValue?.(bridge, 'کاربری اصلی') || '';
  }

  function locationText(location) {
    if (!location || !Number.isFinite(Number(location.lat)) || !Number.isFinite(Number(location.lon))) return 'موقعیت ثبت نشده';
    const accuracy = Number.isFinite(Number(location.accuracyM)) ? ` • دقت ±${faNum(Math.round(Number(location.accuracyM)))} متر` : '';
    const source = location.source === 'manual' ? 'دستی' : 'GPS';
    return `${faNum(Number(location.lat).toFixed(6))}, ${faNum(Number(location.lon).toFixed(6))} • ${source}${accuracy}`;
  }

  function visibleBridges() {
    return dbList('bridges').filter(bridge => !bridge.archived).sort((a, b) => bridgeName(a).localeCompare(bridgeName(b), 'fa'));
  }

  function renderBridges() {
    const host = document.getElementById('bridgeList');
    if (!host) return;
    const query = normalizeFaSearch(document.getElementById('bridgeSearch')?.value || '');
    const records = visibleBridges().filter(bridge => !query || normalizeFaSearch(JSON.stringify(bridge)).includes(query));
    host.innerHTML = records.map(bridge => {
      const tags = [...(window.bridgeProfileTags?.(bridge) || [])].filter(tag => !['always', 'incomplete'].includes(tag));
      return `<div class="bridge-card"><input class="record-check" type="checkbox" data-scope="bridges" value="${esc(bridge.id)}" onclick="event.stopPropagation()" aria-label="انتخاب پل"><button class="bridge-card-main" type="button" onclick="viewBridge('${esc(bridge.id)}')"><b>${esc(bridgeName(bridge))}</b><small>${[bridgeCode(bridge), bridgeUse(bridge), locationText(bridge.location)].filter(Boolean).map(esc).join(' • ')}</small><span class="bridge-tags">${tags.slice(0, 5).map(tag => `<span>${esc(tagLabels[tag] || tag)}</span>`).join('')}</span></button><div class="bridge-card-actions"><button class="btn" type="button" onclick="editBridge('${esc(bridge.id)}')">ویرایش</button><button class="btn" type="button" onclick="shareBridge('${esc(bridge.id)}')">اشتراک‌گذاری</button><button class="btn primary" type="button" onclick="startInspectionForBridge('${esc(bridge.id)}')">بازدید جدید</button><button class="btn danger" type="button" onclick="archiveBridge('${esc(bridge.id)}')">حذف</button></div></div>`;
    }).join('') || '<div class="empty">هنوز پلی ثبت نشده است. ابتدا «افزودن پل» را انتخاب کنید.</div>';
    const count = document.getElementById('bridgeCount');
    if (count) count.textContent = `${faNum(records.length)} پل`;
  }

  function fieldControl(field, value) {
    const required = field.required ? 'required' : '';
    const common = `aria-label="${esc(field.label)}" data-bridge-field="${esc(field.id)}" ${required}`;
    if (field.kind === 'select') {
      return `<select ${common}><option value="">— انتخاب کنید —</option>${(field.options || []).map(option => `<option value="${esc(option)}" ${String(value) === String(option) ? 'selected' : ''}>${esc(option)}</option>`).join('')}</select>`;
    }
    if (field.kind === 'number') {
      return `<input ${common} type="number" inputmode="decimal" step="any" value="${esc(value ?? '')}" placeholder="${esc(field.example || '')}">`;
    }
    return `<input ${common} type="text" maxlength="240" value="${esc(value || '')}" placeholder="${esc(field.example || '')}">`;
  }

  function renderBridgeProfileForm(bridge) {
    const host = document.getElementById('bridgeProfileSections');
    if (!host) return;
    const schema = window.BRIDGE_PROFILE_SCHEMA;
    const profile = bridge?.profile || {};
    host.innerHTML = (schema?.sections || []).map((section, index) => `<details class="profile-section" ${index < 2 ? 'open' : ''}><summary><span>${faNum(index + 1)}. ${esc(section.title)}</span><small>${faNum((section.fields || []).length)} فیلد</small></summary><div class="form-grid">${(section.fields || []).map(field => `<div class="field"><label>${esc(field.label)}${field.required ? ' *' : ''}</label>${fieldControl(field, profile[field.id])}</div>`).join('')}</div></details>`).join('');
    pendingBridgeLocation = bridge?.location ? { ...bridge.location } : null;
    refreshBridgeLocationEditor();
  }

  function editBridge(bridgeId = '') {
    bridgeEditorId = bridgeId;
    const bridge = bridgeId ? dbList('bridges').find(item => item.id === bridgeId) : null;
    const title = document.getElementById('bridgeFormTitle');
    if (title) title.textContent = bridge ? 'ویرایش شناسنامه فنی پل' : 'افزودن پل جدید';
    go('bridgeForm');
    renderBridgeProfileForm(bridge);
    setTimeout(() => document.querySelector('[data-bridge-field]')?.focus(), 60);
  }

  function collectBridgeProfile() {
    const profile = {};
    document.querySelectorAll('[data-bridge-field]').forEach(control => {
      const value = control.type === 'number' ? enNum(control.value.trim()) : control.value.trim();
      profile[control.dataset.bridgeField] = value;
    });
    for (const section of window.BRIDGE_PROFILE_SCHEMA?.sections || []) {
      const presence = (section.fields || []).find(field => /وجود/.test(field.label));
      if (!presence || !/وجود ندارد|خیر/.test(normalizeFaSearch(profile[presence.id] || ''))) continue;
      (section.fields || []).forEach(field => {
        if (field.id !== presence.id) profile[field.id] = '';
      });
    }
    const specialPresenceId = fieldIdByLabel('وجود سیستم ویژه؟');
    if (/وجود ندارد|خیر/.test(normalizeFaSearch(profile[specialPresenceId] || ''))) {
      ['سیستم سازه‌ای اصلی', 'نوع سیستم عرشه'].forEach(label => {
        const fieldId = fieldIdByLabel(label);
        if (fieldId && /ترکه|معلق|پل کابلی/.test(normalizeFaSearch(profile[fieldId] || ''))) profile[fieldId] = '';
      });
    }
    return profile;
  }

  function validateBridgeProfile(profile) {
    for (const section of window.BRIDGE_PROFILE_SCHEMA?.sections || []) {
      for (const field of section.fields || []) {
        const value = String(profile[field.id] ?? '').trim();
        if (field.required && !value) return `${field.label} الزامی است.`;
        if (field.kind === 'select' && value && !(field.options || []).includes(value)) return `گزینه انتخاب‌شده برای «${field.label}» معتبر نیست.`;
        if (field.kind === 'number' && value && !Number.isFinite(Number(enNum(value)))) return `مقدار «${field.label}» باید عدد معتبر باشد.`;
      }
    }
    return '';
  }

  function fieldIdByLabel(label) {
    for (const section of window.BRIDGE_PROFILE_SCHEMA?.sections || []) {
      const field = (section.fields || []).find(item => item.label === label);
      if (field) return field.id;
    }
    return '';
  }

  function collectManualLocation() {
    const latText = enNum(document.getElementById('bridgeLatitude')?.value || '');
    const lonText = enNum(document.getElementById('bridgeLongitude')?.value || '');
    if (!latText && !lonText) return null;
    const lat = Number(latText), lon = Number(lonText);
    if (!Number.isFinite(lat) || lat < -90 || lat > 90) throw new Error('عرض جغرافیایی باید بین ۹۰- و ۹۰ درجه باشد.');
    if (!Number.isFinite(lon) || lon < -180 || lon > 180) throw new Error('طول جغرافیایی باید بین ۱۸۰- و ۱۸۰ درجه باشد.');
    const sameAsGps = pendingBridgeLocation && Math.abs(Number(pendingBridgeLocation.lat) - lat) < 1e-8 && Math.abs(Number(pendingBridgeLocation.lon) - lon) < 1e-8;
    return {
      lat,
      lon,
      accuracyM: sameAsGps ? pendingBridgeLocation.accuracyM : null,
      capturedAt: sameAsGps ? pendingBridgeLocation.capturedAt : Date.now(),
      provider: sameAsGps ? pendingBridgeLocation.provider : 'manual',
      source: sameAsGps ? 'gps' : 'manual',
    };
  }

  function saveBridge() {
    const profile = collectBridgeProfile();
    const profileError = validateBridgeProfile(profile);
    if (profileError) { toast(profileError); return; }
    const nameId = fieldIdByLabel('نام پل');
    const codeId = fieldIdByLabel('کد پل');
    const useId = fieldIdByLabel('کاربری اصلی');
    const name = String(profile[nameId] || '').trim();
    const code = String(profile[codeId] || '').trim();
    const use = String(profile[useId] || '').trim();
    if (name.length < 2) { toast('نام پل باید حداقل دو نویسه باشد.'); return; }
    if (!use) { toast('کاربری اصلی پل را انتخاب کنید.'); return; }
    const duplicateCode = code && dbList('bridges').find(item => item.id !== bridgeEditorId && !item.archived && normalizeFaSearch(bridgeCode(item)) === normalizeFaSearch(code));
    if (duplicateCode) { toast('کد پل باید یکتا باشد.'); return; }
    let location;
    try { location = collectManualLocation(); } catch (error) { toast(error.message); return; }
    const prior = bridgeEditorId ? dbList('bridges').find(item => item.id === bridgeEditorId) : null;
    const bridge = {
      ...(prior || {}),
      id: prior?.id || id('bridge'),
      name,
      code,
      use,
      profile,
      profileSchemaVersion: window.BRIDGE_PROFILE_SCHEMA?.version || '',
      profileSourceSha256: window.BRIDGE_PROFILE_SCHEMA?.sourceSha256 || '',
      location,
      archived: false,
      createdAt: prior?.createdAt || Date.now(),
      updatedAt: Date.now(),
    };
    dbSave('bridges', bridge);
    audit(prior ? 'ویرایش پل' : 'افزودن پل', 'bridges', bridge.id, `${name}${code ? ' • ' + code : ''}`);
    bridgeEditorId = bridge.id;
    toast(prior ? 'شناسنامه پل ویرایش شد.' : 'پل با موفقیت ثبت شد.');
    go('bridges');
    renderBridges();
  }

  function refreshBridgeLocationEditor() {
    const lat = document.getElementById('bridgeLatitude');
    const lon = document.getElementById('bridgeLongitude');
    if (lat) lat.value = pendingBridgeLocation?.lat ?? '';
    if (lon) lon.value = pendingBridgeLocation?.lon ?? '';
    const text = document.getElementById('bridgeLocationState');
    if (text) text.textContent = locationText(pendingBridgeLocation);
  }

  function captureBridgeLocation() {
    const state = document.getElementById('bridgeLocationState');
    if (state) state.textContent = 'در حال دریافت موقعیت GPS…';
    if (!Native?.isNative?.() || !Native.requestLocation({ target: 'bridge-profile' })) {
      if (state) state.textContent = 'GPS فقط در نسخه اندروید در دسترس است؛ مختصات را دستی وارد کنید.';
      toast('دریافت GPS آغاز نشد. ورود دستی مختصات همچنان در دسترس است.');
    }
  }

  function receiveBridgeLocationResult(result) {
    if (!result?.ok) {
      const messages = {
        'permission-denied': 'مجوز موقعیت مکانی داده نشد.',
        'location-disabled': 'GPS/موقعیت دستگاه خاموش است.',
        'location-timeout': 'دریافت موقعیت تازه در زمان مقرر انجام نشد.',
        'location-unavailable': 'موقعیت مکانی در دسترس نیست.',
        'mock-location': 'موقعیت شبیه‌سازی‌شده پذیرفته نمی‌شود.',
        'stale-location': 'موقعیت دریافتی قدیمی است؛ دوباره تلاش کنید.',
      };
      const message = messages[result?.error] || 'دریافت موقعیت مکانی ناموفق بود.';
      const state = document.getElementById('bridgeLocationState');
      if (state) state.textContent = message;
      toast(message);
      return;
    }
    const lat = Number(result.lat), lon = Number(result.lon);
    if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      toast('مختصات GPS معتبر نیست.');
      return;
    }
    pendingBridgeLocation = {
      lat,
      lon,
      accuracyM: Number.isFinite(Number(result.accuracyM)) ? Number(result.accuracyM) : null,
      capturedAt: Number(result.capturedAt) || Date.now(),
      provider: result.provider || 'gps',
      source: 'gps',
    };
    refreshBridgeLocationEditor();
    toast('موقعیت GPS پل ثبت شد.');
  }

  function clearBridgeLocation() {
    pendingBridgeLocation = null;
    refreshBridgeLocationEditor();
  }

  function viewBridge(bridgeId) {
    const bridge = dbList('bridges').find(item => item.id === bridgeId);
    if (!bridge) return;
    const entries = window.bridgeProfileEntries?.(bridge) || [];
    const groups = (window.BRIDGE_PROFILE_SCHEMA?.sections || []).map(section => {
      const active = window.bridgeProfileSectionActive?.(bridge, section.id) !== false;
      const rows = entries.filter(item => item.id.startsWith(section.id + '__') && item.value.trim() && window.bridgeProfileEntryActive?.(bridge, item) !== false)
        .filter(item => active || /وجود/.test(item.label));
      if (!rows.length) return '';
      return `<details class="profile-view"><summary>${esc(section.title)}</summary>${rows.map(row => `<div><span>${esc(row.label)}</span><b>${esc(row.value)}</b></div>`).join('')}</details>`;
    }).join('');
    showGeneric('شناسنامه فنی پل', `<div class="rolebox"><b>${esc(bridgeName(bridge))}</b><div class="muted">${[bridgeCode(bridge), bridgeUse(bridge), locationText(bridge.location)].filter(Boolean).map(esc).join(' • ')}</div></div>${groups}`,
      [{ t: 'ویرایش', fn: `closeModal('genericModal');editBridge('${esc(bridgeId)}')` }, { t: 'بازدید جدید', c: 'primary', fn: `closeModal('genericModal');startInspectionForBridge('${esc(bridgeId)}')` }]);
  }

  async function archiveBridge(bridgeId) {
    const bridge = dbList('bridges').find(item => item.id === bridgeId);
    if (!bridge) return;
    const related = dbList('inspections').filter(item => item.bridgeId === bridgeId);
    const warning = related.length ? `\n${faNum(related.length)} بازدید و آسیب‌های وابسته نیز حذف می‌شوند.` : '';
    if (!confirm(`پل «${bridgeName(bridge)}» به‌طور کامل حذف شود؟${warning}`)) return;
    const result = await (window.dbDeleteBatch?.(window.bridgeDeleteEntries?.(bridgeId) || [{ kind: 'bridges', id: bridgeId }]) || { ok: false });
    if (!result?.ok) { toast('حذف پل انجام نشد؛ داده‌ها حفظ شدند.'); return; }
    audit('حذف دائمی پل', 'bridges', bridge.id, bridgeName(bridge));
    renderBridges();
    renderDashboard?.();
    toast('پل و سوابق وابسته به‌طور کامل حذف شدند.');
  }

  function selectedInspectionBridge() {
    const select=document.getElementById('inspectionBridge');
    const idValue = select ? select.value : window.currentInspection?.bridgeId || '';
    return dbList('bridges').find(item => item.id === idValue) || null;
  }

  function renderInspectionBridgeSelector(selectedId = '') {
    const select = document.getElementById('inspectionBridge');
    if (!select) return;
    const options = visibleBridges();
    select.innerHTML = `<option value="">— انتخاب پل —</option>${options.map(bridge => `<option value="${esc(bridge.id)}" ${bridge.id === selectedId ? 'selected' : ''}>${esc(bridgeName(bridge))}${bridgeCode(bridge) ? ' — ' + esc(bridgeCode(bridge)) : ''}</option>`).join('')}`;
    const hint = document.getElementById('selectedBridgeSummary');
    const bridge = options.find(item => item.id === (selectedId || select.value));
    if (hint) hint.textContent = bridge ? `${bridgeUse(bridge)} • ${locationText(bridge.location)}` : 'ابتدا پل را انتخاب کنید تا چک‌لیست اختصاصی ساخته شود.';
  }

  function handleInspectionBridgeChanged() {
    const bridge = selectedInspectionBridge();
    const old=window.currentInspection;
    if(old?.bridgeId && old.bridgeId!==(bridge?.id || '') && (window.checklistHasEnteredData?.(window.collectInspectionItems?.() || []) || old.signatureAttachment || old.elementAssessments?.length || old.internationalAssessment?.reviewer)) {
      document.getElementById('inspectionBridge').value=old.bridgeId;toast('برای تغییر پل، بازدید جدید بسازید؛ اطلاعات این بازدید حفظ شد.');return;
    }
    const hint = document.getElementById('selectedBridgeSummary');
    if (!bridge) {
      if (hint) hint.textContent = 'ابتدا پل را انتخاب کنید تا چک‌لیست اختصاصی ساخته شود.';
      if (window.currentInspection) {
        window.currentInspection.bridgeId = '';
        window.currentInspection.bridgeSnapshot = null;
        window.currentInspection.items = [];
      }
      renderChecklist();
      return;
    }
    if (window.currentInspection) {
      const oldId = window.currentInspection.bridgeId;
      window.currentInspection.bridgeId = bridge.id;
      window.currentInspection.bridgeName = bridgeName(bridge);
      window.currentInspection.bridgeCode = bridgeCode(bridge);
      window.currentInspection.bridgeSnapshot = JSON.parse(JSON.stringify(bridge));
      if (oldId && oldId !== bridge.id) {window.currentInspection.items = [];window.currentInspection.itemPhotos={};window.currentInspection.itemLocations={};window.currentInspection.internationalAssessment=null;window.currentInspection.elementAssessments=[];delete window.currentInspection.scores;}
    }
    if (hint) hint.textContent = `${bridgeUse(bridge)} • ${locationText(bridge.location)}`;
    renderChecklist();
    window.autosaveCurrentInspection?.();
  }

  function startInspectionForBridge(bridgeId) {
    newInspection(bridgeId);
  }

  function shareBridge(bridgeId){const bridge=dbList('bridges').find(item=>item.id===bridgeId);if(!bridge)return;if(!Native?.exportBridge?.(bridge)){toast('اشتراک‌گذاری فایل پل فقط در نسخه اندروید در دسترس است.');}}
  function uploadBridgeFile(){if(!Native?.pickBridge?.())toast('انتخاب فایل پل فقط در نسخه اندروید در دسترس است.');}
  window.receiveBridgeImport=function(raw){let result={};try{result=typeof raw==='string'?JSON.parse(raw):raw||{};}catch(_){}if(!result.ok||!result.bridge){toast('فایل پل معتبر نیست.');return;}const bridge={...result.bridge,id:id('bridge'),createdAt:Date.now(),updatedAt:Date.now(),archived:false};dbSave('bridges',bridge);renderBridges();toast('پل از فایل وارد شد.');};

  window.renderBridges = renderBridges;
  window.editBridge = editBridge;
  window.saveBridge = saveBridge;
  window.captureBridgeLocation = captureBridgeLocation;
  window.receiveBridgeLocationResult = receiveBridgeLocationResult;
  window.clearBridgeLocation = clearBridgeLocation;
  window.viewBridge = viewBridge;
  window.archiveBridge = archiveBridge;
  window.bridgeName = bridgeName;
  window.bridgeCode = bridgeCode;
  window.bridgeUse = bridgeUse;
  window.bridgeLocationText = locationText;
  window.selectedInspectionBridge = selectedInspectionBridge;
  window.renderInspectionBridgeSelector = renderInspectionBridgeSelector;
  window.handleInspectionBridgeChanged = handleInspectionBridgeChanged;
  window.startInspectionForBridge = startInspectionForBridge;
  window.shareBridge = shareBridge;
  window.uploadBridgeFile = uploadBridgeFile;
})();
