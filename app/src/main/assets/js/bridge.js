(function () {
  'use strict';
  const store = Object.create(null); const readyCallbacks = []; const mutationWaiters = new Map(); const requestWaiters = new Map(); let ready = false; let versionName = 'web-dev'; let versionCode = 0; let mutationSequence = 0;
  function cloneList(kind){return Array.isArray(store[kind])?store[kind]:[];}
  function send(type,payload){try{if(window.BridgeNative&&typeof window.BridgeNative.postMessage==='function'){window.BridgeNative.postMessage(JSON.stringify({type,payload:payload||{}}));return true;}}catch(e){console.error('Native bridge message failed',type,e);}return false;}
  function sendTransfer(type,payload){try{if(window.BridgeTransferNative&&typeof window.BridgeTransferNative.postMessage==='function'){window.BridgeTransferNative.postMessage(JSON.stringify({type,payload:payload||{}}));return true;}}catch(e){console.error('Bridge transfer failed',type,e);}return false;}
  function saveLocal(kind,obj){const a=cloneList(kind).filter(x=>String(x.id)!==String(obj.id));a.unshift(obj);store[kind]=a;try{localStorage.setItem('bridge_'+kind,JSON.stringify(a));}catch(_){} }
  function deleteLocal(kind,id){const a=cloneList(kind).filter(x=>String(x.id)!==String(id));store[kind]=a;try{localStorage.setItem('bridge_'+kind,JSON.stringify(a));}catch(_){} }
  function normalizedDeleteEntries(entries){const seen=new Set();return (Array.isArray(entries)?entries:[]).map(x=>({kind:String(x?.kind||''),id:String(x?.id||'')})).filter(x=>{const key=`${x.kind}\u0000${x.id}`;if(!x.kind||!x.id||seen.has(key))return false;seen.add(key);return true;});}
  function deleteBatch(entries){
    const clean=normalizedDeleteEntries(entries);if(!clean.length)return Promise.resolve({ok:true,deleted:0});
    // Android exposes a synchronous, origin-local delete endpoint.  Using it
    // first makes the operation transactional from the page's point of view:
    // the in-memory list is changed only after SQLite confirms the delete.
    // WebMessage remains a compatibility fallback for older installed builds.
    if(window.BridgeAndroid&&typeof window.BridgeAndroid.deleteBatch==='function'){
      const requestId=`del-${Date.now().toString(36)}-${(++mutationSequence).toString(36)}`;
      try{
        const raw=window.BridgeAndroid.deleteBatch(JSON.stringify({requestId,entries:clean}));
        const result=typeof raw==='string'?JSON.parse(raw):raw||{};
        if(result?.ok){clean.forEach(x=>deleteLocal(x.kind,x.id));document.dispatchEvent(new CustomEvent('bridge-entities-deleted',{detail:{entries:clean}}));}
        return Promise.resolve(result);
      }catch(error){
        console.error('Direct native delete failed',error);
        return Promise.resolve({ok:false,error:'direct-delete-failed'});
      }
    }
    const native=!!(window.BridgeNative&&typeof window.BridgeNative.postMessage==='function');
    if(!native){clean.forEach(x=>deleteLocal(x.kind,x.id));document.dispatchEvent(new CustomEvent('bridge-entities-deleted',{detail:{entries:clean}}));return Promise.resolve({ok:true,deleted:clean.length});}
    const requestId=`del-${Date.now().toString(36)}-${(++mutationSequence).toString(36)}`;
    // Update the in-memory store immediately.  Some Android WebView versions
    // do not deliver the asynchronous Java callback back to the page even
    // though the SQLite transaction succeeds; waiting for that callback made
    // deletion appear broken until the app was restarted.
    clean.forEach(x=>deleteLocal(x.kind,x.id));
    document.dispatchEvent(new CustomEvent('bridge-entities-deleted',{detail:{entries:clean,pending:true}}));
    return new Promise(resolve=>{const timer=setTimeout(()=>{mutationWaiters.delete(requestId);resolve({ok:true,deleted:clean.length,callbackLost:true});},5000);mutationWaiters.set(requestId,{entries:clean,resolve,timer});
      // Deletion is a small control message. Use the primary WebMessage channel
      // so it also works on WebViews without the transfer listener.
      // Different Android System WebView versions have exposed only one of
      // the two registered message listeners reliably.  Send the idempotent
      // delete request through both channels; the SQLite delete is safe when
      // the second delivery finds rows already removed.
      const primarySent=send('deleteBatch',{requestId,entries:clean});
      const transferSent=sendTransfer('deleteBatch',{requestId,entries:clean});
      if(!primarySent&&!transferSent){clearTimeout(timer);mutationWaiters.delete(requestId);resolve({ok:false,error:'bridge-unavailable'});}
    });
  }
  function applyEntityPatches(patches){Object.entries(patches||{}).forEach(([kind,rows])=>{if(!Array.isArray(rows))return;rows.forEach(row=>{if(row?.id!=null)saveLocal(kind,row);});});document.dispatchEvent(new CustomEvent('bridge-governed-entities',{detail:{entities:patches||{}}}));}
  function request(type,payload={}){
    const native=!!(window.BridgeNative&&typeof window.BridgeNative.postMessage==='function');
    if(!native){
      const handler=window.BridgeFallbackHandlers?.[type];
      return Promise.resolve().then(()=>typeof handler==='function'?handler(payload):{ok:false,error:'native-required'}).then(result=>{if(result?.entities)applyEntityPatches(result.entities);return result;});
    }
    const requestId='req-'+Date.now().toString(36)+'-'+(++mutationSequence).toString(36);
    return new Promise(resolve=>{
      const timer=setTimeout(()=>{requestWaiters.delete(requestId);resolve({ok:false,error:'native-timeout'});},45000);
      requestWaiters.set(requestId,{resolve,timer});
      if(!send(type,{...payload,requestId})){clearTimeout(timer);requestWaiters.delete(requestId);resolve({ok:false,error:'bridge-unavailable'});}
    });
  }
  function hydrateFallback(){const kinds=window.ENTITY_KINDS||['users','bridges','standards','inspections','defects','audit','settings','reminders','reviews','criticalFindings','inspectionVoids','reportRevisions','inspectionPrograms'];kinds.forEach(kind=>{try{store[kind]=JSON.parse(localStorage.getItem('bridge_'+kind)||localStorage.getItem('rail_'+kind)||'[]');}catch(_){store[kind]=[];}});}
  function fireReady(){if(ready)return;ready=true;while(readyCallbacks.length){const cb=readyCallbacks.shift();try{cb();}catch(e){console.error('bridge-ready callback failed',e);}}document.dispatchEvent(new CustomEvent('bridge-ready'));}
  window.onRailReady=function(callback){if(typeof callback!=='function')return;if(ready)setTimeout(callback,0);else readyCallbacks.push(callback);};
  window.BridgeNativeClient={
    bootstrap(snapshot,vName,vCode){let parsed={};try{parsed=JSON.parse(snapshot||'{}')||{};}catch(e){console.error('Invalid DB bootstrap',e);}Object.keys(parsed).forEach(k=>{store[k]=Array.isArray(parsed[k])?parsed[k]:[];});versionName=String(vName||versionName);versionCode=Number(vCode||0);fireReady();},
    dbList(kind){return cloneList(kind);},
    dbSave(kind,id,json){let obj;try{obj=JSON.parse(json);}catch(_){return false;}saveLocal(kind,obj);return send('dbSave',{kind:String(kind),id:String(id),json:JSON.stringify(obj)});},
    dbDelete(kind,id){return deleteBatch([{kind,id}]);},
    dbDeleteBatch(entries){return deleteBatch(entries);},
    request(type,payload){return request(type,payload||{});},
    authStatus(){return request('authStatus',{});},
    authenticate(userId,pin){return request('authenticate',{userId,pin});},
    enrollPin(userId,pin){return request('enrollPin',{userId,pin});},
    lockSession(){return request('lockSession',{});},
    submitInspection(bundle){return request('submitInspection',bundle||{});},
    reviewInspection(payload){return request('reviewInspection',payload||{});},
    voidInspection(payload){return request('voidInspection',payload||{});},
    createCorrectionDraft(payload){return request('createCorrectionDraft',payload||{});},
    saveInspectionProgram(program){return request('saveInspectionProgram',{program});},
    updateCriticalFinding(payload){return request('updateCriticalFinding',payload||{});},
    saveGovernedUser(user){return request('saveGovernedUser',{user});},
    deactivateGovernedUser(userId,reason){return request('deactivateGovernedUser',{userId,reason});},
    appendAudit(payload){return request('appendAudit',payload||{});},
    finalizeInspection(inspection,defects,reminders,audit){return send('finalizeInspection',{inspection,defects:defects||[],reminders:reminders||[],audit});},
    pickImage(payload){return send('pickImage',payload||{});},takePhoto(payload){return send('takePhoto',payload||{});},requestLocation(payload){return send('requestLocation',payload||{});},openLocationSettings(){return send('openLocationSettings',{});},saveSignature(payload){return send('saveSignature',payload||{});},pickBackup(){return send('pickBackup',{});},pickBridge(){return sendTransfer('pickBridge',{});},exportBridge(bridge){return sendTransfer('exportBridge',{bridge});},scheduleReminder(id,timestamp,title,body){return send('scheduleReminder',{id,timestamp,title,body});},exportReport(format,name,report){return send('exportReport',{format,name,report});},exportBackup(){return send('exportBackup',{});},restorePendingBackup(){return send('restorePendingBackup',{});},appVersion(){return versionName+' ('+versionCode+')';},isNative(){return !!(window.BridgeNative&&typeof window.BridgeNative.postMessage==='function');}
  };
  window.Native=window.BridgeNativeClient;
  window.dbList=kind=>window.BridgeNativeClient.dbList(kind);
  window.dbSave=function(kind,obj){if(!obj||obj.id==null)return;obj.updatedAt=Date.now();window.BridgeNativeClient.dbSave(kind,String(obj.id),JSON.stringify(obj));document.dispatchEvent(new CustomEvent('bridge-entity-saved',{detail:{kind:String(kind),obj}}));};
  window.dbDelete=(kind,id)=>window.BridgeNativeClient.dbDelete(kind,String(id));
  window.dbDeleteBatch=entries=>window.BridgeNativeClient.dbDeleteBatch(entries);
  window.receiveDbDeleteResult=function(raw){let result={ok:false};try{result=typeof raw==='string'?JSON.parse(raw):raw||result;}catch(_){}const requestId=String(result.requestId||''),waiter=mutationWaiters.get(requestId);if(!waiter)return;clearTimeout(waiter.timer);mutationWaiters.delete(requestId);if(result.ok){waiter.entries.forEach(x=>deleteLocal(x.kind,x.id));document.dispatchEvent(new CustomEvent('bridge-entities-deleted',{detail:{entries:waiter.entries}}));}waiter.resolve(result);};
  window.receiveNativeRequestResult=function(raw){let result={ok:false,error:'invalid-native-response'};try{result=typeof raw==='string'?JSON.parse(raw):raw||result;}catch(_){}const requestId=String(result.requestId||''),waiter=requestWaiters.get(requestId);if(!waiter)return;clearTimeout(waiter.timer);requestWaiters.delete(requestId);if(result.ok&&result.entities)applyEntityPatches(result.entities);waiter.resolve(result);};
  window.receiveFinalizeResult=function(raw){let r={ok:false};try{r=typeof raw==='string'?JSON.parse(raw):raw||r;}catch(_){}const pending=window.pendingFinalization;if(r.ok&&pending){saveLocal('inspections',pending.inspection);(pending.defects||[]).forEach(x=>saveLocal('defects',x));(pending.reminders||[]).forEach(x=>saveLocal('reminders',x));if(pending.audit)saveLocal('audit',pending.audit);window.setCurrentInspection?.(pending.inspection);window.currentInspection=pending.inspection;window.setFinalizationPending?.(false);window.pendingFinalization=null;document.dispatchEvent(new CustomEvent('bridge-finalization-committed',{detail:{inspection:pending.inspection}}));if(typeof toast==='function')toast('بازدید با موفقیت نهایی شد');if(typeof go==='function')go('dashboard');}else{window.setFinalizationPending?.(false);window.pendingFinalization=null;if(typeof toast==='function')toast('نهایی‌سازی بازدید انجام نشد؛ پیش‌نویس شما حفظ شد.');}};
  window.addEventListener('load',()=>{setTimeout(()=>{if(!ready&&!window.BridgeNativeClient.isNative()){hydrateFallback();fireReady();}},25);},{once:true});
  const resilience=document.createElement('script');resilience.src='js/resilience.js';resilience.defer=true;document.head.appendChild(resilience);
})();
