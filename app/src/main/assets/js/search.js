'use strict';
(function(){
  const fa='۰۱۲۳۴۵۶۷۸۹', ar='٠١٢٣٤٥٦٧٨٩', en='0123456789';
  function normalizeFaSearch(v){
    return String(v??'').normalize('NFKC')
      .replace(/[يى]/g,'ی').replace(/ك/g,'ک').replace(/[ۀة]/g,'ه')
      .replace(/ؤ/g,'و').replace(/[إأ]/g,'ا')
      .replace(/[\u064B-\u065F\u0670\u06D6-\u06ED]/g,'').replace(/ـ/g,'')
      .replace(/[۰-۹]/g,d=>en[fa.indexOf(d)]).replace(/[٠-٩]/g,d=>en[ar.indexOf(d)])
      .toLowerCase().replace(/\s+/g,' ').trim();
  }
  function debounce(fn,delay=180){let t;return function(...args){clearTimeout(t);t=setTimeout(()=>fn.apply(this,args),delay)}}
  function bindDebouncedSearch(id,handler){const input=document.getElementById(id);if(!input||input.dataset.debounceBound)return;input.dataset.debounceBound='1';input.removeAttribute('oninput');input.addEventListener('input',debounce(()=>{if(typeof window[handler]==='function')window[handler]();},180));}

  function filterInspectionChecklist(){
    const input=document.getElementById('checklistSearch'),state=document.getElementById('checklistSearchState'),q=normalizeFaSearch(input?.value||'');
    if(!q){document.querySelectorAll('#checklistHost .checkcat').forEach(cat=>{cat.hidden=false;cat.querySelectorAll('.checkitem').forEach(item=>item.hidden=false);window.setChecklistCategoryExpanded?.(cat,false);});if(state)state.textContent='';return;}
    if(q)document.querySelectorAll('#checklistHost .checkcat').forEach(cat=>window.ensureChecklistCategoryBody?.(cat));
    let visible=0,total=0;
    document.querySelectorAll('#checklistHost .checkcat').forEach(cat=>{let catVisible=0;const catName=normalizeFaSearch(cat.querySelector('.checkcat-title-main')?.textContent||cat.querySelector('.checkcat-title')?.textContent||'');cat.querySelectorAll('.checkitem').forEach(item=>{total++;const hay=normalizeFaSearch((item.querySelector('h4')?.textContent||'')+' '+catName);const show=!q||hay.includes(q);item.hidden=!show;if(show){visible++;catVisible++;}});cat.hidden=catVisible===0;if(typeof window.setChecklistCategoryExpanded==='function'&&!cat.hidden)window.setChecklistCategoryExpanded(cat,!!q);});
    if(state)state.textContent=q?(visible?`${faNum(visible)} مورد از ${faNum(total)} مورد نمایش داده می‌شود.`:'نتیجه‌ای برای این جستجو پیدا نشد.') : '';
  }
  function clearChecklistSearch(){const input=document.getElementById('checklistSearch');if(input){input.value='';input.focus();}filterInspectionChecklist();}
  window.filterInspectionChecklist=filterInspectionChecklist;window.clearChecklistSearch=clearChecklistSearch;
  window.normalizeFaSearch=normalizeFaSearch;
  window.debounceSearch=debounce;
  window.onRailReady?.(()=>{bindDebouncedSearch('bridgeSearch','renderBridges');bindDebouncedSearch('insSearch','renderInspections');bindDebouncedSearch('defSearch','renderDefects');bindDebouncedSearch('checklistSearch','filterInspectionChecklist');});
})();
