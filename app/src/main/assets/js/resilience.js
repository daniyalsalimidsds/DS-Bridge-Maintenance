(function () {
  'use strict';

  let autosaveTimer = null;
  let emptyDraftDeleteInFlight = false;
  let finalizationPending = false;
  let signatureState = { strokes: [], active: null };
  const el = id => document.getElementById(id);
  const val = id => el(id)?.value?.trim?.() ?? '';

  function currentDraft() {
    const base = window.currentInspection;
    const bridge = window.selectedInspectionBridge?.();
    if (document.querySelector('.page.active')?.id !== 'inspectionForm') return null;
    if (finalizationPending || !base || !bridge || ['submitted','approved'].includes(base.workflowStatus) || base.status === 'نهایی' || base.finalizedAt) return null;
    window.captureEngineeringAssessment?.();
    const items = typeof window.collectInspectionItems === 'function' ? window.collectInspectionItems() : [];
    if (typeof window.checklistHasEnteredData === 'function' && !window.checklistHasEnteredData(items) && !base.signatureAttachment && !base.internationalAssessment?.reviewer && !base.elementAssessments?.length) {
      const persisted = typeof dbList === 'function' && dbList('inspections').some(item => item.id === base.id && item.status === 'پیش‌نویس');
      if (persisted && !emptyDraftDeleteInFlight) {
        emptyDraftDeleteInFlight = true;
        const entries = window.inspectionDeleteEntries?.(base.id) || [{ kind: 'inspections', id: base.id }];
        Promise.resolve(window.dbDeleteBatch?.(entries)).finally(() => {
          emptyDraftDeleteInFlight = false;
          window.renderDrafts?.();
          window.renderDashboard?.();
        });
      }
      return null;
    }
    const overallResult=inspectionOverall(items);
    const overall=overallResult.id;
    return {
      ...base,
      jdate: el('jdateBtn')?.textContent?.trim() || base.jdate || '',
      gdate: val('gdate'),
      bridgeId: bridge.id,
      bridgeName: bridgeName(bridge),
      bridgeCode: bridgeCode(bridge),
      bridgeUse: bridgeUse(bridge),
      bridgeLocation: bridge.location ? { ...bridge.location } : null,
      bridgeSnapshot: JSON.parse(JSON.stringify(bridge)),
      visitType: val('visitType'),
      shift: val('shift'),
      inspector: window.getActiveUser?.()?.name || val('inspectorName'),
      inspectorId: window.getActiveUser?.()?.id || base.inspectorId || '',
      items,
      overall,
      overallLabel: overallResult.label,
      photos: [...(window.currentPhotos || [])],
      status: 'پیش‌نویس',
      workflowStatus: base.workflowStatus === 'returned' ? 'returned' : 'draft',
      draftAutoSavedAt: Date.now(),
      updatedAt: Date.now(),
    };
  }

  function autosaveNow() {
    const draft = currentDraft();
    if (!draft) return;
    Object.assign(window.currentInspection, draft);
    dbSave('inspections', draft);
    const badge = el('inspectionNo');
    if (badge && !String(badge.textContent).includes('ذخیره خودکار')) badge.textContent = 'پیش‌نویس • ذخیره خودکار';
  }

  function scheduleAutosave() {
    if (finalizationPending) return;
    clearTimeout(autosaveTimer);
    autosaveTimer = setTimeout(autosaveNow, 850);
  }

  function setFinalizationPending(value) {
    finalizationPending = Boolean(value);
    if (finalizationPending) clearTimeout(autosaveTimer);
  }

  function parseJalaliMs(text, hour = 9) {
    const parts = enNum(String(text || '')).match(/(\d{4})\D+(\d{1,2})\D+(\d{1,2})/);
    if (!parts || typeof window.toGregorian !== 'function') return 0;
    try {
      const gregorian = window.toGregorian(Number(parts[1]), Number(parts[2]), Number(parts[3]));
      const timestamp = new Date(gregorian.gy, gregorian.gm - 1, gregorian.gd, hour, 0, 0, 0).getTime();
      return Number.isFinite(timestamp) ? timestamp : 0;
    } catch (_) { return 0; }
  }

  function reminderRecordsForInspection(inspection) {
    if (!inspection || inspection.status !== 'نهایی') return [];
    const now = Date.now();
    const records = [];
    const deadline = parseJalaliMs(inspection.deadline, 9);
    const next = parseJalaliMs(inspection.nextInspection, 8);
    if (deadline > now) records.push({
      id: `deadline-${inspection.id}`, timestamp: deadline, title: 'مهلت رفع آسیب پل',
      body: `مهلت پیگیری گزارش ${inspection.no || ''} برای ${inspection.bridgeName || 'پل'} فرا رسیده است.`,
      ownerId: inspection.id, active: true, updatedAt: now,
    });
    if (next > now) records.push({
      id: `next-${inspection.id}`, timestamp: next, title: 'بازدید دوره‌ای پل',
      body: `زمان بازدید بعدی ${inspection.bridgeName || ''} / ${inspection.no || ''} فرا رسیده است.`,
      ownerId: inspection.id, active: true, updatedAt: now,
    });
    return records;
  }

  function scheduleInspectionReminders(inspection, persist = true) {
    reminderRecordsForInspection(inspection).forEach(reminder => {
      if (persist) dbSave('reminders', reminder);
      if (Native?.isNative?.()) Native.scheduleReminder(reminder.id, reminder.timestamp, reminder.title, reminder.body);
    });
  }

  function signaturePoint(event) {
    const canvas = el('signatureCanvas');
    const rect = canvas?.getBoundingClientRect();
    if (!rect || !rect.width || !rect.height) return null;
    return {
      x: Number(((event.clientX - rect.left) / rect.width).toFixed(5)),
      y: Number(((event.clientY - rect.top) / rect.height).toFixed(5)),
      p: Number(Math.max(0, Math.min(1, event.pressure || 0.5)).toFixed(3)),
      t: Date.now(),
    };
  }

  function bindSignature() {
    const canvas = el('signatureCanvas');
    if (!canvas || canvas.dataset.vectorBound) return;
    canvas.dataset.vectorBound = '1';
    canvas.addEventListener('pointerdown', event => {
      const point = signaturePoint(event);
      if (!point) return;
      signatureState.active = [point];
      signatureState.strokes.push(signatureState.active);
    });
    canvas.addEventListener('pointermove', event => {
      if (!signatureState.active) return;
      const point = signaturePoint(event);
      if (point) signatureState.active.push(point);
    });
    ['pointerup', 'pointercancel', 'lostpointercapture'].forEach(name => canvas.addEventListener(name, () => { signatureState.active = null; }));
  }

  function persistSignature() {
    const inspection = window.currentInspection;
    if (!inspection || !signatureState.strokes.some(stroke => stroke.length > 1)) return;
    inspection.signature = {
      format: 'bridge-signature-vector-v1',
      strokes: signatureState.strokes.map(stroke => stroke.slice(0, 5000)),
      capturedAt: Date.now(),
    };
    inspection.signaturePresent = true;
    autosaveNow();
  }

  function init() {
    const form = el('inspectionForm');
    form?.addEventListener('input', scheduleAutosave, true);
    form?.addEventListener('change', scheduleAutosave, true);
    bindSignature();
    document.addEventListener('click', event => {
      const button = event.target.closest?.('button');
      const code = button?.getAttribute('onclick') || '';
      if (code.includes('openSignature(')) { signatureState = { strokes: [], active: null }; setTimeout(bindSignature, 0); }
      else if (code.includes('clearSignature()')) signatureState = { strokes: [], active: null };
      else if (code.includes('saveSignature()')) setTimeout(persistSignature, 0);
    });
    document.addEventListener('bridge-finalization-committed', event => scheduleInspectionReminders(event.detail?.inspection, false));
    const drafts = dbList('inspections').filter(item => item.status === 'پیش‌نویس' && !item.archived);
    if (drafts.length) setTimeout(() => toast(`${faNum(drafts.length)} پیش‌نویس ذخیره‌شده برای ادامه موجود است.`), 1400);
  }

  window.autosaveCurrentInspection = scheduleAutosave;
  window.scheduleInspectionAutosave = scheduleAutosave;
  window.flushInspectionAutosave = autosaveNow;
  window.setFinalizationPending = setFinalizationPending;
  window.reminderRecordsForInspection = reminderRecordsForInspection;
  window.scheduleInspectionReminders = scheduleInspectionReminders;
  window.onRailReady(init);
})();
