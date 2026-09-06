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
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Content-sized, paginated RTL tables shared by all native export paths. */
final class ReportLayout {
    private ReportLayout() {}
    private static String text(Object raw) { return raw == null || raw == JSONObject.NULL ? "" : String.valueOf(raw); }
    private static String display(Object raw) {
        String value=text(raw);
        if(value.startsWith("LINK|")){String[] p=value.split("\\|",3);return p.length>2?p[2]:"";}
        return value;
    }
    private static boolean summary(JSONArray row) {
        return row!=null && row.length()>7 && text(row.opt(0)).isEmpty() && text(row.opt(2)).isEmpty() && !text(row.opt(3)).isEmpty();
    }
    private static boolean visit(JSONArray row) {
        return row!=null && row.length()>7 && text(row.opt(0)).isEmpty() && !text(row.opt(2)).isEmpty();
    }
    private static TextPaint paint(Typeface font,float size){TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);p.setTypeface(font);p.setTextSize(size);p.setColor(0xff20343e);return p;}
    private static StaticLayout layout(String value,TextPaint p,float width) {
        return StaticLayout.Builder.obtain(value,0,value.length(),p,Math.max(1,(int)width-12))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false).setLineSpacing(2,1).build();
    }
    private static double[] widths(JSONArray rows,boolean profile) {
        int count=0;for(int r=0;r<rows.length();r++){JSONArray row=rows.optJSONArray(r);if(row!=null)count=Math.max(count,row.length());}
        count=Math.max(1,count);double[] widths=new double[count];TextPaint p=paint(Typeface.DEFAULT,14);
        for(int c=0;c<count;c++){
            double best=profile?(c==0?24:32):8;
            for(int r=0;r<rows.length();r++){
                JSONArray row=rows.optJSONArray(r);if(row==null||(!profile&&(summary(row)||visit(row))))continue;
                for(String part:display(row.opt(c)).split("\n",-1))best=Math.max(best,(p.measureText(part)+20)/7.0);
            }
            double cap=profile?(c==0?48:90):(c==3?60:c==9?60:c==2?36:c==8?32:c==10?40:18);
            widths[c]=Math.min(cap,best);
        }
        return widths;
    }
    private static String xml(String value){return value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]","").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static String col(int n){StringBuilder s=new StringBuilder();while(n>0){n--;s.insert(0,(char)('A'+n%26));n/=26;}return s.toString();}
    private static List<String> cellParts(String value,float width) {
        List<String> parts=new ArrayList<>();
        if(value.isEmpty()){parts.add("");return parts;}
        StaticLayout measured=layout(value,paint(Typeface.DEFAULT,14),width);
        int firstLine=0,start=0;
        while(firstLine<measured.getLineCount()){
            int last=firstLine;
            while(last+1<measured.getLineCount() && measured.getLineBottom(last+1)-measured.getLineTop(firstLine)<490)last++;
            int end=measured.getLineEnd(last);
            if(end<=start)break;
            parts.add(value.substring(start,end));start=end;firstLine=last+1;
        }
        if(start<value.length())parts.add(value.substring(start));
        if(parts.isEmpty())parts.add(value);
        return parts;
    }
    static String xlsxSheet(JSONArray original,boolean profile) {
        double[] widths=widths(original,profile);int count=widths.length;
        // Excel caps row height at 409 points. Continue exceptionally long cells
        // in additional rows so text is retained and remains visible on opening.
        JSONArray rows=new JSONArray();
        for(int r=0;r<original.length();r++){
            JSONArray row=original.optJSONArray(r);if(row==null)continue;
            boolean total=!profile&&summary(row),isVisit=!profile&&visit(row);
            List<List<String>> pieces=new ArrayList<>();int chunks=1;
            for(int c=0;c<row.length();c++){
                double width=widths[c];
                if(total&&(c==3||c==7)){width=0;for(int j=c==3?0:7;j<(c==3?7:count);j++)width+=widths[j];}
                if(isVisit&&(c==2||c==3)){width=0;for(int j=c==2?0:3;j<(c==2?3:count);j++)width+=widths[j];}
                List<String> parts=cellParts(display(row.opt(c)),(float)(width*7+5));pieces.add(parts);
                chunks=Math.max(chunks,parts.size());
            }
            for(int part=0;part<chunks;part++){
                JSONArray copy=new JSONArray();
                for(int c=0;c<row.length();c++){
                    Object raw=row.opt(c);String value=text(raw);
                    if(raw instanceof Number)copy.put(part==0?raw:"");
                    else if(value.startsWith("LINK|")&&part==0){String[] link=value.split("\\|",3);copy.put("LINK|"+(link.length>1?link[1]:"")+"|"+pieces.get(c).get(0));}
                    else copy.put(part<pieces.get(c).size()?pieces.get(c).get(part):"");
                }
                if(part>0){
                    if(total && text(copy.opt(3)).isEmpty())copyPut(copy,3,"ادامه — "+text(row.opt(3)));
                    else if(isVisit && text(copy.opt(2)).isEmpty())copyPut(copy,2,"ادامه — "+text(row.opt(2)));
                    else if(profile && text(copy.opt(0)).isEmpty())copyPut(copy,0,"ادامه — "+text(row.opt(0)));
                    else if(!profile&&!total&&!isVisit)copyPut(copy,0,"ادامه");
                }
                rows.put(copy);
            }
        }
        StringBuilder s=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols>");
        for(int c=0;c<count;c++)s.append("<col min=\"").append(c+1).append("\" max=\"").append(c+1).append("\" width=\"").append(String.format(Locale.US,"%.2f",widths[c])).append("\" bestFit=\"1\" customWidth=\"1\"/>");
        s.append("</cols><sheetData>");List<String> merges=new ArrayList<>();TextPaint p=paint(Typeface.DEFAULT,14);
        for(int r=0;r<rows.length();r++){
            JSONArray row=rows.optJSONArray(r);boolean total=!profile&&summary(row),visit=!profile&&visit(row);double height=r==0?34:26;
            if(total){
                Object label=row.opt(3),value=row.opt(7);row=new JSONArray();for(int c=0;c<count;c++)row.put(c==0?label:c==7?value:"");
                if(count>=8)merges.add("A"+(r+1)+":G"+(r+1));if(count>8)merges.add("H"+(r+1)+":"+col(count)+(r+1));
            }else if(visit){
                Object label=row.opt(2),value=row.opt(3);row=new JSONArray();for(int c=0;c<count;c++)row.put(c==0?label:c==3?value:"");
                merges.add("A"+(r+1)+":C"+(r+1));merges.add("D"+(r+1)+":"+col(count)+(r+1));
            }
            for(int c=0;c<count;c++){
                double width=widths[c];
                if(total&&(c==0||c==7)){width=0;for(int j=c;j<(c==0?7:count);j++)width+=widths[j];}
                if(visit&&(c==0||c==3)){width=0;for(int j=c;j<(c==0?3:count);j++)width+=widths[j];}
                height=Math.max(height,layout(display(row.opt(c)),p,(float)(width*7+5)).getHeight()*.75+12);
            }
            s.append("<row r=\"").append(r+1).append("\" ht=\"").append(String.format(Locale.US,"%.1f",Math.min(409,height))).append("\" customHeight=\"1\">");
            for(int c=0;c<count;c++){
                Object raw=row.opt(c);String value=text(raw),ref=col(c+1)+(r+1);int style=r==0?1:total||visit?2:3;
                if(!profile&&!total&&!visit&&r>0&&c==4)style=value.contains("اضطراری")?4:value.contains("متوسط")?5:value.equals("کم")?6:value.equals("ندارد")?7:2;
                s.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\"");
                if(raw instanceof Number && Double.isFinite(((Number)raw).doubleValue()))s.append("><v>").append(raw).append("</v></c>");
                else if(value.startsWith("LINK|")){
                    String[] parts=value.split("\\|",3);String target=parts.length>1?parts[1]:"",label=parts.length>2?parts[2]:target;
                    // Only application-generated local relative paths may become formulas.
                    if(target.startsWith("../photos/")&&!target.contains("\n")&&!target.contains("\r")){
                        String formula="HYPERLINK(\""+target.replace("\"","\"\"")+"\",\""+label.replace("\"","\"\"")+"\")";
                        s.append(" t=\"str\"><f>").append(xml(formula)).append("</f><v>").append(xml(label)).append("</v></c>");
                    }else s.append(" t=\"inlineStr\"><is><t>").append(xml(label)).append("</t></is></c>");
                }else s.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(value)).append("</t></is></c>");
            }
            s.append("</row>");
        }
        s.append("</sheetData>");
        if(!merges.isEmpty()){s.append("<mergeCells count=\"").append(merges.size()).append("\">");for(String ref:merges)s.append("<mergeCell ref=\"").append(ref).append("\"/>");s.append("</mergeCells>");}
        return s.append("<pageMargins left=\"0.25\" right=\"0.25\" top=\"0.4\" bottom=\"0.4\" header=\"0.2\" footer=\"0.2\"/><pageSetup orientation=\"landscape\" paperSize=\"9\"/></worksheet>").toString();
    }
    private static void copyPut(JSONArray array,int index,Object value){try{array.put(index,value);}catch(Exception e){throw new IllegalArgumentException(e);}}

    static void writePdf(Context context,File out,JSONObject meta,JSONArray report,JSONArray profile,JSONArray checklist)throws Exception {
        Typeface font=Typeface.createFromAsset(context.getAssets(),"fonts/Vazirmatn.ttf");
        PdfDocument document=new PdfDocument();
        try{
            Pager pager=new Pager(document,meta,font);
            pager.table("مشخصات گزارش",report,true);
            pager.table("مشخصات پل",profile,true);
            pager.table("چک‌لیست، امتیازها و ارزیابی وضعیت",checklist,false);
            pager.finish();
            try(FileOutputStream stream=new FileOutputStream(out)){document.writeTo(stream);stream.getFD().sync();}
        }finally{document.close();}
    }
    private static final class Pager {
        final PdfDocument document;final JSONObject meta;final Typeface font;final TextPaint body,heading;final Paint box=new Paint(Paint.ANTI_ALIAS_FLAG);
        PdfDocument.Page page;Canvas canvas;int pageNumber=0;float y;String title;JSONArray header;float[] columns;
        static final int W=842,H=595,M=28;static final float BOTTOM=558;
        Pager(PdfDocument d,JSONObject m,Typeface f){document=d;meta=m;font=f;body=paint(f,8);heading=paint(Typeface.create(f,Typeface.BOLD),8);}
        void newPage(){
            finish();page=document.startPage(new PdfDocument.PageInfo.Builder(W,H,++pageNumber).create());canvas=page.getCanvas();canvas.drawColor(0xffffffff);
            TextPaint p=paint(Typeface.create(font,Typeface.BOLD),17);p.setTextAlign(Paint.Align.RIGHT);canvas.drawText(meta.optString("organization","بازرسی پل"),W-M,29,p);
            p.setTextSize(11);canvas.drawText(title,W-M,52,p);p.setTextSize(8);p.setColor(0xff486575);p.setTextAlign(Paint.Align.LEFT);canvas.drawText("Bridge Maintenance 1.6.0",M,29,p);y=68;
            if(header!=null)drawSimple(header,columns,true);
        }
        void finish(){if(page==null)return;TextPaint p=paint(font,8);p.setTextAlign(Paint.Align.RIGHT);canvas.drawText("صفحه "+pageNumber+"  •  گزارش بازرسی چشمی",W-M,H-13,p);document.finishPage(page);page=null;}
        void table(String name,JSONArray rows,boolean info){
            if(rows==null||rows.length()==0)return;title=name;
            double[] ideal=widths(rows,info);columns=new float[ideal.length];double sum=0;for(double v:ideal)sum+=v;for(int c=0;c<ideal.length;c++)columns[c]=(float)(ideal[c]/sum*(W-2*M));
            if(info&&columns.length==2){columns[0]=285;columns[1]=W-2*M-285;}
            header=rows.optJSONArray(0);newPage();
            for(int r=1;r<rows.length();r++){
                JSONArray row=rows.optJSONArray(r);if(row==null)continue;
                boolean summary=!info&&summary(row),visit=!info&&visit(row);
                if(summary||visit){JSONArray pair=new JSONArray().put(row.opt(summary?3:2)).put(row.opt(summary?7:3));drawSimple(pair,new float[]{345,W-2*M-345},true);}
                else drawSimple(row,columns,false);
            }
            finish();
        }
        void drawSimple(JSONArray row,float[] widths,boolean emphasized){
            TextPaint p=emphasized?heading:body;StaticLayout[] layouts=new StaticLayout[widths.length];int lineCount=1;float lineHeight=12;
            for(int c=0;c<widths.length;c++){layouts[c]=layout(display(row.opt(c)),p,widths[c]);lineCount=Math.max(lineCount,layouts[c].getLineCount());for(int l=0;l<layouts[c].getLineCount();l++)lineHeight=Math.max(lineHeight,layouts[c].getLineBottom(l)-layouts[c].getLineTop(l));}
            float desired=lineCount*lineHeight+12;
            if(y+desired>BOTTOM && desired<BOTTOM-120 && y>120)newPage();
            int start=0;
            while(start<lineCount){
                int capacity=(int)Math.floor((BOTTOM-y-12)/lineHeight);
                if(capacity<1){newPage();continue;}
                int lines=Math.min(capacity,lineCount-start);float height=Math.max(27,lines*lineHeight+12),x=W-M;
                for(int c=0;c<widths.length;c++){
                    x-=widths[c];box.setStyle(Paint.Style.FILL);box.setColor(emphasized?0xffe5eff3:0xffffffff);canvas.drawRect(x,y,x+widths[c],y+height,box);
                    StaticLayout layout=layouts[c];
                    if(start<layout.getLineCount()){
                        int endLine=Math.min(start+lines,layout.getLineCount())-1;
                        float textBottom=y+6+layout.getLineBottom(endLine)-layout.getLineTop(start);
                        canvas.save();canvas.clipRect(x+4,y+4,x+widths[c]-4,Math.min(y+height-4,textBottom));canvas.translate(x+6,y+6-layout.getLineTop(start));layout.draw(canvas);canvas.restore();
                    }
                    box.setStyle(Paint.Style.STROKE);box.setStrokeWidth(.4f);box.setColor(0xffc9d8df);canvas.drawRect(x,y,x+widths[c],y+height,box);
                }
                y+=height;start+=lines;if(start<lineCount)newPage();
            }
        }
    }
}
