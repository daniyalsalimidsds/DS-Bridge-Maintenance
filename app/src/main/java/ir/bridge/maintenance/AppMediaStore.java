package ir.bridge.maintenance;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.exifinterface.media.ExifInterface;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.List;

public final class AppMediaStore {
    public static final String APP_DIR = "BridgeMaintenance";
    private static final int THUMB_MAX = 480;
    private final Context context; private final AppDb db; private final File root,thumbRoot;
    public AppMediaStore(Context context, AppDb db) {
        this.context=context.getApplicationContext();this.db=db;
        File base=context.getExternalFilesDir(null);if(base==null)base=context.getFilesDir();
        root=new File(base,APP_DIR);new File(root,"Images").mkdirs();new File(root,"Signatures").mkdirs();new File(root,"exports").mkdirs();new File(root,"backups").mkdirs();
        thumbRoot=new File(root,"Thumbnails");thumbRoot.mkdirs();
    }
    public File root(){return root;}
    public synchronized int deleteFiles(List<AppDb.MediaRecord> records) {
        int deleted = 0;
        if (records == null) return deleted;
        for (AppDb.MediaRecord record : records) {
            if (record == null) continue;
            boolean originalGone = false, thumbnailGone = false;
            try {
                File original = new File(root, record.relativePath).getCanonicalFile();
                originalGone = !original.getPath().startsWith(root.getCanonicalPath()+File.separator)
                        || !original.exists() || original.delete();
            } catch (Exception ignored) { }
            try {
                File thumbnail = thumbFile(record.mediaId);
                thumbnailGone = !thumbnail.exists() || thumbnail.delete();
            } catch (Exception ignored) { }
            if (originalGone && thumbnailGone) deleted++;
        }
        return deleted;
    }
    public JSONObject importUri(Uri uri,String subsection,String visitDate,String ownerKind,String ownerId)throws Exception{ContentResolver cr=context.getContentResolver();String mime=cr.getType(uri);if(mime==null||!mime.startsWith("image/"))mime="image/jpeg";String ext=extensionFor(mime);try(InputStream in=cr.openInputStream(uri)){if(in==null)throw new IllegalArgumentException("Image stream unavailable");return saveStream(in,mime,ext,subsection,visitDate,ownerKind,ownerId);}}
    public File createCameraFile(String subsection,String visitDate)throws Exception{File dir=imageDateDir(visitDate);String base=MediaNaming.safeName(subsection)+"-"+MediaNaming.compactDate(visitDate);return new File(dir,uniqueName(dir,base,".jpg"));}
    public JSONObject registerCameraFile(File file,String subsection,String visitDate,String ownerKind,String ownerId)throws Exception{normalizeExifInPlace(file);return registerFile(file,"image/jpeg",ownerKind,ownerId);}
    public JSONObject saveDataUri(String dataUri,String suggestedName,String visitDate,String ownerKind,String ownerId)throws Exception{int comma=dataUri.indexOf(',');if(comma<=0||!dataUri.startsWith("data:image/"))throw new IllegalArgumentException("Unsupported data URI");String head=dataUri.substring(0,comma),mime=head.substring(5,head.indexOf(';'));byte[] bytes=android.util.Base64.decode(dataUri.substring(comma+1),android.util.Base64.DEFAULT);if(bytes.length>40_000_000)throw new IllegalArgumentException("Legacy image too large");return saveStream(new ByteArrayInputStream(bytes),mime,extensionFor(mime),MediaNaming.stripExtension(suggestedName==null?"تصویر":suggestedName),visitDate,ownerKind,ownerId);}
    public JSONObject saveSignatureDataUri(String dataUri,String signerName,String visitDate,String ownerId)throws Exception{
        int comma=dataUri==null?-1:dataUri.indexOf(',');if(comma<=0||!dataUri.startsWith("data:image/png"))throw new IllegalArgumentException("Signature must be PNG");byte[] bytes=android.util.Base64.decode(dataUri.substring(comma+1),android.util.Base64.DEFAULT);if(bytes.length==0||bytes.length>5_000_000)throw new IllegalArgumentException("Signature image size invalid");File dateDir=new File(new File(root,"Signatures"),MediaNaming.compactDate(visitDate));if(!dateDir.exists()&&!dateDir.mkdirs())throw new IllegalStateException("Cannot create signature directory");String base=MediaNaming.safeName(signerName==null?"امضا":signerName)+"-"+MediaNaming.compactDate(visitDate);File target=new File(dateDir,base+".png");if(target.exists())target=new File(dateDir,uniqueName(dateDir,base,".png"));try(FileOutputStream out=new FileOutputStream(target)){out.write(bytes);out.getFD().sync();}try{return registerFile(target,"image/png","signature",ownerId);}catch(Exception e){target.delete();throw e;}
    }
    public JSONObject saveLegacyDataUri(String dataUri,String suggestedName,String visitDate,String ownerKind,String ownerId,int sequence,String logicalSlot)throws Exception{
        int comma=dataUri.indexOf(',');if(comma<=0||!dataUri.startsWith("data:image/"))throw new IllegalArgumentException("Unsupported data URI");String head=dataUri.substring(0,comma);int semi=head.indexOf(';');if(semi<6)throw new IllegalArgumentException("Malformed data URI");String mime=head.substring(5,semi);if(!mime.startsWith("image/"))throw new IllegalArgumentException("Unsupported media type");byte[] bytes=android.util.Base64.decode(dataUri.substring(comma+1),android.util.Base64.DEFAULT);if(bytes.length==0||bytes.length>40_000_000)throw new IllegalArgumentException("Legacy image size invalid");String binaryHash=sha256(bytes),mediaId=MediaNaming.legacyMediaId(ownerKind,ownerId,logicalSlot,binaryHash);AppDb.MediaRecord existing=db.mediaById(mediaId);if(existing!=null){File f=resolveMedia(mediaId);if(f!=null&&binaryHash.equalsIgnoreCase(sha256(f)))return mediaJson(existing);}String ext=extensionFor(mime),subsection=MediaNaming.stripExtension(suggestedName==null?"تصویر":suggestedName);File dir=imageDateDir(visitDate);String base=MediaNaming.safeName(subsection)+"-"+MediaNaming.compactDate(visitDate);int seq=Math.max(1,sequence);File target;while(true){target=new File(dir,base+"-"+seq+"."+ext.toLowerCase(Locale.ROOT));if(!target.exists())break;if(binaryHash.equalsIgnoreCase(sha256(target)))break;seq++;if(seq>10000)throw new IllegalStateException("Cannot allocate legacy image filename");}if(!target.exists()){try(FileOutputStream out=new FileOutputStream(target)){out.write(bytes);out.getFD().sync();}}BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(target.getAbsolutePath(),opts);if(opts.outWidth<=0||opts.outHeight<=0){target.delete();throw new IllegalArgumentException("Legacy image decode validation failed");}AppDb.MediaRecord m=new AppDb.MediaRecord();m.mediaId=mediaId;m.ownerKind=ownerKind;m.ownerId=ownerId;m.fileName=target.getName();m.relativePath=relative(target);m.mime=mime;m.width=opts.outWidth;m.height=opts.outHeight;m.sha256=binaryHash;m.createdAt=System.currentTimeMillis();db.saveMedia(m);return mediaJson(m);
    }
    private JSONObject saveStream(InputStream input,String mime,String ext,String subsection,String visitDate,String ownerKind,String ownerId)throws Exception{
        File dir=imageDateDir(visitDate);String base=MediaNaming.safeName(subsection)+"-"+MediaNaming.compactDate(visitDate);File target=new File(dir,uniqueName(dir,base,"."+ext));
        try{try(FileOutputStream out=new FileOutputStream(target)){byte[] buf=new byte[64*1024];int n;long total=0;while((n=input.read(buf))!=-1){total+=n;if(total>40_000_000L)throw new IllegalArgumentException("Image exceeds 40 MB limit");out.write(buf,0,n);}out.getFD().sync();}normalizeExifInPlace(target);return registerFile(target,mime,ownerKind,ownerId);}catch(Exception e){target.delete();throw e;}
    }
    private JSONObject registerFile(File target,String mime,String ownerKind,String ownerId)throws Exception{
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(target.getAbsolutePath(),opts);if(opts.outWidth<=0||opts.outHeight<=0)throw new IllegalArgumentException("Image decode validation failed");String id="m-"+UUID.randomUUID(),rel=relative(target);AppDb.MediaRecord m=new AppDb.MediaRecord();m.mediaId=id;m.ownerKind=ownerKind;m.ownerId=ownerId;m.fileName=target.getName();m.relativePath=rel;m.mime=mime;m.width=opts.outWidth;m.height=opts.outHeight;m.sha256=sha256(target);m.createdAt=System.currentTimeMillis();db.saveMedia(m);return mediaJson(m);
    }
    private JSONObject mediaJson(AppDb.MediaRecord m)throws Exception{
        ensureThumbnail(m);
        JSONObject o=new JSONObject();o.put("id",m.mediaId);o.put("mediaId",m.mediaId);o.put("name",m.fileName);o.put("url",mediaUrl(m.mediaId));o.put("thumbnailUrl",thumbnailUrl(m.mediaId));o.put("mime",m.mime);o.put("width",m.width);o.put("height",m.height);o.put("relativePath",m.relativePath);o.put("sha256",m.sha256);o.put("at",m.createdAt);o.put("ownerId",m.ownerId);o.put("ownerKind",m.ownerKind);return o;
    }
    public File resolveMedia(String mediaId){AppDb.MediaRecord m=db.mediaById(mediaId);if(m==null)return null;try{File f=new File(root,m.relativePath).getCanonicalFile();if(!f.getPath().startsWith(root.getCanonicalPath()+File.separator)||!f.isFile())return null;return f;}catch(Exception e){return null;}}
    public synchronized File resolveThumbnail(String mediaId){try{AppDb.MediaRecord m=db.mediaById(mediaId);if(m==null)return null;File original=resolveMedia(mediaId);if(original==null)return null;File thumb=thumbFile(mediaId);if(!thumb.isFile()||thumb.length()==0)ensureThumbnail(m);return thumb.isFile()&&thumb.length()>0?thumb:null;}catch(Exception e){return null;}}
    public static String mediaUrl(String mediaId){return BuildConfig.APP_ORIGIN+"/media/"+Uri.encode(mediaId);}
    public static String thumbnailUrl(String mediaId){return BuildConfig.APP_ORIGIN+"/media-thumb/"+Uri.encode(mediaId);}
    private synchronized void ensureThumbnail(AppDb.MediaRecord m)throws Exception{
        File out=thumbFile(m.mediaId);if(out.isFile()&&out.length()>0)return;File src=resolveMedia(m.mediaId);if(src==null)throw new IllegalStateException("Thumbnail source missing");
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(src.getAbsolutePath(),bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IllegalArgumentException("Thumbnail source invalid");
        int sample=1;while(Math.max(bounds.outWidth,bounds.outHeight)/sample>THUMB_MAX*2&&sample<64)sample*=2;BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=sample;Bitmap decoded=BitmapFactory.decodeFile(src.getAbsolutePath(),opts);if(decoded==null)throw new IllegalArgumentException("Thumbnail decode failed");Bitmap scaled=decoded;int max=Math.max(decoded.getWidth(),decoded.getHeight());if(max>THUMB_MAX){float ratio=THUMB_MAX/(float)max;scaled=Bitmap.createScaledBitmap(decoded,Math.max(1,Math.round(decoded.getWidth()*ratio)),Math.max(1,Math.round(decoded.getHeight()*ratio)),true);}File tmp=new File(thumbRoot,m.mediaId+".tmp");try(FileOutputStream fos=new FileOutputStream(tmp)){if(!scaled.compress(Bitmap.CompressFormat.JPEG,82,fos))throw new IllegalStateException("Thumbnail encode failed");fos.getFD().sync();}if(out.exists()&&!out.delete())throw new IllegalStateException("Cannot replace thumbnail");if(!tmp.renameTo(out)){copyFile(tmp,out);tmp.delete();}if(scaled!=decoded)scaled.recycle();decoded.recycle();
    }
    private File thumbFile(String mediaId)throws Exception{String safe=mediaId==null?"":mediaId.replaceAll("[^A-Za-z0-9._-]","_");if(safe.isEmpty())throw new SecurityException("Unsafe media id");File f=new File(thumbRoot,safe+".jpg").getCanonicalFile();if(!f.getPath().startsWith(thumbRoot.getCanonicalPath()+File.separator))throw new SecurityException("Thumbnail path outside root");return f;}
    private static void copyFile(File src,File dest)throws Exception{try(FileInputStream in=new FileInputStream(src);FileOutputStream out=new FileOutputStream(dest)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.getFD().sync();}}
    private File imageDateDir(String visitDate){File dir=new File(new File(root,"Images"),MediaNaming.compactDate(visitDate));if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Cannot create image directory");return dir;}
    private static String uniqueName(File dir,String base,String ext){for(int i=1;i<10000;i++){String n=base+"-"+i+ext.toLowerCase(Locale.ROOT);if(!new File(dir,n).exists())return n;}return base+"-"+System.currentTimeMillis()+ext;}
    private String relative(File file)throws Exception{String rp=root.getCanonicalFile().toURI().relativize(file.getCanonicalFile().toURI()).getPath();if(rp.startsWith("../")||rp.isEmpty())throw new SecurityException("Media path outside root");return Uri.decode(rp);}
    private static String extensionFor(String mime){String ext=MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);return ext==null?"jpg":ext;}
    private static String sha256(byte[] bytes)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");md.update(bytes);StringBuilder b=new StringBuilder();for(byte x:md.digest())b.append(String.format(Locale.ROOT,"%02x",x));return b.toString();}
    private static String sha256(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(FileInputStream in=new FileInputStream(f)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)md.update(buf,0,n);}StringBuilder b=new StringBuilder();for(byte x:md.digest())b.append(String.format(Locale.ROOT,"%02x",x));return b.toString();}
    private static void normalizeExifInPlace(File f){try{ExifInterface exif=new ExifInterface(f);int o=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL),deg=o==ExifInterface.ORIENTATION_ROTATE_90?90:o==ExifInterface.ORIENTATION_ROTATE_180?180:o==ExifInterface.ORIENTATION_ROTATE_270?270:0;if(deg==0)return;BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getAbsolutePath(),bounds);int sample=1,max=Math.max(bounds.outWidth,bounds.outHeight);while(max/sample>4096&&sample<16)sample*=2;BitmapFactory.Options decode=new BitmapFactory.Options();decode.inSampleSize=sample;Bitmap src=BitmapFactory.decodeFile(f.getAbsolutePath(),decode);if(src==null)return;Matrix m=new Matrix();m.postRotate(deg);Bitmap rot=Bitmap.createBitmap(src,0,0,src.getWidth(),src.getHeight(),m,true);try(FileOutputStream out=new FileOutputStream(f,false)){rot.compress(Bitmap.CompressFormat.JPEG,92,out);out.getFD().sync();}if(rot!=src)rot.recycle();src.recycle();}catch(Exception ignored){}}
}
