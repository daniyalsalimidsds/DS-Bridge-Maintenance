(function () {
  'use strict';

  const ROLES = Object.freeze({
    INSPECTOR: 'بازرس',
    QC: 'بازبین کنترل کیفیت',
    SUPERVISOR: 'سرپرست',
    ADMIN: 'مدیر سیستم',
  });
  const VISIT_TYPES = Object.freeze([
    'بازدید چشمی مستمر',
    'بازدید دوره‌ای شش‌ماهه خط و ابنیه',
    'پس از خرابی',
    'بازدید ویژه',
    'بازدید روزانه',
  ]);
  const ERROR_MESSAGES = Object.freeze({
    'authentication-required': 'نشست شما قفل است؛ دوباره وارد شوید.',
    'invalid-credentials': 'شناسه کاربر یا رمز/PIN صحیح نیست.',
    locked: 'ورود به‌دلیل تلاش‌های ناموفق موقتاً قفل شده است.',
    'weak-pin': 'PIN باید ۶ تا ۱۲ رقم، یا گذرواژه‌ای با حداقل ۸ نویسه باشد.',
    'role-not-authorized': 'نقش شما مجوز انجام این عملیات را ندارد.',
    'approved-qualification-required': 'صلاحیت حرفه‌ای این کاربر هنوز به‌صورت مستقل تأیید نشده است.',
    'qualification-expired': 'اعتبار صلاحیت حرفه‌ای کاربر منقضی شده است.',
    'review-qualification-required': 'این کاربر مجوز کنترل کیفیت گزارش را ندارد.',
    'inspection-qualification-required': 'این کاربر مجوز اجرای بازدید میدانی را ندارد.',
    'inspection-type-not-authorized': 'این نوع بازدید در دامنه صلاحیت کاربر نیست.',
    'independent-reviewer-required': 'بازرس نمی‌تواند گزارش خود را کنترل و تأیید کند.',
    'independent-qualification-approval-required': 'تأیید صلاحیت باید توسط کاربر مدیریتی دیگری انجام شود.',
    'inspector-reassignment-reason-required': 'تغییر بازرس رکورد موجود به دلیل مستند حداقل ۱۰ نویسه‌ای نیاز دارد.',
    'signature-identity-mismatch': 'امضا به هویت احراز‌شده بازرس متصل نیست.',
    'signature-evidence-not-registered': 'فایل امضا در مخزن امن دستگاه برای همین بازدید ثبت نشده است.',
    'signature-evidence-not-found': 'فایل امضای PNG معتبر برای همین بازدید در مخزن امن دستگاه یافت نشد.',
    'severity-needs-review': 'شدت ناشناخته یا مهاجرت‌نشده باید پیش از ارسال تعیین تکلیف شود.',
    'critical-case-required-for-every-emergency': 'برای هر یافته اضطراری، پرونده ایمنی بحرانی کامل لازم است.',
    'duplicate-critical-case': 'برای هر رخداد اضطراری فقط یک پرونده یافته بحرانی مجاز است.',
    'critical-case-safety-fields-required': 'اطلاعات اقدام فوری، محدودیت بهره‌برداری، اعلان، مسئول و مهلت کامل نیست.',
    'invalid-critical-timeline': 'زمان اعلان و مهلت اقدام نمی‌تواند پیش از زمان کشف باشد.',
    'critical-notification-late-reason-required': 'برای اعلان با بیش از ۲۴ ساعت تأخیر، دلیل مستند لازم است.',
    'critical-overdue-reason-required': 'اگر مهلت پرونده گذشته است، دلیل تأخیر و وضعیت تشدید پیگیری باید ثبت شود.',
    'critical-owner-not-available': 'مسئول تعیین‌شده برای یافته بحرانی فعال یا معتبر نیست.',
    'critical-defect-link-required': 'یافته بحرانی باید به همان رخداد آسیب اضطراری متصل باشد.',
    'inspection-field-record-locked': 'رکورد میدانی پس از ارسال قفل است.',
    'inspection-not-awaiting-review': 'این بازدید در صف کنترل کیفیت نیست.',
    'signature-must-be-recaptured-after-return': 'پس از بازگشت گزارش برای اصلاح، امضای بازرس باید دوباره ثبت شود.',
    'review-comment-required': 'نظر کنترل کیفیت باید روشن و حداقل ۵ نویسه باشد.',
    'submitted-record-hash-mismatch': 'هش رکورد ارسالی معتبر نیست؛ تأیید متوقف شد.',
    'void-reason-required': 'برای ابطال رسمی، دلیل روشن حداقل ۱۰ نویسه‌ای لازم است.',
    'independent-void-approval-required': 'بازرس گزارش نمی‌تواند همان گزارش را باطل کند.',
    'correction-reason-required': 'دلیل ایجاد نسخه اصلاحی لازم است.',
    'risk-override-reason-required': 'دلیل فنی تغییر موعد مبتنی بر ریسک لازم است.',
    'late-reason-required': 'برای برنامه عقب‌افتاده، علت تأخیر باید ثبت شود.',
    'resolution-evidence-required': 'شرح اقدام و مرجع مدرک رفع خطر لازم است.',
    'resolution-required-before-close': 'ابتدا اقدام اصلاحی و مدرک رفع خطر باید ثبت شود.',
    'independent-closure-verification-required': 'بستن پرونده بحرانی به تأیید مستقل نیاز دارد.',
    'native-timeout': 'پاسخ لایه امن دستگاه دریافت نشد؛ داده ارسالی نهایی نشده است.',
    'native-required': 'این عملیات رسمی فقط در برنامه Android قابل انجام است.',
  });

  const state = {
    initialized: false,
    authenticated: false,
    bootstrapRequired: false,
    user: null,
    credentialUserIds: [],
    expiresAt: 0,
    authSelection: '',
    criticalResolver: null,
    criticalContext: null,
    reassignmentResolver: null,
  };

  function errorMessage(code) {
    return ERROR_MESSAGES[String(code || '')] || 'عملیات انجام نشد؛ داده قبلی بدون تغییر باقی ماند.';
  }

  function currentUser() {
    if (!state.authenticated || !state.user || (state.expiresAt && state.expiresAt <= Date.now())) return null;
    return state.user;
  }

  function qualificationStatus(user, visitType = '*', review = false, today = new Date()) {
    const q = user?.qualification;
    if (!q || q.status !== 'approved') return { ok: false, error: 'approved-qualification-required' };
    if (!q.approvedBy || q.approvedBy === user.id) return { ok: false, error: 'independent-qualification-approval-required' };
    const expiry = Date.parse(String(q.expiresAt || '') + 'T23:59:59Z');
    if (!Number.isFinite(expiry) || expiry < today.getTime()) return { ok: false, error: 'qualification-expired' };
    if (review && !q.canReview) return { ok: false, error: 'review-qualification-required' };
    if (!review && q.canInspect === false) return { ok: false, error: 'inspection-qualification-required' };
    const allowed = Array.isArray(q.allowedInspectionTypes) ? q.allowedInspectionTypes : [];
    if (visitType !== '*' && !allowed.includes('*') && !allowed.includes(visitType)) return { ok: false, error: 'inspection-type-not-authorized' };
    return { ok: true };
  }

  function isVoided(inspectionId) {
    return dbList('inspectionVoids').some(item => item.inspectionId === inspectionId && item.status === 'void');
  }

  function isOfficialInspection(record) {
    return Boolean(record && record.workflowStatus === 'approved' && record.officialReport !== false && !isVoided(record.id));
  }

  function workflowLabel(record) {
    const status = record?.workflowStatus || (record?.status === 'نهایی' ? 'approved' : 'draft');
    return ({
      draft: 'پیش‌نویس',
      submitted: 'در انتظار کنترل کیفیت',
      returned: 'بازگشت برای اصلاح',
      approved: isVoided(record?.id) ? 'باطل‌شده' : 'تأییدشده',
    })[status] || 'نامشخص';
  }

  function programStatus(program, today = new Date()) {
    const effective = program?.riskOverrideDueDate || program?.dueDate;
    const due = Date.parse(String(effective || '') + 'T23:59:59Z');
    if (!Number.isFinite(due)) return { id: 'unconfigured', label: 'برنامه‌ریزی‌نشده' };
    const days = Math.ceil((due - today.getTime()) / 86400000);
    if (due < today.getTime()) return { id: 'overdue', label: 'عقب‌افتاده', days };
    if (days <= 30) return { id: 'due-soon', label: 'نزدیک به موعد', days };
    return { id: 'scheduled', label: 'برنامه‌ریزی‌شده', days };
  }

  function criticalCompleteness(value) {
    const required = ['discoveredAt', 'immediateAction', 'operatingRestriction', 'restrictionRationale',
      'notifiedContact', 'notifiedAt', 'ownerId', 'dueAt'];
    const missing = required.filter(key => !String(value?.[key] || '').trim());
    if (value?.operatingRestriction && !['none', 'load', 'lane', 'closure', 'shoring', 'other'].includes(value.operatingRestriction)) missing.push('operatingRestriction');
    const discovery = Date.parse(value?.discoveredAt || '');
    const notified = Date.parse(value?.notifiedAt || '');
    const due = Date.parse(value?.dueAt || '');
    if (!Number.isFinite(discovery)) missing.push('discoveredAt');
    if (!Number.isFinite(notified)) missing.push('notifiedAt');
    if (!Number.isFinite(due)) missing.push('dueAt');
    if (Number.isFinite(discovery) && Number.isFinite(notified) && notified < discovery) missing.push('notifiedAt');
    if (Number.isFinite(discovery) && Number.isFinite(due) && due < discovery) missing.push('dueAt');
    if (Number.isFinite(discovery) && Number.isFinite(notified) && notified - discovery > 86400000 && String(value?.notificationLateReason || '').trim().length < 8) missing.push('notificationLateReason');
    if (Number.isFinite(due) && due < Date.now() && String(value?.overdueReason || '').trim().length < 8) missing.push('overdueReason');
    return { ok: missing.length === 0, missing: [...new Set(missing)] };
  }

  function applyAuthStatus(result) {
    state.authenticated = Boolean(result?.authenticated);
    state.bootstrapRequired = Boolean(result?.bootstrapRequired);
    state.user = result?.user || null;
    state.credentialUserIds = Array.isArray(result?.credentialUserIds) ? result.credentialUserIds : [];
    state.expiresAt = Number(result?.expiresAt || 0);
    renderSessionControl();
    window.renderDashboard?.();
    window.renderUsers?.();
  }

  async function refreshAuthStatus(showIfLocked = false) {
    const result = await Native.authStatus();
    if (result?.ok) applyAuthStatus(result);
    if ((showIfLocked || state.bootstrapRequired) && !state.authenticated) openAuthentication(true);
    return result;
  }

  function renderSessionControl() {
    const button = document.getElementById('sessionButton');
    if (!button) return;
    const user = currentUser();
    button.textContent = user ? 'قفل' : 'ورود';
    button.setAttribute('aria-label', user ? 'قفل کردن نشست ' + user.name : 'ورود امن کاربر');
    button.classList.toggle('session-unlocked', Boolean(user));
    const line = document.getElementById('activeUserLine');
    if (line) line.textContent = user ? 'کاربر احراز‌شده: ' + user.name + ' • ' + user.role : 'نشست قفل است';
  }

  function requireSession(message = '') {
    if (currentUser()) return true;
    if (message) toast(message);
    openAuthentication(true);
    return false;
  }

  function authUsers() {
    const enrolled = new Set(state.credentialUserIds);
    return dbList('users').filter(user => !user.archived && (state.bootstrapRequired ? user.role === ROLES.ADMIN : enrolled.has(user.id)));
  }

  function openAuthentication(force = false, selectedId = '') {
    const modal = document.getElementById('authModal');
    if (!modal) return;
    const users = authUsers();
    state.authSelection = selectedId || state.authSelection || users[0]?.id || '';
    document.getElementById('authTitle').textContent = state.bootstrapRequired ? 'راه‌اندازی ورود امن' : 'ورود امن';
    document.getElementById('authHelp').textContent = state.bootstrapRequired
      ? 'برای مدیر اولیه یک PIN حداقل ۶ رقمی یا گذرواژه حداقل ۸ نویسه‌ای بسازید. سپس حداقل دو هویت مستقل برای بازرسی و QC تعریف کنید.'
      : 'کاربر و PIN خود را وارد کنید. امکان paste و مدیر رمز عبور غیرفعال نشده است.';
    const select = document.getElementById('authUser');
    select.innerHTML = users.map(user => '<option value="' + esc(user.id) + '"' + (user.id === state.authSelection ? ' selected' : '') + '>' + esc(user.name + ' • ' + user.role) + '</option>').join('');
    const confirmField = document.getElementById('authPinConfirmField');
    confirmField.hidden = !state.bootstrapRequired;
    document.getElementById('authSubmit').textContent = state.bootstrapRequired ? 'ثبت PIN و ورود' : 'ورود';
    document.getElementById('authCancel').hidden = force || state.bootstrapRequired;
    document.getElementById('authError').textContent = '';
    document.getElementById('authPin').value = '';
    document.getElementById('authPinConfirm').value = '';
    modal.dataset.required = force || state.bootstrapRequired ? 'true' : 'false';
    modal.classList.add('open');
    setTimeout(() => document.getElementById('authPin')?.focus(), 80);
  }

  async function submitAuthentication() {
    const userId = document.getElementById('authUser')?.value || '';
    const pin = document.getElementById('authPin')?.value || '';
    const confirmPin = document.getElementById('authPinConfirm')?.value || '';
    const error = document.getElementById('authError');
    if (state.bootstrapRequired && pin !== confirmPin) {
      error.textContent = 'PIN و تکرار آن یکسان نیست.';
      return;
    }
    const button = document.getElementById('authSubmit');
    button.disabled = true;
    error.textContent = '';
    const result = state.bootstrapRequired ? await Native.enrollPin(userId, pin) : await Native.authenticate(userId, pin);
    button.disabled = false;
    document.getElementById('authPin').value = '';
    document.getElementById('authPinConfirm').value = '';
    if (!result?.ok) {
      error.textContent = errorMessage(result?.error);
      error.focus();
      return;
    }
    applyAuthStatus(result);
    document.getElementById('authModal').classList.remove('open');
    toast('ورود امن انجام شد.');
  }

  async function toggleSession() {
    if (!currentUser()) { openAuthentication(false); return; }
    const result = await Native.lockSession();
    if (result?.ok) applyAuthStatus(result);
    openAuthentication(true);
  }

  function qualificationBadge(user) {
    const q = qualificationStatus(user, '*', false);
    if (q.ok) return '<span class="pill green">صلاحیت معتبر</span>';
    return '<span class="pill warning">نیازمند تأیید صلاحیت</span>';
  }

  function renderUsers() {
    const host = document.getElementById('userList');
    if (!host) return;
    const actor = currentUser();
    const enrolled = new Set(state.credentialUserIds);
    const users = dbList('users').filter(user => !user.archived);
    host.innerHTML = users.map(user => '<div class="listitem user-row"><div class="listmain"><b>' + esc(user.name) + '</b><small>' +
      esc(user.role || 'کاربر') + ' • شناسه پرسنلی: ' + esc(user.personnelId || 'ثبت نشده') + '</small></div>' +
      qualificationBadge(user) + (enrolled.has(user.id) ? '<span class="pill blue">PIN فعال</span>' : '<span class="pill warning">بدون PIN</span>') +
      (actor?.id === user.id ? '<span class="pill green">نشست فعلی</span>' : '<button class="btn" type="button" onclick="setActiveUser(\'' + esc(user.id) + '\')">ورود</button>') +
      (actor?.role === ROLES.ADMIN ? '<button class="btn" type="button" onclick="editUser(\'' + esc(user.id) + '\')">ویرایش</button>' +
        (actor.id !== user.id ? '<button class="btn danger" type="button" onclick="deleteUser(\'' + esc(user.id) + '\')">غیرفعال‌سازی</button>' : '') : '') +
      '</div>').join('') || '<div class="empty">کاربری ثبت نشده است.</div>';
  }

  function visitTypeChecks(selected) {
    const values = new Set(Array.isArray(selected) ? selected : []);
    return VISIT_TYPES.map((type, index) => '<label class="check-option"><input id="qualType' + index + '" type="checkbox" value="' + esc(type) + '"' +
      (values.has('*') || values.has(type) ? ' checked' : '') + '> ' + esc(type) + '</label>').join('');
  }

  function editUser(userId = '') {
    const actor = currentUser();
    if (!actor || actor.role !== ROLES.ADMIN) { toast('مدیریت کاربران فقط برای مدیر احراز‌شده مجاز است.'); openAuthentication(); return; }
    const user = userId ? dbList('users').find(item => item.id === userId) : null;
    const q = user?.qualification || {};
    showGeneric(user ? 'ویرایش هویت و صلاحیت' : 'افزودن هویت حرفه‌ای',
      '<div class="form-grid"><div class="field"><label for="userEditorName">نام و نام خانوادگی *</label><input id="userEditorName" maxlength="120" value="' + esc(user?.name || '') + '"></div>' +
      '<div class="field"><label for="userPersonnelId">شناسه پرسنلی *</label><input id="userPersonnelId" maxlength="80" value="' + esc(user?.personnelId || '') + '"></div>' +
      '<div class="field"><label for="userEditorRole">نقش *</label><select id="userEditorRole">' +
      Object.values(ROLES).map(role => '<option' + (user?.role === role ? ' selected' : '') + '>' + esc(role) + '</option>').join('') + '</select></div>' +
      '<div class="field"><label for="qualDiscipline">رشته/حوزه تخصص *</label><input id="qualDiscipline" value="' + esc(q.discipline || '') + '"></div>' +
      '<div class="field"><label for="qualDegree">مدرک تحصیلی *</label><input id="qualDegree" value="' + esc(q.degree || '') + '"></div>' +
      '<div class="field"><label for="qualExperience">سابقه مرتبط (سال) *</label><input id="qualExperience" type="number" min="0" max="80" inputmode="numeric" value="' + esc(q.experienceYears ?? '') + '"></div>' +
      '<div class="field"><label for="qualCourse">دوره BIRM/MBEI/سازمانی *</label><input id="qualCourse" value="' + esc(q.trainingCourse || '') + '"></div>' +
      '<div class="field"><label for="qualCertificate">شماره گواهی *</label><input id="qualCertificate" value="' + esc(q.certificateNo || '') + '"></div>' +
      '<div class="field"><label for="qualExpiry">تاریخ انقضا *</label><input id="qualExpiry" type="date" value="' + esc(q.expiresAt || '') + '"></div></div>' +
      '<fieldset class="governance-fieldset"><legend>دامنه مجاز انواع بازرسی</legend>' + visitTypeChecks(q.allowedInspectionTypes) + '</fieldset>' +
      '<div class="form-grid"><label class="check-option"><input id="qualCanInspect" type="checkbox"' + (q.canInspect !== false ? ' checked' : '') + '> مجاز به بازرسی</label>' +
      '<label class="check-option"><input id="qualCanReview" type="checkbox"' + (q.canReview ? ' checked' : '') + '> مجاز به QC مستقل</label>' +
      '<label class="check-option"><input id="qualApprove" type="checkbox"' + (q.status === 'approved' ? ' checked' : '') + '> تأیید صلاحیت توسط مدیر فعلی</label></div>' +
      '<div class="field"><label for="userPin">PIN جدید/بازنشانی</label><input id="userPin" type="password" inputmode="numeric" autocomplete="new-password" minlength="6"><small>برای کاربر جدید الزامی؛ plaintext ذخیره نمی‌شود.</small></div>' +
      '<div id="userEditorError" class="inline-error" role="alert" tabindex="-1"></div>',
      [{ t: 'ذخیره امن', c: 'primary', fn: 'saveUserEditor(\'' + esc(userId) + '\')' }]);
  }

  async function saveUserEditor(userId = '') {
    const get = value => document.getElementById(value);
    const name = get('userEditorName')?.value.trim() || '';
    const personnelId = get('userPersonnelId')?.value.trim() || '';
    const error = get('userEditorError');
    const selectedTypes = VISIT_TYPES.filter((type, index) => get('qualType' + index)?.checked);
    const pin = get('userPin')?.value || '';
    const targetId = userId || id('user');
    if (name.length < 2 || !personnelId || !selectedTypes.length) {
      error.textContent = 'نام، شناسه پرسنلی و حداقل یک نوع بازرسی الزامی است.';
      error.focus();
      return;
    }
    if (!userId && !pin) {
      error.textContent = 'برای کاربر جدید PIN لازم است.';
      error.focus();
      return;
    }
    const user = {
      id: targetId,
      name,
      personnelId,
      role: get('userEditorRole')?.value || ROLES.INSPECTOR,
      qualification: {
        status: get('qualApprove')?.checked ? 'approved' : 'needs_review',
        discipline: get('qualDiscipline')?.value.trim() || '',
        degree: get('qualDegree')?.value.trim() || '',
        experienceYears: Number(get('qualExperience')?.value),
        trainingCourse: get('qualCourse')?.value.trim() || '',
        certificateNo: get('qualCertificate')?.value.trim() || '',
        expiresAt: get('qualExpiry')?.value || '',
        allowedInspectionTypes: selectedTypes,
        canInspect: Boolean(get('qualCanInspect')?.checked),
        canReview: Boolean(get('qualCanReview')?.checked),
      },
    };
    const result = await Native.saveGovernedUser(user);
    if (!result?.ok) {
      error.textContent = errorMessage(result?.error);
      error.focus();
      return;
    }
    if (pin) {
      const enrolled = await Native.enrollPin(targetId, pin);
      get('userPin').value = '';
      if (!enrolled?.ok) {
        error.textContent = 'هویت ذخیره شد، اما PIN ثبت نشد: ' + errorMessage(enrolled?.error);
        error.focus();
        await refreshAuthStatus();
        return;
      }
    }
    closeModal('genericModal');
    await refreshAuthStatus();
    renderUsers();
    toast('هویت و صلاحیت ذخیره شد.');
  }

  function setActiveUser(userId) {
    openAuthentication(false, userId);
    setTimeout(() => {
      const select = document.getElementById('authUser');
      if (select && [...select.options].some(option => option.value === userId)) select.value = userId;
    }, 0);
  }

  function deleteUser(userId) {
    const user = dbList('users').find(item => item.id === userId);
    if (!user) return;
    showGeneric('غیرفعال‌سازی هویت',
      '<div class="notice urgent">هویت حذف فیزیکی نمی‌شود تا سوابق امضا و audit قابل استناد بماند.</div>' +
      '<div class="field"><label for="deactivateReason">دلیل غیرفعال‌سازی *</label><textarea id="deactivateReason" rows="3"></textarea></div>' +
      '<div id="deactivateError" class="inline-error" role="alert" tabindex="-1"></div>',
      [{ t: 'غیرفعال‌سازی', c: 'danger', fn: 'confirmDeactivateUser(\'' + esc(userId) + '\')' }]);
  }

  async function confirmDeactivateUser(userId) {
    const reason = document.getElementById('deactivateReason')?.value.trim() || '';
    const result = await Native.deactivateGovernedUser(userId, reason);
    if (!result?.ok) {
      const error = document.getElementById('deactivateError');
      error.textContent = errorMessage(result?.error);
      error.focus();
      return;
    }
    closeModal('genericModal');
    renderUsers();
    toast('هویت بدون حذف سوابق غیرفعال شد.');
  }

  function criticalOwnerOptions(selected = '') {
    return dbList('users').filter(user => !user.archived).map(user =>
      '<option value="' + esc(user.id) + '"' + (user.id === selected ? ' selected' : '') + '>' + esc(user.name + ' • ' + user.role) + '</option>').join('');
  }

  function toLocalInput(isoValue, offsetHours = 0) {
    const date = isoValue ? new Date(isoValue) : new Date(Date.now() + offsetHours * 3600000);
    if (!Number.isFinite(date.getTime())) return '';
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
  }

  function openCriticalResponse(item, prior = {}) {
    return new Promise(resolve => {
      state.criticalResolver = resolve;
      state.criticalContext = { item, prior };
      showGeneric('اقدام ایمنی برای یافته اضطراری ' + (item.code || ''),
        '<div class="notice urgent">این اطلاعات یک پرونده Critical Finding مستقل می‌سازد. ثبت «اضطراری» بدون اقدام واقعی، تصمیم بهره‌برداری و اعلان قابل قبول نیست.</div>' +
        '<div class="form-grid"><div class="field"><label for="criticalDiscovery">زمان کشف *</label><input id="criticalDiscovery" type="datetime-local" value="' + esc(toLocalInput(prior.discoveredAt)) + '"></div>' +
        '<div class="field"><label for="criticalDue">مهلت اقدام *</label><input id="criticalDue" type="datetime-local" value="' + esc(toLocalInput(prior.dueAt, 24)) + '"></div>' +
        '<div class="field"><label for="criticalRestriction">تصمیم بهره‌برداری *</label><select id="criticalRestriction">' +
        [['none','بدون محدودیت با توجیه'],['load','محدودیت بار'],['lane','محدودیت خط/مسیر'],['closure','انسداد'],['shoring','شمع‌بندی/مهاربندی موقت'],['other','سایر']].map(pair =>
          '<option value="' + pair[0] + '"' + (prior.operatingRestriction === pair[0] ? ' selected' : '') + '>' + pair[1] + '</option>').join('') + '</select></div>' +
        '<div class="field"><label for="criticalOwner">مسئول پیگیری *</label><select id="criticalOwner">' + criticalOwnerOptions(prior.ownerId) + '</select></div>' +
        '<div class="field"><label for="criticalNotifiedAt">زمان اعلان *</label><input id="criticalNotifiedAt" type="datetime-local" value="' + esc(toLocalInput(prior.notifiedAt)) + '"></div>' +
        '<div class="field"><label for="criticalContact">مقام/واحد مطلع‌شده *</label><input id="criticalContact" maxlength="180" value="' + esc(prior.notifiedContact || '') + '"></div></div>' +
        '<div class="field"><label for="criticalImmediate">اقدام فوری انجام‌شده *</label><textarea id="criticalImmediate" rows="3">' + esc(prior.immediateAction || '') + '</textarea></div>' +
        '<div class="field"><label for="criticalRationale">توجیه تصمیم محدودیت/انسداد *</label><textarea id="criticalRationale" rows="3">' + esc(prior.restrictionRationale || '') + '</textarea></div>' +
        '<div class="field"><label for="criticalLateReason">علت اعلان دیرتر از ۲۴ ساعت، در صورت وقوع</label><textarea id="criticalLateReason" rows="2">' + esc(prior.notificationLateReason || '') + '</textarea></div>' +
        '<div class="field"><label for="criticalOverdueReason">علت تأخیر و تشدید پیگیری، اگر مهلت گذشته است</label><textarea id="criticalOverdueReason" rows="2">' + esc(prior.overdueReason || '') + '</textarea></div>' +
        '<div id="criticalResponseError" class="inline-error" role="alert" tabindex="-1"></div>',
        [{ t: 'ثبت اقدام ایمنی', c: 'primary', fn: 'confirmCriticalSafetyResponse()' },
          { t: 'انصراف از ارسال', fn: 'cancelCriticalSafetyResponse()' }]);
    });
  }

  function readIsoInput(idValue) {
    const value = document.getElementById(idValue)?.value || '';
    const date = new Date(value);
    return Number.isFinite(date.getTime()) ? date.toISOString() : '';
  }

  function confirmCriticalSafetyResponse() {
    const value = {
      discoveredAt: readIsoInput('criticalDiscovery'),
      immediateAction: document.getElementById('criticalImmediate')?.value.trim() || '',
      operatingRestriction: document.getElementById('criticalRestriction')?.value || '',
      restrictionRationale: document.getElementById('criticalRationale')?.value.trim() || '',
      notifiedContact: document.getElementById('criticalContact')?.value.trim() || '',
      notifiedAt: readIsoInput('criticalNotifiedAt'),
      notificationLateReason: document.getElementById('criticalLateReason')?.value.trim() || '',
      overdueReason: document.getElementById('criticalOverdueReason')?.value.trim() || '',
      ownerId: document.getElementById('criticalOwner')?.value || '',
      dueAt: readIsoInput('criticalDue'),
    };
    const validation = criticalCompleteness(value);
    if (!validation.ok) {
      const error = document.getElementById('criticalResponseError');
      error.textContent = 'فیلدهای الزامی کامل نیست: ' + validation.missing.join('، ');
      error.focus();
      return;
    }
    closeModal('genericModal');
    const resolve = state.criticalResolver;
    state.criticalResolver = null;
    state.criticalContext = null;
    resolve?.(value);
  }

  function cancelCriticalSafetyResponse() {
    closeModal('genericModal');
    const resolve = state.criticalResolver;
    state.criticalResolver = null;
    state.criticalContext = null;
    resolve?.(null);
  }

  function collectInspectorReassignment(record, nextUser) {
    return new Promise(resolve => {
      state.reassignmentResolver = resolve;
      showGeneric('ثبت تغییر مسئول بازدید',
        '<div class="notice urgent">این رکورد قبلاً به ' + esc(record.inspector || record.inspectorId || 'بازرس دیگر') +
        ' متصل بوده است. تغییر هویت بازرس باید پیش از ارسال، مستند و در audit ثبت شود.</div>' +
        '<div class="field"><label for="inspectorReassignmentReason">دلیل تغییر به ' + esc(nextUser?.name || '') + ' *</label>' +
        '<textarea id="inspectorReassignmentReason" rows="4" minlength="10"></textarea></div>' +
        '<div id="inspectorReassignmentError" class="inline-error" role="alert" tabindex="-1"></div>',
        [{ t: 'ثبت دلیل و ادامه', c: 'primary', fn: 'confirmInspectorReassignment()' },
          { t: 'انصراف از ارسال', fn: 'cancelInspectorReassignment()' }]);
    });
  }

  function confirmInspectorReassignment() {
    const reason = document.getElementById('inspectorReassignmentReason')?.value.trim() || '';
    if (reason.length < 10) {
      const error = document.getElementById('inspectorReassignmentError');
      error.textContent = errorMessage('inspector-reassignment-reason-required');
      error.focus();
      return;
    }
    closeModal('genericModal');
    const resolve = state.reassignmentResolver;
    state.reassignmentResolver = null;
    resolve?.(reason);
  }

  function cancelInspectorReassignment() {
    closeModal('genericModal');
    const resolve = state.reassignmentResolver;
    state.reassignmentResolver = null;
    resolve?.(null);
  }

  async function collectCriticalCases(items, inspection) {
    const emergency = (items || []).filter(item => item.applicable !== false && item.statusId === 'emergency');
    const responses = { ...(inspection.criticalResponses || {}) };
    const cases = [];
    for (const item of emergency) {
      const occurrenceIndex = Number(item.occurrenceIndex) || 1;
      const key = item.itemId + '::' + occurrenceIndex;
      let response = responses[key];
      if (!criticalCompleteness(response).ok) response = await openCriticalResponse(item, response || {});
      if (!response) return null;
      responses[key] = response;
      cases.push({
        ...response,
        itemId: item.itemId,
        itemCode: item.itemCode,
        occurrenceIndex,
        title: item.code + ' — ' + (item.itemName || item.itemId),
      });
    }
    inspection.criticalResponses = responses;
    return cases;
  }

  function renderReviews() {
    const host = document.getElementById('reviewList');
    if (!host) return;
    const records = dbList('inspections').filter(item => item.workflowStatus === 'submitted');
    document.getElementById('reviewCount').textContent = faNum(records.length) + ' گزارش';
    host.innerHTML = records.map(record => '<div class="listitem"><div class="listmain"><b>' +
      esc(record.draftReference || record.id) + ' — ' + esc(record.bridgeName || 'پل نامشخص') +
      '</b><small>' + esc(record.jdate || '') + ' • بازرس: ' + esc(record.inspector || '') +
      ' • چرخه ' + faNum(record.submissionCycle || 1) + '</small></div><span class="pill warning">در انتظار QC</span>' +
      '<button class="btn primary" type="button" onclick="openReview(\'' + esc(record.id) + '\')">بازبینی</button></div>').join('') ||
      '<div class="empty">گزارشی در صف کنترل کیفیت نیست.</div>';
  }

  function openReview(inspectionId) {
    if (!requireSession('برای کنترل کیفیت وارد شوید.')) return;
    const record = dbList('inspections').find(item => item.id === inspectionId);
    if (!record) return;
    const same = currentUser()?.id === record.inspectorId;
    const critical = dbList('criticalFindings').filter(item => item.inspectionId === inspectionId);
    showGeneric('کنترل کیفیت مستقل',
      '<div class="workflow-banner"><b>' + esc(record.bridgeName || '') + '</b><span>' + esc(record.draftReference || record.id) + '</span></div>' +
      '<dl class="review-facts"><div><dt>بازرس</dt><dd>' + esc(record.inspector || '') + '</dd></div><div><dt>هش رکورد</dt><dd class="hash-value">' + esc(record.fieldHash || '') + '</dd></div>' +
      '<div><dt>یافته‌های اضطراری</dt><dd>' + faNum(critical.length) + '</dd></div><div><dt>پوشش</dt><dd>' + esc(record.overallLabel || '') + '</dd></div></dl>' +
      (same ? '<div class="callout urgent" role="alert">استقلال بازبین نقض می‌شود؛ شما بازرس همین گزارش هستید و تأیید ممکن نیست.</div>' : '') +
      '<div class="field"><label for="reviewComment">نظر مستند QC *</label><textarea id="reviewComment" rows="4" minlength="5"></textarea><small>کامل‌بودن داده، عکس، شدت، سازگاری امتیاز و پرونده‌های بحرانی را کنترل کنید.</small></div>' +
      '<div id="reviewError" class="inline-error" role="alert" tabindex="-1"></div>',
      same ? [{ t: 'بستن', fn: "closeModal('genericModal')" }] :
        [{ t: 'تأیید و صدور شماره رسمی', c: 'primary', fn: 'submitReview(\'' + esc(inspectionId) + '\',\'approve\')' },
          { t: 'بازگشت برای اصلاح', c: 'danger', fn: 'submitReview(\'' + esc(inspectionId) + '\',\'return\')' }]);
  }

  async function submitReview(inspectionId, decision) {
    const comment = document.getElementById('reviewComment')?.value.trim() || '';
    const result = await Native.reviewInspection({ inspectionId, decision, comment });
    if (!result?.ok) {
      const error = document.getElementById('reviewError');
      error.textContent = errorMessage(result?.error);
      error.focus();
      return;
    }
    closeModal('genericModal');
    renderReviews();
    window.renderInspections?.();
    window.renderDrafts?.();
    window.renderDashboard?.();
    toast(decision === 'approve' ? 'گزارش با شماره یکتای ' + result.reportNo + ' تأیید شد.' : 'گزارش با حفظ snapshot ارسالی برای اصلاح بازگردانده شد.');
  }

  function openRecordGovernance(inspectionId) {
    const record = dbList('inspections').find(item => item.id === inspectionId);
    if (!record) return;
    if (record.workflowStatus === 'submitted') { openReview(inspectionId); return; }
    const voided = isVoided(inspectionId);
    showGeneric('حاکمیت رکورد رسمی',
      '<div class="workflow-banner"><b>' + esc(record.reportNo || record.no || record.id) + '</b><span>' + esc(workflowLabel(record)) + '</span></div>' +
      '<div class="notice">اصل گزارش حذف یا بازنویسی نمی‌شود. اصلاح فقط با نسخه جانشین و ابطال فقط با دلیل و تأیید مستقل ثبت می‌شود.</div>' +
      (voided ? '<div class="callout urgent">این گزارش باطل شده است و فقط برای ردیابی تاریخی نگهداری می‌شود.</div>' : '') +
      '<div class="field"><label for="governanceReason">دلیل مهندسی/اداری *</label><textarea id="governanceReason" rows="3"></textarea></div>' +
      '<div id="recordGovernanceError" class="inline-error" role="alert" tabindex="-1"></div>',
      voided ? [{ t: 'ایجاد نسخه اصلاحی', c: 'primary', fn: 'createCorrectionDraft(\'' + esc(inspectionId) + '\')' }] :
        [{ t: 'ایجاد نسخه اصلاحی', c: 'primary', fn: 'createCorrectionDraft(\'' + esc(inspectionId) + '\')' },
          { t: 'ابطال رسمی بدون حذف', c: 'danger', fn: 'voidInspectionRecord(\'' + esc(inspectionId) + '\')' }]);
  }

  async function voidInspectionRecord(inspectionId) {
    const reason = document.getElementById('governanceReason')?.value.trim() || '';
    const result = await Native.voidInspection({ inspectionId, reason });
    if (!result?.ok) { showRecordError(result); return; }
    closeModal('genericModal');
    window.renderInspections?.();
    window.renderDashboard?.();
    toast('ابطال ثبت شد؛ اصل گزارش و شواهد آن حفظ شدند.');
  }

  async function createCorrectionDraft(inspectionId) {
    const reason = document.getElementById('governanceReason')?.value.trim() || '';
    const result = await Native.createCorrectionDraft({ inspectionId, reason });
    if (!result?.ok) { showRecordError(result); return; }
    closeModal('genericModal');
    window.renderDrafts?.();
    editInspection(result.draftId);
    toast('نسخه اصلاحی با پیوند به گزارش اصلی ساخته شد.');
  }

  function showRecordError(result) {
    const error = document.getElementById('recordGovernanceError');
    error.textContent = errorMessage(result?.error);
    error.focus();
  }

  function renderCriticalFindings() {
    const host = document.getElementById('criticalList');
    if (!host) return;
    const now = Date.now();
    const records = dbList('criticalFindings').slice().sort((a, b) => Date.parse(a.dueAt || 0) - Date.parse(b.dueAt || 0));
    host.innerHTML = records.map(record => {
      const overdue = record.status !== 'closed' && Date.parse(record.dueAt || '') < now;
      return '<div class="listitem critical-case ' + (overdue ? 'is-overdue' : '') + '"><div class="listmain"><b>' + esc(record.title || record.id) +
        '</b><small>وضعیت: ' + esc(record.status || 'open') + ' • مسئول: ' + esc(userName(record.ownerId)) +
        ' • مهلت: ' + esc(formatDateTime(record.dueAt)) + '</small></div>' +
        '<span class="pill ' + (overdue ? 'danger' : record.status === 'closed' ? 'green' : 'warning') + '">' +
        (overdue ? 'عقب‌افتاده' : record.status === 'closed' ? 'بسته' : 'فعال') + '</span>' +
        '<button class="btn primary" type="button" onclick="openCriticalFinding(\'' + esc(record.id) + '\')">پیگیری</button></div>';
    }).join('') || '<div class="empty">پرونده بحرانی ثبت نشده است.</div>';
  }

  function userName(userId) {
    return dbList('users').find(user => user.id === userId)?.name || userId || '—';
  }

  function formatDateTime(value) {
    const date = new Date(value);
    return Number.isFinite(date.getTime()) ? date.toLocaleString('fa-IR') : '—';
  }

  function openCriticalFinding(caseId) {
    if (!requireSession('برای پیگیری پرونده بحرانی وارد شوید.')) return;
    const record = dbList('criticalFindings').find(item => item.id === caseId);
    if (!record) return;
    showGeneric('پرونده یافته بحرانی',
      '<div class="workflow-banner urgent"><b>' + esc(record.title || record.id) + '</b><span>' + esc(record.status || '') + '</span></div>' +
      '<dl class="review-facts"><div><dt>اقدام فوری</dt><dd>' + esc(record.immediateAction || '') + '</dd></div><div><dt>تصمیم بهره‌برداری</dt><dd>' + esc(record.operatingRestriction || '') + ' • ' + esc(record.restrictionRationale || '') + '</dd></div>' +
      '<div><dt>اعلان</dt><dd>' + esc(record.notifiedContact || '') + ' • ' + esc(formatDateTime(record.notifiedAt)) + '</dd></div><div><dt>مسئول/مهلت</dt><dd>' + esc(userName(record.ownerId)) + ' • ' + esc(formatDateTime(record.dueAt)) + '</dd></div></dl>' +
      '<div class="field"><label for="criticalUpdateSummary">شرح اقدام/رفع خطر</label><textarea id="criticalUpdateSummary" rows="3">' + esc(record.resolutionSummary || record.interimAction || '') + '</textarea></div>' +
      '<div class="field"><label for="criticalEvidence">مرجع مدرک، عکس یا دستورکار</label><input id="criticalEvidence" value="' + esc(record.resolutionEvidence || '') + '"></div>' +
      '<div id="criticalUpdateError" class="inline-error" role="alert" tabindex="-1"></div>',
      criticalActions(record, caseId));
  }

  function criticalActions(record, caseId) {
    if (record.status === 'closed') return [{ t: 'بستن', fn: "closeModal('genericModal')" }];
    const actions = [];
    if (record.acknowledgmentStatus !== 'acknowledged') actions.push({ t: 'اعلام رؤیت/پذیرش مسئولیت', c: 'primary', fn: 'updateCriticalFinding(\'' + esc(caseId) + '\',\'acknowledge\')' });
    actions.push({ t: 'ثبت اقدام موقت', fn: 'updateCriticalFinding(\'' + esc(caseId) + '\',\'interim\')' });
    if (record.status === 'resolution-pending-verification') actions.push({ t: 'تأیید مستقل و بستن', c: 'primary', fn: 'updateCriticalFinding(\'' + esc(caseId) + '\',\'close\')' });
    else actions.push({ t: 'ارسال رفع خطر برای تأیید', c: 'primary', fn: 'updateCriticalFinding(\'' + esc(caseId) + '\',\'resolve\')' });
    return actions;
  }

  async function updateCriticalFinding(caseId, action) {
    const summary = document.getElementById('criticalUpdateSummary')?.value.trim() || '';
    const evidenceReference = document.getElementById('criticalEvidence')?.value.trim() || '';
    const result = await Native.updateCriticalFinding({ caseId, action, summary, evidenceReference });
    if (!result?.ok) {
      const error = document.getElementById('criticalUpdateError');
      error.textContent = errorMessage(result?.error);
      error.focus();
      return;
    }
    closeModal('genericModal');
    renderCriticalFindings();
    window.renderDashboard?.();
    toast('وضعیت پرونده بحرانی ثبت و audit شد.');
  }

  function renderPrograms() {
    const host = document.getElementById('programList');
    if (!host) return;
    const programs = new Map(dbList('inspectionPrograms').map(item => [item.bridgeId, item]));
    const bridges = dbList('bridges').filter(item => !item.archived);
    host.innerHTML = bridges.map(bridge => {
      const program = programs.get(bridge.id);
      const status = programStatus(program);
      return '<div class="listitem"><div class="listmain"><b>' + esc(bridgeName(bridge)) + '</b><small>آخرین تأیید: ' +
        esc(program?.lastInspectionDate || '—') + ' • موعد مؤثر: ' + esc(program?.effectiveDueDate || program?.dueDate || 'ثبت نشده') +
        '</small></div><span class="pill ' + (status.id === 'overdue' ? 'danger' : status.id === 'due-soon' ? 'warning' : 'green') + '">' +
        esc(status.label) + '</span><button class="btn primary" type="button" onclick="editInspectionProgram(\'' + esc(bridge.id) + '\')">تنظیم برنامه</button></div>';
    }).join('') || '<div class="empty">ابتدا شناسنامه یک پل را ثبت کنید.</div>';
  }

  function editInspectionProgram(bridgeId) {
    if (!requireSession('برای مدیریت برنامه بازدید وارد شوید.')) return;
    const program = dbList('inspectionPrograms').find(item => item.bridgeId === bridgeId) || {};
    const bridge = dbList('bridges').find(item => item.id === bridgeId);
    showGeneric('برنامه بازرسی ' + (bridge ? bridgeName(bridge) : ''),
      '<div class="form-grid"><div class="field"><label for="programDue">موعد پایه *</label><input id="programDue" type="date" value="' + esc(program.dueDate || '') + '"></div>' +
      '<div class="field"><label for="programInterval">فاصله پایه (ماه) *</label><input id="programInterval" type="number" min="1" max="120" inputmode="numeric" value="' + esc(program.intervalMonths || 12) + '"></div>' +
      '<div class="field"><label for="programBasis">مبنای فاصله *</label><select id="programBasis">' +
      [['owner-policy','دستورالعمل مالک'],['risk-based','مبتنی بر ریسک'],['regulatory','الزام مصوب'],['engineering-review','بازبینی مهندسی']].map(pair =>
        '<option value="' + pair[0] + '"' + (program.intervalBasis === pair[0] ? ' selected' : '') + '>' + pair[1] + '</option>').join('') + '</select></div>' +
      '<div class="field"><label for="programOverrideDue">موعد زودتر مبتنی بر ریسک</label><input id="programOverrideDue" type="date" value="' + esc(program.riskOverrideDueDate || '') + '"></div></div>' +
      '<div class="field"><label for="programOverrideReason">دلیل تغییر مبتنی بر ریسک</label><textarea id="programOverrideReason" rows="2">' + esc(program.riskOverrideReason || '') + '</textarea></div>' +
      '<div class="field"><label for="programLateReason">علت تأخیر، اگر موعد گذشته است</label><textarea id="programLateReason" rows="2">' + esc(program.lateReason || '') + '</textarea></div>' +
      '<div id="programError" class="inline-error" role="alert" tabindex="-1"></div>',
      [{ t: 'ذخیره برنامه', c: 'primary', fn: 'saveInspectionProgram(\'' + esc(bridgeId) + '\')' }]);
  }

  async function saveInspectionProgram(bridgeId) {
    const program = {
      bridgeId,
      dueDate: document.getElementById('programDue')?.value || '',
      intervalMonths: Number(document.getElementById('programInterval')?.value),
      intervalBasis: document.getElementById('programBasis')?.value || '',
      riskOverrideDueDate: document.getElementById('programOverrideDue')?.value || '',
      riskOverrideReason: document.getElementById('programOverrideReason')?.value.trim() || '',
      lateReason: document.getElementById('programLateReason')?.value.trim() || '',
    };
    const effective = program.riskOverrideDueDate || program.dueDate;
    if (Date.parse(effective + 'T23:59:59Z') < Date.now() && program.lateReason.length < 8) {
      const error = document.getElementById('programError');
      error.textContent = errorMessage('late-reason-required');
      error.focus();
      return;
    }
    const result = await Native.saveInspectionProgram(program);
    if (!result?.ok) {
      const error = document.getElementById('programError');
      error.textContent = errorMessage(result?.error);
      error.focus();
      return;
    }
    closeModal('genericModal');
    renderPrograms();
    const saved = dbList('inspectionPrograms').find(item => item.bridgeId === bridgeId);
    const timestamp = Date.parse((saved?.effectiveDueDate || '') + 'T08:00:00Z');
    if (Number.isFinite(timestamp) && timestamp > Date.now()) Native.scheduleReminder(saved.id, timestamp, 'موعد بازرسی پل', 'موعد برنامه بازرسی پل فرا رسیده است.');
    toast('برنامه بازرسی و وضعیت موعد ذخیره شد.');
  }

  function scheduleCriticalReminders() {
    dbList('criticalFindings').filter(item => item.status !== 'closed').forEach(item => {
      const when = Date.parse(item.dueAt || '');
      if (Number.isFinite(when) && when > Date.now()) Native.scheduleReminder(item.id, when, 'مهلت پرونده یافته بحرانی', item.title || 'اقدام ایمنی پل');
    });
  }

  function scheduleProgramReminders() {
    dbList('inspectionPrograms').forEach(item => {
      const when = Date.parse((item.effectiveDueDate || item.dueDate || '') + 'T08:00:00Z');
      if (Number.isFinite(when) && when > Date.now()) Native.scheduleReminder(item.id, when,
        'موعد بازرسی پل', 'موعد برنامه بازرسی پل فرا رسیده است.');
    });
  }

  async function fallbackPbkdf2(pin, saltBytes) {
    const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(pin), 'PBKDF2', false, ['deriveBits']);
    const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', salt: saltBytes, iterations: 120000, hash: 'SHA-256' }, key, 256);
    return [...new Uint8Array(bits)].map(byte => byte.toString(16).padStart(2, '0')).join('');
  }

  function fallbackCredentials() {
    try { return JSON.parse(localStorage.getItem('bridge_dev_credentials') || '{}'); } catch (_) { return {}; }
  }

  function saveFallbackCredentials(value) {
    localStorage.setItem('bridge_dev_credentials', JSON.stringify(value));
  }

  function randomHex(bytes = 16) {
    const values = crypto.getRandomValues(new Uint8Array(bytes));
    return [...values].map(value => value.toString(16).padStart(2, '0')).join('');
  }

  function fallbackAuthStatus() {
    const credentials = fallbackCredentials();
    const user = state.user && dbList('users').find(item => item.id === state.user.id && !item.archived);
    return { ok: true, authenticated: Boolean(user), user: user || null, bootstrapRequired: !Object.keys(credentials).length,
      credentialUserIds: Object.keys(credentials), expiresAt: user ? Date.now() + 3600000 : 0 };
  }

  function fallbackAudit(actor, action, kind, entityId, reason = '') {
    return { id: id('aud'), actorId: actor?.id || '', actorName: actor?.name || 'سیستم', user: actor?.name || 'سیستم',
      action, kind, eid: entityId, reason, detail: reason, at: Date.now(), appendOnly: true, schemaVersion: 1,
      previousHash: dbList('audit')[0]?.eventHash || '', eventHash: randomHex(32) };
  }

  function fallbackPatch(kind, value, event) {
    return { ok: true, entities: { [kind]: [value], audit: event ? [event] : [] } };
  }

  function installFallbackHandlers() {
    window.BridgeFallbackHandlers = {
      async authStatus() { return fallbackAuthStatus(); },
      async enrollPin({ userId, pin }) {
        const values = fallbackCredentials();
        if (Object.keys(values).length && currentUser()?.role !== ROLES.ADMIN) return { ok: false, error: 'role-not-authorized' };
        if (!/^[0-9]{6,12}$/.test(pin) && String(pin).length < 8) return { ok: false, error: 'weak-pin' };
        const salt = randomHex(16);
        const bytes = new Uint8Array(salt.match(/../g).map(value => parseInt(value, 16)));
        values[userId] = { salt, verifier: await fallbackPbkdf2(pin, bytes) };
        saveFallbackCredentials(values);
        if (!state.user) state.user = dbList('users').find(user => user.id === userId) || null;
        return fallbackAuthStatus();
      },
      async authenticate({ userId, pin }) {
        const record = fallbackCredentials()[userId];
        if (!record) return { ok: false, error: 'invalid-credentials' };
        const bytes = new Uint8Array(record.salt.match(/../g).map(value => parseInt(value, 16)));
        if (await fallbackPbkdf2(pin, bytes) !== record.verifier) return { ok: false, error: 'invalid-credentials' };
        state.user = dbList('users').find(user => user.id === userId && !user.archived) || null;
        return fallbackAuthStatus();
      },
      async lockSession() { state.user = null; return fallbackAuthStatus(); },
      async saveGovernedUser({ user }) {
        const actor = currentUser();
        if (actor?.role !== ROLES.ADMIN) return { ok: false, error: 'role-not-authorized' };
        const next = { ...dbList('users').find(item => item.id === user.id), ...user, archived: false, updatedAt: Date.now() };
        if (next.qualification?.status === 'approved') {
          if (actor.id === next.id) next.qualification = { ...next.qualification, status: 'needs_review', approvedBy: '' };
          else next.qualification = { ...next.qualification, approvedBy: actor.id, approvedAt: Date.now() };
        }
        return fallbackPatch('users', next, fallbackAudit(actor, 'user-updated', 'users', next.id, next.role));
      },
      async deactivateGovernedUser({ userId, reason }) {
        const actor = currentUser();
        if (actor?.role !== ROLES.ADMIN) return { ok: false, error: 'role-not-authorized' };
        const user = dbList('users').find(item => item.id === userId);
        if (!user || reason.length < 8) return { ok: false, error: 'deactivation-reason-required' };
        const next = { ...user, archived: true, deactivatedAt: Date.now(), deactivationReason: reason };
        return fallbackPatch('users', next, fallbackAudit(actor, 'user-deactivated', 'users', userId, reason));
      },
      async submitInspection({ inspection, defects, criticalFindings }) {
        const actor = currentUser();
        const q = qualificationStatus(actor, inspection.visitType, false);
        if (!q.ok) return q;
        const now = Date.now();
        const submitted = { ...inspection, workflowStatus: 'submitted', status: 'ارسال‌شده', submittedAt: now,
          fieldLocked: true, officialReport: false, submissionCycle: (inspection.submissionCycle || 0) + 1,
          fieldHash: randomHex(32), updatedAt: now };
        const revision = { id: 'submission-' + submitted.id + '-' + submitted.submissionCycle, inspectionId: submitted.id,
          revision: submitted.submissionCycle, type: 'field-submission', inspectionHash: submitted.fieldHash, submittedAt: now, updatedAt: now };
        return { ok: true, entities: { inspections: [submitted], defects, criticalFindings,
          reportRevisions: [revision], audit: [fallbackAudit(actor, 'inspection-submitted', 'inspections', submitted.id)] } };
      },
      async reviewInspection({ inspectionId, decision, comment }) {
        const actor = currentUser();
        const inspection = dbList('inspections').find(item => item.id === inspectionId);
        if (!inspection || inspection.workflowStatus !== 'submitted') return { ok: false, error: 'inspection-not-awaiting-review' };
        const q = qualificationStatus(actor, inspection.visitType, true);
        if (!q.ok) return q;
        if (inspection.inspectorId === actor.id) return { ok: false, error: 'independent-reviewer-required' };
        if (comment.length < 5) return { ok: false, error: 'review-comment-required' };
        const now = Date.now();
        const review = { id: id('review'), inspectionId, reviewerId: actor.id, decision, comment, reviewedAt: now, updatedAt: now };
        const next = { ...inspection, qcReviewId: review.id, qcReviewerId: actor.id, qcReviewer: actor.name, qcComment: comment, qcReviewedAt: now, updatedAt: now };
        let reportNo = '';
        if (decision === 'approve') {
          const day = new Date().toISOString().slice(0, 10).replaceAll('-', '');
          const key = 'bridge_dev_report_seq_' + day;
          const sequence = Number(localStorage.getItem(key) || 0) + 1;
          localStorage.setItem(key, String(sequence));
          reportNo = 'B-' + day + '-' + String(sequence).padStart(3, '0');
          Object.assign(next, { workflowStatus: 'approved', status: 'نهایی', reportNo, no: reportNo, officialReport: true, approvedAt: now, approvedBy: actor.id, fieldLocked: true });
        } else Object.assign(next, { workflowStatus: 'returned', status: 'بازگشت برای اصلاح', returnedAt: now, fieldLocked: false });
        return { ok: true, reportNo, entities: { inspections: [next], reviews: [review],
          audit: [fallbackAudit(actor, decision === 'approve' ? 'inspection-approved' : 'inspection-returned', 'inspections', inspectionId, comment)] } };
      },
      async voidInspection({ inspectionId, reason }) {
        const actor = currentUser();
        if (!actor || reason.length < 10) return { ok: false, error: 'void-reason-required' };
        const value = { id: 'void-' + inspectionId, inspectionId, reason, approvedBy: actor.id, voidedAt: Date.now(), status: 'void', updatedAt: Date.now() };
        return fallbackPatch('inspectionVoids', value, fallbackAudit(actor, 'report-voided', 'inspections', inspectionId, reason));
      },
      async createCorrectionDraft({ inspectionId, reason }) {
        const actor = currentUser();
        const source = dbList('inspections').find(item => item.id === inspectionId);
        if (!source || reason.length < 8) return { ok: false, error: 'correction-reason-required' };
        const next = { ...source, id: id('ins'), status: 'پیش‌نویس', workflowStatus: 'draft', supersedesInspectionId: inspectionId,
          correctionReason: reason, reportNo: undefined, no: undefined, officialReport: false, signatureAttachment: null,
          inspectorId: actor.id, inspector: actor.name, updatedAt: Date.now() };
        return { ...fallbackPatch('inspections', next, fallbackAudit(actor, 'correction-draft-created', 'inspections', next.id, reason)), draftId: next.id };
      },
      async saveInspectionProgram({ program }) {
        const actor = currentUser();
        if (![ROLES.ADMIN, ROLES.SUPERVISOR].includes(actor?.role)) return { ok: false, error: 'role-not-authorized' };
        const value = { ...program, id: 'program-' + program.bridgeId, effectiveDueDate: program.riskOverrideDueDate || program.dueDate, updatedAt: Date.now() };
        return fallbackPatch('inspectionPrograms', value, fallbackAudit(actor, 'inspection-program-saved', 'inspectionPrograms', value.id));
      },
      async updateCriticalFinding(payload) {
        const actor = currentUser();
        const value = { ...dbList('criticalFindings').find(item => item.id === payload.caseId), updatedAt: Date.now() };
        if (payload.action === 'acknowledge') Object.assign(value, { status: 'acknowledged', acknowledgmentStatus: 'acknowledged', acknowledgedAt: Date.now(), acknowledgedBy: actor.id });
        if (payload.action === 'interim') Object.assign(value, { status: 'interim-action', interimAction: payload.summary, interimActionAt: Date.now() });
        if (payload.action === 'resolve') Object.assign(value, { status: 'resolution-pending-verification', resolutionSummary: payload.summary, resolutionEvidence: payload.evidenceReference, resolvedBy: actor.id });
        if (payload.action === 'close') Object.assign(value, { status: 'closed', closedAt: Date.now(), closedBy: actor.id });
        return fallbackPatch('criticalFindings', value, fallbackAudit(actor, 'critical-finding-' + payload.action, 'criticalFindings', payload.caseId));
      },
      async appendAudit(payload) {
        const event = fallbackAudit(currentUser(), payload.action, payload.kind, payload.entityId, payload.reason || payload.detail);
        return { ok: true, entities: { audit: [event] } };
      },
    };
  }

  async function init() {
    installFallbackHandlers();
    state.initialized = true;
    await refreshAuthStatus(true);
    renderReviews();
    renderPrograms();
    renderCriticalFindings();
    scheduleCriticalReminders();
    scheduleProgramReminders();
    document.getElementById('authPin')?.addEventListener('keydown', event => {
      if (event.key === 'Enter') submitAuthentication();
    });
    document.addEventListener('bridge-governed-entities', event => {
      const entities = event.detail?.entities || {};
      if (entities.criticalFindings) { renderCriticalFindings(); scheduleCriticalReminders(); }
      if (entities.inspectionPrograms) { renderPrograms(); scheduleProgramReminders(); }
      if (entities.inspections || entities.reviews) renderReviews();
    });
  }

  window.Governance = {
    initialized: false,
    currentUser,
    requireSession,
    openAuthentication,
    refreshAuthStatus,
    errorMessage,
    qualificationStatus,
    criticalCompleteness,
    programStatus,
    isOfficialInspection,
    isVoided,
    workflowLabel,
    collectCriticalCases,
    collectInspectorReassignment,
  };
  Object.defineProperty(window.Governance, 'initialized', { get: () => state.initialized });
  Object.assign(window, {
    renderUsers, editUser, saveUserEditor, setActiveUser, deleteUser, confirmDeactivateUser,
    submitAuthentication, toggleSession, collectCriticalCases, confirmCriticalSafetyResponse, cancelCriticalSafetyResponse,
    confirmInspectorReassignment, cancelInspectorReassignment,
    renderReviews, openReview, submitReview, openRecordGovernance, voidInspectionRecord, createCorrectionDraft,
    renderCriticalFindings, openCriticalFinding, updateCriticalFinding,
    renderPrograms, editInspectionProgram, saveInspectionProgram,
    isOfficialInspection,
  });
  window.onRailReady(init);
})();
