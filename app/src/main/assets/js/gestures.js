(function () {
  'use strict';
  const MIN_X=64, LOCK=12, VELOCITY=.28, SYS_EDGE_X=20, SYS_EDGE_Y=28;
  const excluded='input,textarea,select,option,canvas,[contenteditable="true"],[data-no-swipe],.map-viewport,.network-map,.photo img,.image-viewer,.carousel,input[type="range"],audio,video';
  let g=null;
  function drawerWidth(d){return d ? Math.max(1,d.getBoundingClientRect().width) : 1;}
  function resetDrawerStyles(){document.querySelectorAll('.drawer').forEach(d=>{d.style.transition='';d.style.transform='';});}
  function pointStart(x,y,target,source,id){
    if(target?.closest?.(excluded))return;
    const w=innerWidth,h=innerHeight;
    if(y<SYS_EDGE_Y||y>h-SYS_EDGE_Y||x<SYS_EDGE_X||x>w-SYS_EDGE_X)return;
    const open=document.querySelector('.drawer.open');
    g={source,id,sx:x,sy:y,x,y,lastT:performance.now(),vx:0,mode:null,open};
  }
  function pointMove(x,y,source,id,prevent){
    if(!g||g.source!==source||g.id!==id)return;
    const now=performance.now(),dt=Math.max(1,now-g.lastT),dx=x-g.x;
    g.vx=dx/dt;g.x=x;g.y=y;g.lastT=now;
    const totalX=g.x-g.sx,totalY=g.y-g.sy;
    if(!g.mode&&Math.hypot(totalX,totalY)>LOCK)g.mode=Math.abs(totalX)>Math.abs(totalY)*1.15?'horizontal':'vertical';
    if(g.mode!=='horizontal')return;
    prevent?.();
    const side=g.open?(g.open.classList.contains('left')?'left':'right'):(totalX>=0?'left':'right');
    const target=g.open||document.getElementById(side+'Drawer');
    if(!target)return;
    const width=drawerWidth(target);target.style.transition='none';
    if(g.open){
      const p=Math.max(-width,Math.min(width,totalX));
      target.style.transform=side==='left'?`translateX(${Math.min(0,p)}px)`:`translateX(${Math.max(0,p)}px)`;
    }else{
      const opening=(side==='left'&&totalX>0)||(side==='right'&&totalX<0);
      const progress=opening?Math.min(1,Math.abs(totalX)/width):0;
      target.style.transform=side==='left'?`translateX(${(-1+progress)*100}%)`:`translateX(${(1-progress)*100}%)`;
      if(progress>0)document.getElementById('scrim')?.classList.add('open');
    }
  }
  function pointEnd(source,id){
    if(!g||g.source!==source||g.id!==id)return;
    const dx=g.x-g.sx,dy=g.y-g.sy,fast=Math.abs(g.vx)>=VELOCITY;
    const horizontal=g.mode==='horizontal'&&Math.abs(dx)>Math.abs(dy)*1.15;
    const far=Math.abs(dx)>=MIN_X||fast,open=g.open;
    resetDrawerStyles();
    if(horizontal&&far)window.__bridgeLastWebHorizontalSwipe={at:Date.now(),dir:dx>0?'right':'left'};
    if(open&&horizontal&&far){
      const isLeft=open.classList.contains('left');
      if((isLeft&&dx<0)||(!isLeft&&dx>0))closeDrawers();else openDrawer(isLeft?'left':'right');
    }else if(!open&&horizontal&&far){
      if(dx>0)openDrawer('left');else if(dx<0)openDrawer('right');else closeDrawers();
    }else if(open)openDrawer(open.classList.contains('left')?'left':'right');else closeDrawers();
    g=null;
  }
  function cancelGesture(){
    resetDrawerStyles();
    if(g?.open)openDrawer(g.open.classList.contains('left')?'left':'right');else closeDrawers();
    g=null;
  }
  function initSwipeNavigation(){
    if(initSwipeNavigation._done)return;initSwipeNavigation._done=true;
    const touchCapable=('ontouchstart' in window)||(navigator.maxTouchPoints||0)>0;
    if(touchCapable){
      document.addEventListener('touchstart',e=>{if(e.touches.length!==1)return;const t=e.touches[0];pointStart(t.clientX,t.clientY,e.target,'touch',t.identifier);},{passive:true,capture:true});
      document.addEventListener('touchmove',e=>{if(!g||g.source!=='touch')return;const t=[...e.touches].find(x=>x.identifier===g.id);if(!t)return;pointMove(t.clientX,t.clientY,'touch',t.identifier,()=>e.preventDefault());},{passive:false,capture:true});
      document.addEventListener('touchend',e=>{if(!g||g.source!=='touch')return;const t=[...e.changedTouches].find(x=>x.identifier===g.id);if(t){g.x=t.clientX;g.y=t.clientY;pointEnd('touch',t.identifier);}},{passive:true,capture:true});
      document.addEventListener('touchcancel',()=>cancelGesture(),{passive:true,capture:true});
    }else{
      document.addEventListener('pointerdown',e=>{if(e.pointerType==='mouse'&&e.button!==0)return;pointStart(e.clientX,e.clientY,e.target,'pointer',e.pointerId);},{passive:true});
      document.addEventListener('pointermove',e=>pointMove(e.clientX,e.clientY,'pointer',e.pointerId,()=>e.preventDefault()),{passive:false});
      document.addEventListener('pointerup',e=>pointEnd('pointer',e.pointerId),{passive:true});
      document.addEventListener('pointercancel',()=>cancelGesture(),{passive:true});
    }
    initSheetSwipe(touchCapable);
  }

  let sg=null;
  function sheetStart(s,y,source,id){sg={s,id,source,sy:y,y,lastT:performance.now(),vy:0};s.style.transition='none';}
  function sheetMove(y,source,id,prevent){if(!sg||sg.source!==source||sg.id!==id)return;const now=performance.now(),dt=Math.max(1,now-sg.lastT);sg.vy=(y-sg.y)/dt;sg.y=y;sg.lastT=now;const dy=sg.y-sg.sy,kind=sg.s.dataset.swipeSheet||'bottom';if((kind==='bottom'&&dy>0)||(kind==='top'&&dy<0)){prevent?.();sg.s.style.transform=`translateY(${dy}px)`;}}
  function sheetEnd(source,id){if(!sg||sg.source!==source||sg.id!==id)return;const dy=sg.y-sg.sy,kind=sg.s.dataset.swipeSheet||'bottom',close=(kind==='bottom'&&(dy>90||sg.vy>.45))||(kind==='top'&&(dy<-90||sg.vy<-.45));sg.s.style.transition='';sg.s.style.transform='';const modal=sg.s.closest('.modal');if(close){window.__bridgeLastWebSheetSwipeAt=Date.now();sg.s.classList.remove('open');modal?.classList.remove('open');}sg=null;}
  function initSheetSwipe(touchCapable){
    if(touchCapable){
      document.addEventListener('touchstart',e=>{if(e.touches.length!==1||e.target.closest?.(excluded))return;const s=e.target.closest?.('[data-swipe-sheet]');if(!s)return;const t=e.touches[0];sheetStart(s,t.clientY,'touchsheet',t.identifier);},{passive:true,capture:true});
      document.addEventListener('touchmove',e=>{if(!sg||sg.source!=='touchsheet')return;const t=[...e.touches].find(x=>x.identifier===sg.id);if(t)sheetMove(t.clientY,'touchsheet',t.identifier,()=>e.preventDefault());},{passive:false,capture:true});
      document.addEventListener('touchend',e=>{if(!sg||sg.source!=='touchsheet')return;const t=[...e.changedTouches].find(x=>x.identifier===sg.id);if(t){sg.y=t.clientY;sheetEnd('touchsheet',t.identifier);}},{passive:true,capture:true});
      document.addEventListener('touchcancel',()=>{if(sg){sg.s.style.transition='';sg.s.style.transform='';sg=null;}},{passive:true,capture:true});
    }else{
      document.addEventListener('pointerdown',e=>{const s=e.target.closest?.('[data-swipe-sheet]');if(!s||e.target.closest?.(excluded))return;sheetStart(s,e.clientY,'pointersheet',e.pointerId);s.setPointerCapture?.(e.pointerId);},{passive:true});
      document.addEventListener('pointermove',e=>sheetMove(e.clientY,'pointersheet',e.pointerId,()=>e.preventDefault()),{passive:false});
      document.addEventListener('pointerup',e=>sheetEnd('pointersheet',e.pointerId),{passive:true});
      document.addEventListener('pointercancel',()=>{if(sg){sg.s.style.transition='';sg.s.style.transform='';sg=null;}},{passive:true});
    }
  }
  window.initSwipeNavigation=initSwipeNavigation;window.onRailReady(initSwipeNavigation);
})();
