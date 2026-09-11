package ir.bridge.maintenance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class UiFunctionalInstrumentationTest {
 @Test public void bootstrapPinCreatesAuthenticatedAdministratorSession() throws Exception {
  freshDatabase();
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   waitReady(s);
   authenticateAdmin(s);
   assertEquals("user-admin",eval(s,"Governance.currentUser()?.id||''"));
   assertEquals("locked-session-control",eval(s,"document.getElementById('sessionButton')?.classList.contains('session-unlocked')?'locked-session-control':'missing'"));
  }
 }
 @Test public void bridgeSelectionIsFirstAndRemovedRailwayFieldsDoNotExist() throws Exception {
  freshDatabase();
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   waitReady(s);authenticateAdmin(s);eval(s,"(()=>{const fs=BRIDGE_PROFILE_SCHEMA.sections.flatMap(x=>x.fields),fid=l=>fs.find(x=>x.label===l).id,b={id:'qa-ui-bridge',name:'پل رابط',code:'UI-01',use:'راه‌آهن',profile:{}};b.profile[fid('نام پل')]=b.name;b.profile[fid('کد پل')]=b.code;b.profile[fid('کاربری اصلی')]=b.use;b.profile[fid('مصالح اَبَرسازه')]='فولادی';b.profile[fid('نوع روسازی')]='راه‌آهن - ریل روی تراورس و بالاست متکی بر عرشه';dbSave('bridges',b);newInspection(b.id);return 'ok'})()");
   assertEquals("ok",eval(s,"(()=>{const ids=['region','fromStation','toStation','lineType','locationType','kmStart','kmEnd','kmDir','speed','lineClass','weather'];if(ids.some(id=>document.getElementById(id)))return 'bad-fields';const first=document.querySelector('#inspectionForm .card-body .field select');if(first?.id!=='inspectionBridge'||first.value!=='qa-ui-bridge')return 'bad-first';document.querySelector('.checkcat-title')?.click();const row=document.querySelector('.item-occurrence'),rail=[...document.querySelectorAll('.checkcat')].some(x=>x.dataset.category==='rail');return row&&row.querySelector('.occ-location-capture')&&row.dataset.photoKey&&rail?'ok':'bad-checklist'})()"));
  }
 }
 @Test public void guideAndNightModeRemainUsable() throws Exception {
  freshDatabase();
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   waitReady(s);authenticateAdmin(s);
   assertEquals("ok",eval(s,"go('guide');(()=>{const p=document.getElementById('guide');return p?.classList.contains('active')&&p.querySelectorAll('details').length>=10?'ok':'bad'})()"));
   assertEquals("ok",eval(s,"(()=>{document.body.dataset.theme='night';const st=getComputedStyle(document.body),card=getComputedStyle(document.querySelector('.card')||document.body);return st.color&&st.backgroundColor&&card.color?'ok':'bad'})()"));
  }
 }
 @Test public void v120DynamicUiRealDeletionCableExclusionAndDefaultAppearanceContract() throws Exception {
  freshDatabase();
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   waitReady(s);authenticateAdmin(s);
   assertEquals("ok",eval(s,"(()=>{const sections=BRIDGE_PROFILE_SCHEMA.sections,sf=(sid,l)=>sections.find(s=>s.id===sid).fields.find(f=>f.label===l).id,fid=l=>sections.flatMap(s=>s.fields).find(f=>f.label===l).id,b={id:'qa-v110-bridge',name:'پل مستقیم',code:'QA-110',use:'راه‌آهن',profile:{}};b.profile[fid('نام پل')]=b.name;b.profile[fid('کد پل')]=b.code;b.profile[fid('کاربری اصلی')]=b.use;b.profile[fid('مصالح اَبَرسازه')]='فولادی';b.profile[fid('نوع روسازی')]='راه‌آهن - ریل مستقیم بر عناصر کف عرشه';b.profile[sf('14_نرده_جانپناه','وجود جزء؟')]='وجود ندارد';b.profile[sf('15_پیاده_رو_جزیره','وجود پیاده‌رو/جزیره؟')]='وجود ندارد';b.profile[sf('18_کابل_دیافراگم','وجود سیستم ویژه؟')]='وجود ندارد';b.profile[sf('18_کابل_دیافراگم','کابل/سیستم اصلی')]='فاقد کابل اصلی';dbSave('bridges',b);const started=performance.now();newInspection(b.id);const renderMs=performance.now()-started,cats=[...document.querySelectorAll('.checkcat')].map(x=>x.dataset.category),first=document.querySelectorAll('.checkcat-title')[0],second=document.querySelectorAll('.checkcat-title')[1];first.click();second.click();const exclusive=first.closest('.checkcat').classList.contains('collapsed')&&!second.closest('.checkcat').classList.contains('collapsed');const noLegacy=!document.getElementById('actionText')&&!document.getElementById('responsible')&&!!document.querySelector('#inspectionForm [onclick*=\"openSignature\"]');return !cats.includes('cable')&&!cats.includes('rail_ballast')&&!cats.includes('road_pavement')&&exclusive&&noLegacy&&renderMs<750&&normalizedAppearance({}).textSize==='m'?'ok':'bad'})()"));
   assertEquals("started",eval(s,"(()=>{const d={id:'qa-delete-draft',status:'پیش‌نویس',bridgeId:'qa-v110-bridge',bridgeName:'پل مستقیم',updatedAt:Date.now()};dbSave('inspections',d);window.confirm=()=>true;deleteSingleRecord('drafts',d.id);return 'started'})()"));
   assertEquals("deleted",waitForValue(s,"dbList('inspections').some(x=>x.id==='qa-delete-draft')?'waiting':'deleted'","deleted",5000));
   assertEquals("ok",eval(s,"go('bridges');(()=>{const t=document.querySelector('.bridge-tags')?.textContent||'';return /railway|steel|direct-track/.test(t)?'bad':'ok'})()"));
  }
 }
 private static String waitForValue(ActivityScenario<MainActivity> s,String js,String expected,long timeout)throws Exception{long deadline=SystemClock.elapsedRealtime()+timeout;String value="";while(SystemClock.elapsedRealtime()<deadline){value=eval(s,js);if(expected.equals(value))return value;SystemClock.sleep(100);}return value;}
 private static void waitReady(ActivityScenario<MainActivity> s)throws Exception{long deadline=SystemClock.elapsedRealtime()+15000;String v="";while(SystemClock.elapsedRealtime()<deadline){v=eval(s,"document.readyState==='complete'&&typeof dbList==='function'&&typeof newInspection==='function'&&window.Governance?.initialized?'ready':'wait'");if("ready".equals(v))return;SystemClock.sleep(150);}assertEquals("ready",v);}
 private static void authenticateAdmin(ActivityScenario<MainActivity> s)throws Exception{assertEquals("started",eval(s,"(()=>{window.__qaAuth='wait';document.getElementById('authPin').value='135790';document.getElementById('authPinConfirm').value='135790';submitAuthentication().then(()=>window.__qaAuth=Governance.currentUser()?.id||'failed').catch(()=>window.__qaAuth='failed');return 'started'})()"));assertEquals("user-admin",waitForValue(s,"window.__qaAuth||'wait'","user-admin",10000));}
 private static void freshDatabase(){Context context=ApplicationProvider.getApplicationContext();context.deleteDatabase(AppDb.DB_NAME);}
 private static String eval(ActivityScenario<MainActivity> s,String js)throws Exception{AtomicReference<String> out=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);s.onActivity(a->{WebView v=findWebView(a.findViewById(android.R.id.content));assertTrue(v!=null);v.evaluateJavascript(js,r->{String value=r==null?"":r;if(value.length()>=2&&value.startsWith("\"")&&value.endsWith("\"")){value=value.substring(1,value.length()-1).replace("\\\"","\"").replace("\\n","\n").replace("\\\\","\\");}out.set(value);done.countDown();});});assertTrue(done.await(20,TimeUnit.SECONDS));return out.get();}
 private static WebView findWebView(android.view.View v){if(v instanceof WebView)return(WebView)v;if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++){WebView w=findWebView(g.getChildAt(i));if(w!=null)return w;}}return null;}
}
