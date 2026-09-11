package ir.bridge.maintenance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ReportExporter {
    private final Context context;
    private final AppMediaStore mediaStore;
    public ReportExporter(Context context){this(context,null);}
    public ReportExporter(Context context,AppMediaStore mediaStore){this.context=context.getApplicationContext();this.mediaStore=mediaStore;}
    public File create(String format,String requestedName,JSONObject payload)throws Exception{
        File dir=new File(context.getCacheDir(),"exports");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Cannot create export cache");
        String ext="pdf".equals(format)?".pdf":"xlsx".equals(format)?".xlsx":"zip".equals(format)?".zip":".csv",name=safeFileName(requestedName,"bridge-inspection-report"+ext);if(!name.toLowerCase(Locale.ROOT).endsWith(ext))name+=ext;
        File out=new File(dir,System.currentTimeMillis()+"-"+name);JSONArray rows=sanitizeChecklistRows(payload.optJSONArray("rows")),reportInfo=payload.optJSONArray("reportRows"),profiles=payload.optJSONArray("profileRows"),pdfRows=sanitizeChecklistRows(payload.optJSONArray("pdfRows")),records=payload.optJSONArray("records"),criticalFindings=payload.optJSONArray("criticalFindings");if(reportInfo==null)reportInfo=new JSONArray();if(profiles==null)profiles=new JSONArray();if(pdfRows.length()==0)pdfRows=rows;if(records==null)records=new JSONArray();if(criticalFindings==null)criticalFindings=new JSONArray();reportInfo=appendGovernanceRows(reportInfo,records,criticalFindings);JSONObject meta=payload.optJSONObject("meta");if(meta==null)meta=new JSONObject();
        switch(format){case"pdf":writePdf(out,meta,reportInfo,profiles,pdfRows);break;case"xlsx":writeXlsx(out,reportInfo,profiles,rows);break;case"csv":writeCsv(out,reportInfo,profiles,rows);break;case"zip":writeBundle(out,meta,reportInfo,profiles,rows,pdfRows,records,criticalFindings,payload.optJSONArray("bridges"),payload.optJSONObject("profileSchema"),payload.optJSONObject("engineeringReferences"));break;default:throw new IllegalArgumentException("Unsupported export format");}return out;
    }
    private void writeBundle(File out,JSONObject meta,JSONArray reportInfo,JSONArray profiles,JSONArray rows,JSONArray pdfRows,JSONArray records,JSONArray criticalFindings,JSONArray bridges,JSONObject profileSchema,JSONObject references)throws Exception{
        File dir=out.getParentFile(),pdf=new File(dir,out.getName()+".tmp.pdf"),xlsx=new File(dir,out.getName()+".tmp.xlsx"),csv=new File(dir,out.getName()+".tmp.csv");
        try{
            writePdf(pdf,meta,reportInfo,profiles,pdfRows);writeXlsx(xlsx,reportInfo,profiles,rows);writeCsv(csv,reportInfo,profiles,rows);
            try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(out))){
                zipFile(z,"report/bridge-inspection-report.pdf",pdf);zipFile(z,"report/bridge-inspection-report.xlsx",xlsx);zipFile(z,"report/bridge-inspection-report.csv",csv);
                zipText(z,"data/inspections.json",records==null?"[]":records.toString(2));
                zipText(z,"data/critical-findings.json",criticalFindings==null?"[]":criticalFindings.toString(2));
                zipText(z,"data/bridges.json",bridges==null?"[]":bridges.toString(2));
                zipText(z,"data/bridge-profile-schema.json",profileSchema==null?"{}":profileSchema.toString(2));
                zipText(z,"data/engineering-references.json",references==null?"{}":references.toString(2));
                zipText(z,"data/report-metadata.json",meta.toString(2));
                if(records!=null){
                    for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);if(r==null)continue;String visit=safeZipPart((r.optString("no","visit-"+(i+1))+"_"+r.optString("jdate","")+"_"+r.optString("bridgeCode","")+"_"+r.optString("bridgeName","")));Set<String> seen=new LinkedHashSet<>();
                        JSONArray general=r.optJSONArray("photos");addPhotos(z,general,"photos/"+visit+"/00-general/",seen);
                        JSONArray items=r.optJSONArray("items");if(items!=null)for(int j=0;j<items.length();j++){JSONObject it=items.optJSONObject(j);if(it==null)continue;String code=safeZipPart(it.optString("code",it.optString("itemCode","item")+"."+Math.max(1,it.optInt("occurrenceIndex",1))));addPhotos(z,it.optJSONArray("photos"),"photos/"+visit+"/"+code+"/",seen);}JSONObject signature=r.optJSONObject("signatureAttachment");if(signature!=null)addPhotos(z,new JSONArray().put(signature),"امضا/"+visit+"/",new LinkedHashSet<>());
                    }
                }
            }
        }finally{pdf.delete();xlsx.delete();csv.delete();}
    }
    private static JSONArray appendGovernanceRows(JSONArray source,JSONArray records,JSONArray criticalFindings)throws Exception{
        JSONArray rows=new JSONArray(source.toString());
        if(records.length()>0){
            rows.put(new JSONArray());
            rows.put(new JSONArray().put("زنجیره هویت و تأیید").put("جزئیات ثبت‌شده"));
            for(int i=0;i<records.length();i++){
                JSONObject record=records.optJSONObject(i);if(record==null)continue;
                JSONObject signature=record.optJSONObject("signatureAttachment");
                String label="گزارش "+record.optString("reportNo",record.optString("no",record.optString("id")));
                String detail="بازرس: "+record.optString("inspector")+" ["+record.optString("inspectorId")+"]"
                        +" | امضا: "+(signature==null?"ثبت نشده":signature.optString("mediaId"))+" | هش رکورد: "+record.optString("fieldHash",record.optString("identityStatus","legacy-unverified"))
                        +" | ارسال: "+record.optString("submittedAt","ثبت نشده")+" | QC: "+record.optString("qcReviewer","میراثی/ثبت نشده")
                        +" ["+record.optString("qcReviewerId")+"] | تأیید: "+record.optString("approvedAt","ثبت نشده")+" | نظر QC: "+record.optString("qcComment","");
                rows.put(new JSONArray().put(label).put(detail));
            }
        }
        rows.put(new JSONArray());
        rows.put(new JSONArray().put("پرونده‌های یافته بحرانی").put("جزئیات ایمنی و پیگیری"));
        if(criticalFindings.length()==0)rows.put(new JSONArray().put("تعداد پرونده مرتبط").put("۰"));
        for(int i=0;i<criticalFindings.length();i++){
            JSONObject item=criticalFindings.optJSONObject(i);if(item==null)continue;
            String label=item.optString("id")+" • "+item.optString("itemCode")+" • "+item.optString("title");
            String detail="وضعیت: "+item.optString("status")+" | کشف: "+item.optString("discoveredAt")
                    +" | اقدام فوری: "+item.optString("immediateAction")+" | محدودیت: "+item.optString("operatingRestriction")
                    +" — "+item.optString("restrictionRationale")+" | اعلان: "+item.optString("notifiedContact")+" @ "+item.optString("notifiedAt")
                    +" | مسئول: "+item.optString("ownerId")+" | مهلت: "+item.optString("dueAt")
                    +" | تشدید پیگیری: "+item.optString("escalationStatus")+" — "+item.optString("overdueReason")
                    +" | پذیرش: "+item.optString("acknowledgmentStatus")+" | رفع خطر: "+item.optString("resolutionSummary")
                    +" | مدرک: "+item.optString("resolutionEvidence")+" | بستن مستقل: "+item.optString("closedBy");
            rows.put(new JSONArray().put(label).put(detail));
        }
        return rows;
    }
    private void addPhotos(ZipOutputStream z,JSONArray photos,String prefix,Set<String> seen)throws Exception{if(photos==null||mediaStore==null)return;for(int i=0;i<photos.length();i++){JSONObject p=photos.optJSONObject(i);if(p==null)continue;String mid=p.optString("mediaId",p.optString("id",""));if(mid.isEmpty()||!seen.add(mid))continue;File f=mediaStore.resolveMedia(mid);if(f==null)continue;String name=safeZipPart(p.optString("name",f.getName()));if(name.isEmpty())name="image-"+(i+1)+".jpg";zipFile(z,prefix+name,f);}}
    private static void zipFile(ZipOutputStream z,String path,File file)throws Exception{z.putNextEntry(new ZipEntry(path));try(FileInputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)z.write(b,0,n);}z.closeEntry();}
    private static String safeZipPart(String s){String x=s==null?"":s.trim().replaceAll("[\\/:*?\"<>|\r\n]","_").replace("..","_");return x.length()>100?x.substring(0,100):x;}
    /** Remove report/bridge metadata from checklist tables even when a legacy
     * payload still contains those columns. Metadata remains in the dedicated
     * report and bridge sheets. */
    private static JSONArray sanitizeChecklistRows(JSONArray input){
        JSONArray source=input==null?new JSONArray():input,header=source.optJSONArray(0);if(header==null)return source;
        Set<Integer> keep=new LinkedHashSet<>();for(int i=0;i<header.length();i++)if(!isChecklistMetadataHeader(header.optString(i,""))&&!header.optString(i,"").trim().isEmpty()&&!header.optString(i,"").trim().matches("[-—]+"))keep.add(i);
        JSONArray out=new JSONArray();for(int r=0;r<source.length();r++){JSONArray row=source.optJSONArray(r);if(row==null){out.put(JSONObject.NULL);continue;}JSONArray cleaned=new JSONArray();for(Integer column:keep){Object value=column<row.length()?row.opt(column):JSONObject.NULL;cleaned.put(value==null?JSONObject.NULL:value);}out.put(cleaned);}return out;
    }
    private static boolean isChecklistMetadataHeader(String value){
        String h=(value==null?"":value).replace("‌","").replace(" ","").trim();
        return h.equals("تاریخ")||h.equals("تاریخبازدید")||h.equals("نامپل")||h.equals("پل")||h.equals("کدپل")||h.equals("کاربری")||h.equals("کاربریپل")||h.equals("نوعبازدید")||h.equals("بازرس")||h.equals("گزارش")||h.equals("شمارهگزارش")||h.equals("مختصاتپل")||h.equals("GPSپل")||h.equals("اقدام/مسئول");
    }
    private static JSONArray combinedRows(JSONArray reportInfo,JSONArray profiles,JSONArray rows){JSONArray all=new JSONArray();for(int i=0;i<reportInfo.length();i++)all.put(reportInfo.optJSONArray(i));all.put(new JSONArray());for(int i=0;i<profiles.length();i++)all.put(profiles.optJSONArray(i));all.put(new JSONArray());for(int i=0;i<rows.length();i++)all.put(rows.optJSONArray(i));return all;}
    private void writeCsv(File out,JSONArray reportInfo,JSONArray profiles,JSONArray rows)throws Exception{writeCsv(out,combinedRows(reportInfo,profiles,rows));}
    private void writeXlsx(File out,JSONArray reportInfo,JSONArray profiles,JSONArray rows)throws Exception{
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(out))){
            zipText(z,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            zipText(z,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            zipText(z,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"مشخصات گزارش\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"مشخصات پل\" sheetId=\"2\" r:id=\"rId2\"/><sheet name=\"چک‌لیست بازرسی\" sheetId=\"3\" r:id=\"rId3\"/></sheets><calcPr fullCalcOnLoad=\"1\"/></workbook>");
            zipText(z,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/><Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet3.xml\"/><Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            zipText(z,"xl/styles.xml",xlsxStyles());
            zipText(z,"xl/worksheets/sheet1.xml",xlsxSheet(reportInfo,true));
            zipText(z,"xl/worksheets/sheet2.xml",xlsxSheet(profiles,true));
            zipText(z,"xl/worksheets/sheet3.xml",xlsxSheet(rows,false));
        }
    }
    private static String xlsxStyles(){return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"4\"><font><sz val=\"10\"/><name val=\"Vazirmatn\"/><color rgb=\"FF20343E\"/></font><font><b/><sz val=\"10\"/><name val=\"Vazirmatn\"/><color rgb=\"FFFFFFFF\"/></font><font><b/><sz val=\"10\"/><name val=\"Vazirmatn\"/><color rgb=\"FF173443\"/></font><font><u/><sz val=\"10\"/><name val=\"Vazirmatn\"/><color rgb=\"FF0563C1\"/></font></fonts><fills count=\"8\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF123E56\"/><bgColor indexed=\"64\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDCE8EE\"/><bgColor indexed=\"64\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFBE4E7\"/><bgColor indexed=\"64\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFF0DF\"/><bgColor indexed=\"64\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFFF0D3\"/><bgColor indexed=\"64\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE2F3E9\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"2\"><border/><border><left style=\"thin\"><color rgb=\"FFD2DEE3\"/></left><right style=\"thin\"><color rgb=\"FFD2DEE3\"/></right><top style=\"thin\"><color rgb=\"FFD2DEE3\"/></top><bottom style=\"thin\"><color rgb=\"FFD2DEE3\"/></bottom></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"9\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" applyAlignment=\"1\"><alignment horizontal=\"right\" vertical=\"center\" wrapText=\"1\" readingOrder=\"0\"/></xf><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\" readingOrder=\"0\"/></xf><xf numFmtId=\"0\" fontId=\"2\" fillId=\"3\" borderId=\"1\" applyAlignment=\"1\"><alignment horizontal=\"right\" vertical=\"center\" wrapText=\"1\" readingOrder=\"0\"/></xf><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" applyAlignment=\"1\"><alignment horizontal=\"right\" vertical=\"center\" wrapText=\"1\" readingOrder=\"0\"/></xf><xf numFmtId=\"0\" fontId=\"2\" fillId=\"4\" borderId=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\" readingOrder=\"0\"/></xf><xf numFmtId=\"0\" fontId=\"2\" fillId=\"5\" borderId=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\" readingOrder=\"0\"/></xf><xf numFmtId=\"0\" fontId=\"2\" fillId=\"6\" borderId=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\" readingOrder=\"0\"/></xf><xf numFmtId=\"0\" fontId=\"2\" fillId=\"7\" borderId=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\" readingOrder=\"0\"/></xf><xf numFmtId=\"0\" fontId=\"3\" fillId=\"0\" borderId=\"1\" applyAlignment=\"1\"><alignment horizontal=\"right\" vertical=\"center\" wrapText=\"1\" readingOrder=\"0\"/></xf></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";}
    private static String xlsxSheet(JSONArray rows,boolean profile){return ReportLayout.xlsxSheet(rows,profile);}
    private void writeCsv(File out,JSONArray rows)throws Exception{try(FileOutputStream fos=new FileOutputStream(out);BufferedWriter w=new BufferedWriter(new OutputStreamWriter(fos,StandardCharsets.UTF_8))){w.write('\ufeff');for(int r=0;r<rows.length();r++){JSONArray row=rows.optJSONArray(r);if(row==null)continue;for(int c=0;c<row.length();c++){if(c>0)w.write(',');String raw=row.optString(c,""),v=row.opt(c) instanceof Number?raw:protectFormula(csvLinkValue(raw));w.write('"');w.write(v.replace("\"","\"\""));w.write('"');}w.write("\r\n");}}}
    private static String csvLinkValue(String value){if(value!=null&&value.startsWith("LINK|")){String[] p=value.split("\\|",3);String target=p.length>1?p[1]:"",label=p.length>2?p[2]:target;return label+(target.isEmpty()?"":" | "+target);}return value==null?"":value;}
    private void writePdf(File out,JSONObject meta,JSONArray reportInfo,JSONArray profiles,JSONArray rows)throws Exception{ReportLayout.writePdf(context,out,meta,reportInfo,profiles,rows);}
    private static String displayLink(String value){if(value!=null&&value.startsWith("LINK|")){String[] p=value.split("\\|",3);return p.length>2?p[2]:"";}return value==null?"":value;}
    private static void drawRtlCell(Canvas c,Paint p,String text,float x,float y,float w,float h,boolean center,int maxLines){
        String value=(text==null||text.trim().isEmpty())?"—":text;int width=Math.max(1,(int)Math.floor(w-8));
        TextPaint tp=new TextPaint(p);tp.setTextAlign(Paint.Align.LEFT);
        StaticLayout layout=StaticLayout.Builder.obtain(value,0,value.length(),tp,width)
                .setAlignment(center?Layout.Alignment.ALIGN_CENTER:Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false).setMaxLines(Math.max(1,maxLines)).setEllipsize(TextUtils.TruncateAt.END).build();
        float top=y+Math.max(2f,(h-layout.getHeight())/2f);c.save();c.translate(x+4,top);layout.draw(c);c.restore();
    }
    private static int measureRtlHeight(Paint p,String text,float width,boolean center){String value=(text==null||text.trim().isEmpty())?"—":text;TextPaint tp=new TextPaint(p);tp.setTextAlign(Paint.Align.LEFT);StaticLayout layout=StaticLayout.Builder.obtain(value,0,value.length(),tp,Math.max(1,(int)Math.floor(width-8))).setAlignment(center?Layout.Alignment.ALIGN_CENTER:Layout.Alignment.ALIGN_NORMAL).setTextDirection(TextDirectionHeuristics.RTL).setIncludePad(false).build();return layout.getHeight();}
    private static void drawCellBorder(Canvas c,Paint p,float x,float y,float w,float h){Paint.Style old=p.getStyle();int color=p.getColor();float stroke=p.getStrokeWidth();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.4f);p.setColor(0xffd2dee3);c.drawRect(x,y,x+w,y+h,p);p.setStyle(old);p.setStrokeWidth(stroke);p.setColor(color);}
    private static int severityColor(String s){if(s.contains("اضطراری"))return 0xffb42318;if(s.contains("متوسط"))return 0xffd96b00;if(s.contains("کم"))return 0xff9a7500;return 0xff1f7a55;}
    private static String protectFormula(String s){String t=s==null?"":s,l=t.replaceFirst("^\\s+","");return(!l.isEmpty()&&"=+-@".indexOf(l.charAt(0))>=0)?"'"+t:t;}
    private static String safeFileName(String n,String fallback){String s=n==null?fallback:n.trim();s=s.replaceAll("[\\\\/:*?\"<>|\\r\\n]","_");return s.isEmpty()?fallback:(s.length()>120?s.substring(0,120):s);}
    private static String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    private static String col(int n){StringBuilder s=new StringBuilder();while(n>0){n--;s.insert(0,(char)('A'+n%26));n/=26;}return s.toString();}
    private static int maxColumns(JSONArray rows){int m=1;for(int i=0;i<rows.length();i++){JSONArray r=rows.optJSONArray(i);if(r!=null)m=Math.max(m,r.length());}return m;}
    private static void zipText(ZipOutputStream z,String path,String text)throws Exception{z.putNextEntry(new ZipEntry(path));z.write(text.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
}
