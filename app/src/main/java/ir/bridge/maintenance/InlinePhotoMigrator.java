package ir.bridge.maintenance;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Iterator;

public final class InlinePhotoMigrator {
    public static final String STATE_KEY = "legacy_base64_photo_migration_v2";
    private final AppDb db; private final AppMediaStore media;
    public InlinePhotoMigrator(AppDb db, AppMediaStore media) { this.db=db; this.media=media; }
    public MigrationResult run() {
        int entitiesChanged=0, photosMoved=0, failures=0;
        for (AppDb.EntityRow row : db.entityRows()) {
            try { JSONObject root=new JSONObject(row.json);Counter c=new Counter();String date=firstNonEmpty(root.optString("jdate"),root.optString("visitDate"),root.optString("date"));migrateObject(root,row.kind,row.id,date,"تصویر","$",c);if(c.changed){db.replaceEntityJson(row.kind,row.id,root.toString());entitiesChanged++;photosMoved+=c.count;} }
            catch(Exception e){failures++;}
        }
        db.setMigrationState(STATE_KEY,(failures==0?"complete:":"partial:"+failures+":")+System.currentTimeMillis());
        return new MigrationResult(entitiesChanged,photosMoved,failures);
    }
    private void migrateObject(JSONObject o,String ownerKind,String ownerId,String date,String inheritedName,String path,Counter c)throws Exception{
        String localName=firstNonEmpty(o.optString("name"),o.optString("title"),o.optString("subsection"),inheritedName),data=o.optString("data","");
        if(data.startsWith("data:image/")&&!looksLikeSignature(o)){JSONObject meta=media.saveLegacyDataUri(data,localName,date,ownerKind,ownerId,++c.sequence,path+".data");o.remove("data");o.put("mediaId",meta.getString("mediaId"));o.put("url",meta.getString("url"));o.put("thumbnailUrl",meta.optString("thumbnailUrl",meta.getString("url")));if(!o.has("name")||o.optString("name").isEmpty())o.put("name",meta.getString("name"));o.put("relativePath",meta.getString("relativePath"));o.put("mime",meta.getString("mime"));o.put("sha256",meta.getString("sha256"));c.changed=true;c.count++;}
        java.util.ArrayList<String> keys=new java.util.ArrayList<>();Iterator<String> it=o.keys();while(it.hasNext())keys.add(it.next());java.util.Collections.sort(keys);for(String k:keys){if("data".equals(k))continue;Object v=o.opt(k);String child=path+"."+k;if(v instanceof JSONObject)migrateObject((JSONObject)v,ownerKind,ownerId,date,localName,child,c);else if(v instanceof JSONArray)migrateArray((JSONArray)v,ownerKind,ownerId,date,localName,child,c);}
    }
    private void migrateArray(JSONArray a,String ownerKind,String ownerId,String date,String name,String path,Counter c)throws Exception{for(int i=0;i<a.length();i++){Object v=a.opt(i);String child=path+"["+i+"]";if(v instanceof JSONObject)migrateObject((JSONObject)v,ownerKind,ownerId,date,name,child,c);else if(v instanceof JSONArray)migrateArray((JSONArray)v,ownerKind,ownerId,date,name,child,c);}}
    private static boolean looksLikeSignature(JSONObject o){String t=(o.optString("type")+" "+o.optString("kind")+" "+o.optString("name")).toLowerCase();return t.contains("signature")||t.contains("امضا");}
    private static String firstNonEmpty(String...v){for(String s:v)if(s!=null&&!s.trim().isEmpty())return s.trim();return "";}
    private static final class Counter{boolean changed;int count;int sequence;}
    public static final class MigrationResult{public final int entitiesChanged,photosMoved,failures;MigrationResult(int a,int b,int c){entitiesChanged=a;photosMoved=b;failures=c;}}
}
