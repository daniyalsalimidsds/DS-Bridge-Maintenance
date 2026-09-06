(function () {
  'use strict';
  const stack = ['dashboard'];
  let lastHomeBack = 0;
  const EXIT_WINDOW_MS = 2100;

  function syncVisualViewport(){
    const viewport=window.visualViewport,root=document.documentElement;
    const top=viewport?.offsetTop||0,height=viewport?.height||window.innerHeight;
    root.style.setProperty('--bridge-viewport-top',`${Math.max(0,top)}px`);
    root.style.setProperty('--bridge-viewport-height',`${Math.max(1,height)}px`);
  }
  syncVisualViewport();
  window.addEventListener('resize',syncVisualViewport,{passive:true});
  window.visualViewport?.addEventListener('resize',syncVisualViewport,{passive:true});
  window.visualViewport?.addEventListener('scroll',syncVisualViewport,{passive:true});

  function activePageId() { return document.querySelector('.page.active')?.id || 'dashboard'; }
  function renderDestination(p) {
    try {
      if (p==='dashboard') renderDashboard(); else if (p==='bridges') renderBridges(); else if (p==='drafts') renderDrafts(); else if (p==='inspections') renderInspections(); else if (p==='defects') renderDefects();
      else if (p==='reports') renderReportStats(); else if (p==='users') renderUsers();
      else if (p==='master') masterTab();
      else if (p==='settings') loadSettings();
      else if(p==='academy') window.renderAcademy?.();
    } catch (e) { console.error('destination render failed', p, e); }
  }
  function showPage(p, push) {
    const el = document.getElementById(p); if (!el || !el.classList.contains('page')) return false;
    const current = activePageId();
    if(current==='inspectionForm' && p!==current)window.flushInspectionAutosave?.();
    document.querySelectorAll('.page').forEach(x => x.classList.remove('active'));
    el.classList.add('active');
    const title = document.getElementById('pageTitle'); if (title) title.textContent = (window.pageNames || {})[p] || '';
    closeDrawers(); closeOpenSheetsExcept(null);
    const main = document.querySelector('main'); if (main) main.scrollTo({top:0, behavior:'auto'});
    if (push && current !== p) {
      if (stack[stack.length-1] !== current) stack.push(current);
      stack.push(p);
      while (stack.length > 30) stack.shift();
    }
    window.currentPage = p;
    renderDestination(p);
    return true;
  }
  window.go = p => showPage(p, true);
  window.openDrawer = function(side) {
    closeDrawers();
    const d = document.getElementById(side + 'Drawer'); const scrim = document.getElementById('scrim');
    if (d) d.classList.add('open'); if (scrim) scrim.classList.add('open');
  };
  window.closeDrawers = function() {
    document.querySelectorAll('.drawer.open').forEach(x => { x.classList.remove('open'); x.style.transform=''; });
    const s=document.getElementById('scrim'); if(s)s.classList.remove('open');
  };
  function resetModalSheets(modal){modal?.querySelectorAll?.('[data-swipe-sheet]').forEach(s=>{s.style.transition='';s.style.transform='';s.classList.remove('open');});}
  window.closeModal = function(id) { const m=document.getElementById(id); if(m){m.classList.remove('open');resetModalSheets(m);} };
  function closeOpenSheetsExcept(except) {
    document.querySelectorAll('.modal.open').forEach(m => { if (m !== except) m.classList.remove('open'); });
    document.querySelectorAll('[data-swipe-sheet].open').forEach(s => { if (s !== except) s.classList.remove('open'); });
  }
  window.showGeneric = function(title, body, actions=[]) {
    const modal=document.getElementById('genericModal'),t=document.getElementById('genericTitle'),b=document.getElementById('genericBody'),a=document.getElementById('genericActions');
    if(!modal||!t||!b||!a)return;document.activeElement?.blur?.();syncVisualViewport();resetModalSheets(modal);t.textContent=title||'';b.innerHTML=body||'';a.innerHTML=actions.filter(x=>x&&x.t).map(x=>`<button class="btn ${x.c||''}" onclick="${x.fn||''}">${typeof esc==='function'?esc(x.t||''):x.t||''}</button>`).join('')+`<button class="btn" onclick="closeModal('genericModal')">بستن</button>`;modal.classList.add('open');
  };
  window.handleSystemBack = function() {
    const viewer=document.querySelector('.image-viewer.open,[data-image-viewer].open'); if(viewer){viewer.classList.remove('open');return 'handled';}
    const modal=[...document.querySelectorAll('.modal.open')].pop(); if(modal){modal.classList.remove('open');return 'handled';}
    const sheet=[...document.querySelectorAll('[data-swipe-sheet].open')].pop(); if(sheet){sheet.classList.remove('open');return 'handled';}
    if(document.querySelector('.drawer.open')){closeDrawers();return 'handled';}
    const search=document.querySelector('.search-overlay.open,[data-search-overlay].open'); if(search){search.classList.remove('open');return 'handled';}
    const current=activePageId();
    if(current!=='dashboard'){
      while(stack.length && stack[stack.length-1]===current) stack.pop();
      const previous=stack.pop() || 'dashboard'; showPage(previous,false); return 'handled';
    }
    const now=Date.now();
    if(now-lastHomeBack<=EXIT_WINDOW_MS){lastHomeBack=0;return 'exit';}
    lastHomeBack=now;
    if(typeof toast==='function')toast('برای خروج از نرم افزار دوباره بازگشت رو بزنین');
    return 'handled';
  };
  window.onRailReady(() => { stack.length=0; stack.push(activePageId()); window.currentPage=activePageId(); });
})();
