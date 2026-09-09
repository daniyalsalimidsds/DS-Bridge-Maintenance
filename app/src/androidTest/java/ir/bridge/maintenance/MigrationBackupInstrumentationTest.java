package ir.bridge.maintenance;

import static org.junit.Assert.*;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@RunWith(AndroidJUnit4.class)
public class MigrationBackupInstrumentationTest {
 @Test public void v1DatabaseUpgradesWithoutLosingLegacyEntity() throws Exception {
  Context c=ApplicationProvider.getApplicationContext();c.deleteDatabase(AppDb.DB_NAME);File dbFile=c.getDatabasePath(AppDb.DB_NAME);assertTrue(dbFile.getParentFile().exists()||dbFile.getParentFile().mkdirs());
  SQLiteDatabase raw=SQLiteDatabase.openOrCreateDatabase(dbFile,null);raw.execSQL("CREATE TABLE entities (kind TEXT NOT NULL, eid TEXT NOT NULL, json TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(kind,eid))");ContentValues v=new ContentValues();v.put("kind","inspections");v.put("eid","legacy-1");v.put("json",new JSONObject().put("id","legacy-1").put("status","پیش‌نویس").toString());v.put("updated_at",1L);raw.insertOrThrow("entities",null,v);raw.setVersion(1);raw.close();
  try(AppDb db=new AppDb(c)){SQLiteDatabase upgraded=db.getWritableDatabase();assertEquals(2,upgraded.getVersion());try(Cursor cur=upgraded.rawQuery("SELECT json FROM entities WHERE kind='inspections' AND eid='legacy-1'",null)){assertTrue(cur.moveToFirst());assertTrue(cur.getString(0).contains("legacy-1"));}try(Cursor cur=upgraded.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='media'",null)){assertTrue(cur.moveToFirst());}}
 }
 @Test public void legacyBase64PhotoMigrationIsIdempotent() throws Exception {
  Context c=ApplicationProvider.getApplicationContext();c.deleteDatabase(AppDb.DB_NAME);try(AppDb db=new AppDb(c)){AppMediaStore media=new AppMediaStore(c,db);String png="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";JSONObject photo=new JSONObject().put("name","عرض خط.jpg").put("data","data:image/png;base64,"+png);JSONObject entity=new JSONObject().put("id","legacy-photo").put("jdate","۱۴۰۵/۰۵/۲۰").put("photos",new JSONArray().put(photo));db.save("inspections","legacy-photo",entity.toString());InlinePhotoMigrator m=new InlinePhotoMigrator(db,media);InlinePhotoMigrator.MigrationResult first=m.run();assertEquals(1,first.photosMoved);JSONObject migrated=new JSONObject(new JSONArray(db.list("inspections")).getJSONObject(0).toString());JSONObject p=migrated.getJSONArray("photos").getJSONObject(0);assertFalse(p.has("data"));assertTrue(p.has("mediaId"));assertTrue(p.has("thumbnailUrl"));assertNotNull(media.resolveMedia(p.getString("mediaId")));File thumb=media.resolveThumbnail(p.getString("mediaId"));assertNotNull(thumb);assertTrue(thumb.isFile()&&thumb.length()>0);InlinePhotoMigrator.MigrationResult second=m.run();assertEquals(0,second.photosMoved);assertEquals(1,db.mediaRows().size());}
 }
 @Test public void schema3BackupDetectsEntityTampering() throws Exception {
  Context c=ApplicationProvider.getApplicationContext();c.deleteDatabase(AppDb.DB_NAME);try(AppDb db=new AppDb(c)){AppMediaStore media=new AppMediaStore(c,db);db.save("settings","main",new JSONObject().put("id","main").put("theme","night").toString());BackupManager b=new BackupManager(c,db,media);File pkg=b.createPackage();File tampered=new File(c.getCacheDir(),"tampered-backup.zip");try(ZipFile zin=new ZipFile(pkg);ZipOutputStream zout=new ZipOutputStream(new FileOutputStream(tampered))){java.util.Enumeration<? extends ZipEntry> en=zin.entries();while(en.hasMoreElements()){ZipEntry e=en.nextElement();zout.putNextEntry(new ZipEntry(e.getName()));byte[] data=readFully(zin.getInputStream(e));if("entities.json".equals(e.getName())){String x=new String(data,java.nio.charset.StandardCharsets.UTF_8).replace("\"night\"","\"day\"");data=x.getBytes(java.nio.charset.StandardCharsets.UTF_8);}zout.write(data);zout.closeEntry();}}boolean rejected=false;try{b.inspect(Uri.fromFile(tampered));}catch(SecurityException expected){rejected=true;}assertTrue("schema 3 entity checksum tampering must be rejected",rejected);}
 }
 @Test public void completeBackupRoundTripsValidationAndZipSlipIsRejected() throws Exception {
  Context c=ApplicationProvider.getApplicationContext();c.deleteDatabase(AppDb.DB_NAME);try(AppDb db=new AppDb(c)){AppMediaStore media=new AppMediaStore(c,db);db.save("settings","main",new JSONObject().put("id","main").put("theme","day").toString());BackupManager b=new BackupManager(c,db,media);File pkg=b.createPackage();BackupManager.BackupSummary s=b.inspect(Uri.fromFile(pkg));assertEquals("zip",s.format);assertEquals(3,s.schema);assertTrue(s.integrityVerified);assertTrue(s.totalEntities>=1);File evil=new File(c.getCacheDir(),"evil.zip");File escaped=new File(c.getCacheDir().getParentFile(),"evil.txt");escaped.delete();try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(evil))){z.putNextEntry(new ZipEntry("../evil.txt"));z.write("x".getBytes());z.closeEntry();}boolean rejected=false;try{b.inspect(Uri.fromFile(evil));}catch(ZipException|SecurityException|IllegalArgumentException expected){rejected=true;}assertTrue("ZIP path traversal must be rejected by Android or app validator",rejected);assertFalse("ZIP traversal must never create an escaped file",escaped.exists());}
 }
 @Test public void transactionalDeleteRemovesOwnedMediaRowsAndFiles() throws Exception {
  Context c=ApplicationProvider.getApplicationContext();c.deleteDatabase(AppDb.DB_NAME);try(AppDb db=new AppDb(c)){AppMediaStore media=new AppMediaStore(c,db);String png="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";db.save("inspections","delete-owner",new JSONObject().put("id","delete-owner").toString());JSONObject saved=media.saveDataUri(png,"delete-test.png","۱۴۰۵/۰۶/۰۱","inspection","delete-owner");File original=media.resolveMedia(saved.getString("mediaId")),thumbnail=media.resolveThumbnail(saved.getString("mediaId"));assertNotNull(original);assertNotNull(thumbnail);AppDb.DeleteResult result=db.deleteMany(new JSONArray().put(new JSONObject().put("kind","inspections").put("id","delete-owner")));assertEquals(1,result.entitiesDeleted);assertEquals(1,result.media.size());assertEquals(1,media.deleteFiles(result.media));assertFalse(original.exists());assertFalse(thumbnail.exists());assertEquals(0,db.mediaRows().size());assertEquals(0,new JSONArray(db.list("inspections")).length());}
 }
 private static byte[] readFully(InputStream input)throws Exception{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}}
}
