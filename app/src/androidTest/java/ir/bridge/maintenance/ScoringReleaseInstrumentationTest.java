package ir.bridge.maintenance;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class ScoringReleaseInstrumentationTest {
    @Test public void fieldFormPreservesIdentityAndShowsFiveStatuses() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            ready(scenario);
            assertEquals("ok",eval(scenario,"(()=>{const fs=BRIDGE_PROFILE_SCHEMA.sections.flatMap(s=>s.fields),fid=l=>fs.find(f=>f.label===l).id,b={id:'qa-v160',name:'پل آزمایشی ۱.۶',code:'QA-160',use:'راه',profile:{}};b.profile[fid('نام پل')]=b.name;b.profile[fid('کاربری اصلی')]='راه';b.profile[fid('مصالح اَبَرسازه')]='بتن درجا - بتن مسلح';b.profile[fid('نوع روسازی')]='آسفالت';dbSave('bridges',b);newInspection(b.id);document.querySelector('.checkcat-title').click();const row=document.querySelector('.item-occurrence');return row.querySelectorAll('.sev-choice').length===5&&row.dataset.assessed==='false'?'ok':'bad'})()"));
            assertEquals("ok",eval(scenario,"(()=>{const row=document.querySelector('.item-occurrence'),key=row.dataset.item;row.querySelector('[data-sev=low]').click();addChecklistOccurrence(key);addChecklistOccurrence(key);currentInspection.itemPhotos[key+'::3']=[{mediaId:'qa-photo',name:'عکس رخداد سوم'}];currentInspection.itemLocations[key+'::3']={lat:35.7,lon:51.4};removeChecklistOccurrence(key,2);if(itemPhotosFor(key+'::3')[0]?.mediaId!=='qa-photo'||occurrenceLocationFor(key+'::3')?.lat!==35.7)return 'identity';removeChecklistOccurrence(key,3);addChecklistOccurrence(key);return !!document.querySelector('[data-occurrence=\"4\"]')?'ok':'reused'})()"));
            assertEquals("ok",eval(scenario,"(()=>{const row=document.querySelector('.item-occurrence'),key=row.dataset.photoKey;currentInspection.items=[{itemId:row.dataset.item,occurrenceIndex:1,location:{lat:35,lon:51}}];clearOccurrenceLocation(key);return collectInspectionItems().find(x=>x.itemId===row.dataset.item&&x.occurrenceIndex===1).location===null?'ok':'stale-gps'})()"));
            assertEquals("ok",eval(scenario,"(()=>{const items=collectInspectionItems();return !validateEngineeringFinalization(items)&&!document.getElementById('inspectionValidation').hidden?'ok':'accepted-unassessed'})()"));
            eval(scenario,"go('academy');document.querySelector('.lesson summary').click();'ok'");
            capture(scenario,"academy-light.png");
            eval(scenario,"applyAppearance({...normalizedAppearance({}),theme:'night',textSize:'xl'});'ok'");
            capture(scenario,"academy-night-large.png");
            assertEquals("ok",eval(scenario,"document.documentElement.scrollWidth<=window.innerWidth+2?'ok':'overflow'"));
            eval(scenario,"applyAppearance(normalizedAppearance({}));go('inspectionForm');'ok'");
            capture(scenario,"inspection-form.png");
            eval(scenario,"document.querySelector('.checkitem').scrollIntoView({block:'start'});'ok'");
            capture(scenario,"checklist-five-statuses.png");
            eval(scenario,"document.getElementById('scorePreview').scrollIntoView({block:'start'});'ok'");
            capture(scenario,"score-preview.png");
        }
    }

    @Test public void scoredFinalizationRejectsPendingRowsAndRequiresEvidence() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();c.deleteDatabase(AppDb.DB_NAME);
        try(AppDb db=new AppDb(c)){
            JSONObject row=new JSONObject().put("itemId","municipal-58").put("statusId","uninspectable").put("assessed",false);
            JSONObject record=new JSONObject().put("id","qa-score-final").put("bridgeId","qa-v160").put("status","نهایی")
                .put("checklistSchemaVersion","1.6.0").put("scoringVersion","1.6.0").put("scores",new JSONObject().put("version","1.6.0"))
                .put("inspector","بازرس آزمایشی").put("signatureAttachment",new JSONObject().put("mediaId","qa-signature").put("signer","بازرس آزمایشی"))
                .put("items",new JSONArray().put(row));
            JSONObject audit=new JSONObject().put("id","qa-score-audit").put("kind","inspections").put("eid","qa-score-final");
            for(int stage=0;stage<2;stage++){
                boolean rejected=false;try{TransactionalFinalizer.commit(db,record,new JSONArray(),new JSONArray(),audit);}catch(IllegalArgumentException e){rejected=true;}
                assertTrue(rejected);assertEquals(0,new JSONArray(db.list("inspections")).length());row.put("assessed",true);
            }
            row.put("note","زیر تکیه‌گاه دسترسی ایمن وجود ندارد؛ بازدید با بالابر لازم است.");
            TransactionalFinalizer.commit(db,record,new JSONArray(),new JSONArray(),audit);
            assertEquals(1,new JSONArray(db.list("inspections")).length());
        }
    }

    @Test public void longPersianExportRetainsTextTotalsAndNumericCells() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();StringBuilder longNote=new StringBuilder();
        for(int i=0;i<180;i++)longNote.append("سطر ").append(i).append(": مشاهده آزمایشی ترک در تیر و ثبت گستره آسیب.\n");
        longNote.append("پایان توضیحات کنترل‌شده");
        JSONArray header=new JSONArray().put("ردیف").put("کد رخداد").put("گروه").put("مورد بازرسی").put("سطح آسیب").put("ضریب").put("کلید امتیاز").put("نمره").put("GPS آسیب").put("توضیحات").put("-");
        JSONArray rows=new JSONArray().put(header).put(new JSONArray().put(1).put("1.1.1").put("بتن").put("ترک مورب تیر").put("اضطراری").put(4).put("M50").put(-400).put("35.7, 51.4").put(longNote.toString()).put(""))
            .put(new JSONArray().put("").put("").put("").put("نمره منفی کلی پل").put("").put("").put("").put(-400).put("").put("").put(""));
        JSONObject payload=new JSONObject().put("meta",new JSONObject().put("organization","گزارش آزمایشی نسخه ۱.۶.۰"))
            .put("reportRows",new JSONArray().put(new JSONArray().put("مشخصات گزارش").put("مقدار")).put(new JSONArray().put("مبنای محاسبه").put("آزمون صفحه‌بندی؛ اطلاعات پل واقعی نیست")))
            .put("profileRows",new JSONArray().put(new JSONArray().put("مشخصات پل").put("مقدار")).put(new JSONArray().put("نام پل").put("پل آزمایشی")))
            .put("rows",rows).put("pdfRows",rows);
        ReportExporter exporter=new ReportExporter(c);
        File xlsx=exporter.create("xlsx","qa-long-report.xlsx",payload),pdf=exporter.create("pdf","qa-long-report.pdf",payload),csv=exporter.create("csv","qa-long-report.csv",payload);
        try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(xlsx)){
            String xml=new String(read(zip.getInputStream(zip.getEntry("xl/worksheets/sheet3.xml"))),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(xml.contains("پایان توضیحات کنترل‌شده"));assertTrue(xml.contains("<v>-400</v>"));assertTrue(xml.contains("mergeCells"));assertFalse(xml.contains(">-</t>"));
            java.util.regex.Matcher heights=java.util.regex.Pattern.compile("ht=\"([0-9.]+)\"").matcher(xml);
            while(heights.find())assertTrue("row clipping",Double.parseDouble(heights.group(1))<409);
        }
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){
            assertTrue(renderer.getPageCount()>=5);
            for(int n:new int[]{0,2,renderer.getPageCount()-1})try(PdfRenderer.Page page=renderer.openPage(n)){
                Bitmap bmp=Bitmap.createBitmap(page.getWidth()*2,page.getHeight()*2,Bitmap.Config.ARGB_8888);page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                try(FileOutputStream out=new FileOutputStream(new File(qaDir(c),"report-page-"+n+".png"))){bmp.compress(Bitmap.CompressFormat.PNG,100,out);}bmp.recycle();
            }
        }
        for(File f:new File[]{xlsx,pdf,csv})java.nio.file.Files.copy(f.toPath(),new File(qaDir(c),f.getName()).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    private static byte[] read(java.io.InputStream in)throws Exception{try(java.io.InputStream input=in;java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    private static File qaDir(Context c){File f=new File(c.getFilesDir(),"QA160");f.mkdirs();return f;}
    private static void capture(ActivityScenario<MainActivity> s,String name)throws Exception{
        SystemClock.sleep(3000);AtomicReference<Exception> error=new AtomicReference<>();s.onActivity(a->{try{WebView web=find(a.findViewById(android.R.id.content));Bitmap b=Bitmap.createBitmap(web.getWidth(),web.getHeight(),Bitmap.Config.ARGB_8888);web.draw(new Canvas(b));try(FileOutputStream out=new FileOutputStream(new File(qaDir(a),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}catch(Exception e){error.set(e);}});if(error.get()!=null)throw error.get();
    }
    private static void ready(ActivityScenario<MainActivity> s)throws Exception{long until=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<until){if("ready".equals(eval(s,"document.readyState==='complete'&&typeof BridgeScoring==='object'&&typeof newInspection==='function'?'ready':'wait'")))return;SystemClock.sleep(150);}fail("App did not become ready");}
    private static String eval(ActivityScenario<MainActivity> s,String js)throws Exception{AtomicReference<String> result=new AtomicReference<>();CountDownLatch latch=new CountDownLatch(1);s.onActivity(a->find(a.findViewById(android.R.id.content)).evaluateJavascript(js,r->{try{result.set(r!=null&&r.startsWith("\"")?new JSONArray("["+r+"]").getString(0):r);}catch(Exception e){result.set(r);}latch.countDown();}));assertTrue(latch.await(20,TimeUnit.SECONDS));return result.get();}
    private static WebView find(android.view.View v){if(v instanceof WebView)return(WebView)v;if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++){WebView w=find(g.getChildAt(i));if(w!=null)return w;}}return null;}
}
