package ir.bridge.maintenance;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@RunWith(AndroidJUnit4.class)
public class ReportExportInstrumentationTest {
    @Test public void nativePersianBridgePdfIsValidAndMultipage() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        File pdf=new ReportExporter(context).create("pdf","bridge-report.pdf",sample(110));
        assertTrue(pdf.isFile());assertTrue(pdf.length()>12_000);
        try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(descriptor)){
            assertTrue("expected multipage PDF",renderer.getPageCount()>=2);
            try(PdfRenderer.Page page=renderer.openPage(0)){assertTrue(page.getWidth()>page.getHeight());}
        }
    }

    @Test public void csvAndStyledThreeSheetXlsxSeparateReportBridgeAndGroupedChecklist() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();ReportExporter exporter=new ReportExporter(context);JSONObject payload=sample(8);
        payload.put("records",new JSONArray().put(new JSONObject().put("id","inspection-governed").put("reportNo","B-20260910-001")
                .put("inspector","بازرس نمونه").put("inspectorId","inspector-1").put("fieldHash","abc123")
                .put("qcReviewer","بازبین مستقل").put("qcReviewerId","reviewer-1").put("qcComment","تأیید مستقل شواهد")));
        payload.put("criticalFindings",new JSONArray().put(new JSONObject().put("id","critical-1")
                .put("inspectionId","inspection-governed").put("itemCode","1.1").put("title","ناپایداری عضو")
                .put("status","open").put("immediateAction","انسداد و ایمن‌سازی محدوده")
                .put("operatingRestriction","closure").put("restrictionRationale","خطر فوری")
                .put("notifiedContact","مرکز کنترل").put("notifiedAt","2026-09-10T10:00:00Z")
                .put("ownerId","inspector-1").put("dueAt","2026-09-10T12:00:00Z")));
        payload.getJSONArray("rows").put(new JSONArray().put("=2+2").put("@SUM(A1:A2)"));
        File csv=exporter.create("csv","bridge-report.csv",payload);String text=new String(read(csv),StandardCharsets.UTF_8);
        assertTrue(text.startsWith("\ufeff"));assertTrue(text.contains("مشخصات پل"));assertTrue(text.contains("موقعیت مکانی"));assertTrue(text.contains("35.689200, 51.389000"));assertFalse(text.contains("\"۱۴۰۵/۰۵/۲۰\""));assertFalse(text.contains("اقدام / مسئول"));assertTrue(text.contains("'=2+2"));assertTrue(text.contains("../photos/"));
        File xlsx=exporter.create("xlsx","bridge-report.xlsx",payload);assertTrue(xlsx.length()>1000);
        try(ZipFile zip=new ZipFile(xlsx)){
            assertNotNull(zip.getEntry("xl/styles.xml"));
            String reportXml=new String(read(zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))),StandardCharsets.UTF_8);
            String profileXml=new String(read(zip.getInputStream(zip.getEntry("xl/worksheets/sheet2.xml"))),StandardCharsets.UTF_8);
            String checklistXml=new String(read(zip.getInputStream(zip.getEntry("xl/worksheets/sheet3.xml"))),StandardCharsets.UTF_8);
            assertTrue(reportXml.contains("مشخصات گزارش"));assertTrue(reportXml.contains("نام بازرس"));assertTrue(reportXml.contains("بازرس نمونه"));
            assertTrue(reportXml.contains("زنجیره هویت و تأیید"));assertTrue(reportXml.contains("پرونده‌های یافته بحرانی"));assertTrue(reportXml.contains("انسداد و ایمن‌سازی محدوده"));
            assertTrue(profileXml.contains("rightToLeft=\"1\""));assertTrue(profileXml.contains("موقعیت مکانی"));assertTrue(profileXml.contains("35.689200, 51.389000"));
            assertTrue(checklistXml.contains("rightToLeft=\"1\""));assertTrue(checklistXml.contains("customWidth=\"1\""));assertTrue(checklistXml.contains("customHeight=\"1\""));assertTrue(checklistXml.contains("HYPERLINK"));assertFalse(checklistXml.contains("تاریخ"));assertFalse(checklistXml.contains("۱۴۰۵/۰۵/۲۰"));assertFalse(checklistXml.contains("اقدام / مسئول"));assertFalse(checklistXml.contains("شماره گزارش"));assertFalse(checklistXml.contains(">بازرس<"));
            String workbook=new String(read(zip.getInputStream(zip.getEntry("xl/workbook.xml"))),StandardCharsets.UTF_8);assertTrue(workbook.contains("مشخصات گزارش"));assertTrue(workbook.contains("مشخصات پل"));assertTrue(workbook.contains("چک‌لیست بازرسی"));
        }
    }

    @Test public void completeZipContainsAllReportsBridgeDataPhotoAndSignature() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();context.deleteDatabase(AppDb.DB_NAME);
        try(AppDb db=new AppDb(context)){
            AppMediaStore media=new AppMediaStore(context,db);String png="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
            JSONObject photo=media.saveDataUri(png,"BR-01-ترک تیر.png","14050520","inspection","ins-zip-1");
            JSONObject signature=media.saveSignatureDataUri(png,"بازرس نمونه","۱۴۰۵/۰۵/۲۰","ins-zip-1");
            JSONObject bridge=new JSONObject().put("id","bridge-1").put("name","پل آزادی").put("code","BR-01").put("use","راه").put("profile",new JSONObject().put("name","پل آزادی"));
            JSONObject record=new JSONObject().put("id","ins-zip-1").put("no","B-ZIP-1").put("jdate","۱۴۰۵/۰۵/۲۰").put("bridgeId","bridge-1").put("bridgeName","پل آزادی").put("bridgeCode","BR-01").put("signatureAttachment",signature).put("items",new JSONArray().put(new JSONObject().put("itemId","concrete-1").put("itemCode","2").put("occurrenceIndex",1).put("code","2.1").put("location",new JSONObject().put("lat",35.6892).put("lon",51.389).put("accuracyM",7.5)).put("photos",new JSONArray().put(photo))));
            JSONObject critical=new JSONObject().put("id","critical-zip-1").put("inspectionId","ins-zip-1").put("itemCode","2.1")
                    .put("title","یافته بحرانی نمونه").put("status","closed").put("immediateAction","ایمن‌سازی فوری")
                    .put("operatingRestriction","closure").put("restrictionRationale","خطر فوری")
                    .put("notifiedContact","مرکز کنترل").put("notifiedAt","2026-09-10T10:00:00Z")
                    .put("ownerId","inspector-1").put("dueAt","2026-09-10T12:00:00Z");
            JSONObject payload=sample(2).put("records",new JSONArray().put(record)).put("criticalFindings",new JSONArray().put(critical)).put("bridges",new JSONArray().put(bridge)).put("profileSchema",new JSONObject().put("version","367-menu-profile-v1")).put("engineeringReferences",new JSONObject().put("iran","نشریه ۳۶۷"));
            File bundle=new ReportExporter(context,media).create("zip","complete.zip",payload);assertTrue(bundle.isFile());
            try(ZipFile zip=new ZipFile(bundle)){
                for(String path:new String[]{"report/bridge-inspection-report.pdf","report/bridge-inspection-report.xlsx","report/bridge-inspection-report.csv","data/inspections.json","data/critical-findings.json","data/bridges.json","data/bridge-profile-schema.json","data/engineering-references.json"})assertNotNull(path,zip.getEntry(path));
                assertTrue(new String(read(zip.getInputStream(zip.getEntry("data/critical-findings.json"))),StandardCharsets.UTF_8).contains("ایمن‌سازی فوری"));
                assertTrue(zip.stream().anyMatch(entry->entry.getName().startsWith("photos/B-ZIP-1_")&&entry.getName().contains("/2.1/")));
                assertTrue(zip.stream().anyMatch(entry->entry.getName().startsWith("امضا/B-ZIP-1_")&&entry.getName().endsWith(".png")));
            }
        }
    }

    private static JSONObject sample(int count)throws Exception{
        JSONObject payload=new JSONObject().put("meta",new JSONObject().put("organization","شرکت مهندسین مشاور هگزا").put("title","گزارش نگهداری و بازرسی چشمی پل").put("generatedAt","1405/05/20"));
        JSONArray headers=new JSONArray().put("ردیف").put("کد رخداد").put("تاریخ").put("گروه بازرسی").put("مورد بازرسی").put("سطح آسیب").put("مختصات آسیب").put("توضیحات").put("نام تصویر");
        JSONArray rows=new JSONArray().put(headers);String[] levels={"ندارد","کم","متوسط","اضطراری"};
        JSONArray pdfRows=new JSONArray().put(new JSONArray().put("ردیف").put("کد").put("تاریخ").put("گروه").put("مورد بازرسی").put("سطح آسیب").put("GPS آسیب").put("توضیحات").put("نام تصویر"));
        for(int i=0;i<count;i++){
            String image="BR-01-ترک-"+(i+1)+".jpg",link="LINK|../photos/B-"+i+"/1."+(i+1)+"/"+image+"|"+image;
            rows.put(new JSONArray().put(i+1).put("1."+(i+1)).put("۱۴۰۵/۰۵/۲۰").put(i%4==0?"اعضای بتنی":"").put("ترک تیر").put(levels[i%levels.length]).put("35.689200, 51.389000").put("متن فارسی طولانی برای کنترل شکست سطر شماره "+i).put(link));
            pdfRows.put(new JSONArray().put(i+1).put("1."+(i+1)).put("۱۴۰۵/۰۵/۲۰").put(i%4==0?"اعضای بتنی":"").put("ترک تیر").put(levels[i%levels.length]).put("35.689200, 51.389000").put("توضیحات فارسی "+i).put(link));
        }
        JSONArray profileRows=new JSONArray().put(new JSONArray().put("مشخصات پل").put("مقدار")).put(new JSONArray().put("نام پل").put("پل نمونه")).put(new JSONArray().put("موقعیت مکانی").put("35.689200, 51.389000"));
        JSONArray reportRows=new JSONArray().put(new JSONArray().put("مشخصات گزارش").put("مقدار")).put(new JSONArray().put("نام گزارش").put("گزارش نگهداری پل")).put(new JSONArray().put("نام بازرس").put("بازرس نمونه")).put(new JSONArray().put("کل موارد دارای آسیب").put("6"));
        return payload.put("reportRows",reportRows).put("profileRows",profileRows).put("rows",rows).put("pdfRows",pdfRows);
    }

    private static byte[] read(File file)throws Exception{try(FileInputStream input=new FileInputStream(file)){return read(input);}}
    private static byte[] read(InputStream input)throws Exception{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}}
}
