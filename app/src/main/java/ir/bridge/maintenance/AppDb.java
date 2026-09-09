package ir.bridge.maintenance;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class AppDb extends SQLiteOpenHelper {
    public static final String DB_NAME = "bridge_maintenance.db";
    public static final int DB_VERSION = 2;

    public AppDb(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) { createV1(db); createV2(db); }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            if (oldVersion < 1) createV1(db);
            if (oldVersion < 2) createV2(db);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private static void createV1(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS entities (kind TEXT NOT NULL, eid TEXT NOT NULL, json TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(kind,eid))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entities_kind_updated ON entities(kind, updated_at DESC)");
    }

    private static void createV2(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS media (media_id TEXT PRIMARY KEY, owner_kind TEXT, owner_id TEXT, file_name TEXT NOT NULL, relative_path TEXT NOT NULL UNIQUE, mime TEXT NOT NULL, width INTEGER, height INTEGER, sha256 TEXT, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_owner ON media(owner_kind, owner_id, created_at)");
        db.execSQL("CREATE TABLE IF NOT EXISTS migration_state (mkey TEXT PRIMARY KEY, mvalue TEXT NOT NULL, updated_at INTEGER NOT NULL)");
    }

    public synchronized String list(String kind) {
        JSONArray out = new JSONArray();
        try (Cursor c = getReadableDatabase().query("entities", new String[]{"json"}, "kind=?", new String[]{kind}, null, null, "updated_at DESC")) {
            while (c.moveToNext()) { try { out.put(new JSONObject(c.getString(0))); } catch (JSONException ignored) { } }
        }
        return out.toString();
    }

    public synchronized boolean save(String kind, String id, String json) {
        ContentValues v = new ContentValues(); v.put("kind", kind); v.put("eid", id); v.put("json", json); v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().insertWithOnConflict("entities", null, v, SQLiteDatabase.CONFLICT_REPLACE) != -1;
    }

    public synchronized boolean delete(String kind, String id) { return getWritableDatabase().delete("entities", "kind=? AND eid=?", new String[]{kind, id}) > 0; }

    public synchronized DeleteResult deleteMany(JSONArray entries) {
        if (entries == null || entries.length() > 5000) throw new IllegalArgumentException("Invalid delete batch");
        SQLiteDatabase sql = getWritableDatabase(); int deleted = 0; Set<String> ownerIds = new LinkedHashSet<>(); Map<String,MediaRecord> media = new LinkedHashMap<>(); sql.beginTransaction();
        try {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i); if (entry == null) throw new IllegalArgumentException("Invalid delete entry");
                String kind = entry.optString("kind", ""), id = entry.optString("id", "");
                if (!kind.matches("[A-Za-z0-9_\\-]{1,80}") || id.isEmpty() || id.length() > 180 || id.indexOf('\0') >= 0) throw new SecurityException("Unsafe delete entry");
                ownerIds.add(id);
                deleted += sql.delete("entities", "kind=? AND eid=?", new String[]{kind, id});
            }
            for (String ownerId : ownerIds) {
                try (Cursor c = sql.query("media", null, "owner_id=?", new String[]{ownerId}, null, null, null)) {
                    while (c.moveToNext()) { MediaRecord row = MediaRecord.fromCursor(c); media.put(row.mediaId, row); }
                }
                sql.delete("media", "owner_id=?", new String[]{ownerId});
            }
            sql.setTransactionSuccessful(); return new DeleteResult(deleted, new ArrayList<>(media.values()));
        } finally { sql.endTransaction(); }
    }

    public synchronized String snapshotJson() {
        JSONObject kinds = new JSONObject();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT kind,json FROM entities ORDER BY kind,updated_at DESC", null)) {
            while (c.moveToNext()) {
                String kind = c.getString(0); JSONArray a = kinds.optJSONArray(kind);
                if (a == null) { a = new JSONArray(); try { kinds.put(kind, a); } catch (JSONException ignored) { } }
                try { a.put(new JSONObject(c.getString(1))); } catch (JSONException ignored) { }
            }
        }
        return kinds.toString();
    }

    public synchronized List<EntityRow> entityRows() {
        List<EntityRow> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT kind,eid,json FROM entities ORDER BY kind,eid", null)) { while (c.moveToNext()) rows.add(new EntityRow(c.getString(0), c.getString(1), c.getString(2))); }
        return rows;
    }

    public synchronized void replaceEntityJson(String kind, String id, String json) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try { ContentValues v = new ContentValues(); v.put("json", json); v.put("updated_at", System.currentTimeMillis()); db.update("entities", v, "kind=? AND eid=?", new String[]{kind, id}); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }

    public synchronized void saveMedia(MediaRecord m) { getWritableDatabase().insertWithOnConflict("media", null, mediaValues(m), SQLiteDatabase.CONFLICT_REPLACE); }
    public synchronized MediaRecord mediaById(String mediaId) {
        try (Cursor c = getReadableDatabase().query("media", null, "media_id=?", new String[]{mediaId}, null, null, null)) { if (!c.moveToFirst()) return null; return MediaRecord.fromCursor(c); }
    }
    public synchronized List<MediaRecord> mediaRows() {
        List<MediaRecord> rows = new ArrayList<>(); try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM media ORDER BY created_at,media_id", null)) { while (c.moveToNext()) rows.add(MediaRecord.fromCursor(c)); } return rows;
    }

    public synchronized void restoreSnapshot(JSONObject snapshot, List<MediaRecord> restoredMedia) throws Exception {
        if (snapshot == null) throw new IllegalArgumentException("Missing snapshot"); SQLiteDatabase sql = getWritableDatabase(); sql.beginTransaction();
        try {
            sql.delete("entities", null, null); sql.delete("media", null, null); Iterator<String> kinds = snapshot.keys();
            while (kinds.hasNext()) {
                String kind = kinds.next(); if (!kind.matches("[A-Za-z0-9_\\-]{1,80}")) throw new SecurityException("Unsafe entity kind"); JSONArray rows = snapshot.optJSONArray(kind); if (rows == null) throw new IllegalArgumentException("Entity kind is not an array: " + kind);
                for (int i=0;i<rows.length();i++) { JSONObject obj = rows.optJSONObject(i); if (obj == null) throw new IllegalArgumentException("Entity row is not an object"); String id = obj.optString("id",""); if (id.isEmpty() || id.length() > 180 || id.indexOf('\0') >= 0) throw new SecurityException("Unsafe entity id"); ContentValues v = new ContentValues(); v.put("kind",kind); v.put("eid",id); v.put("json",obj.toString()); v.put("updated_at", obj.optLong("updatedAt", System.currentTimeMillis())); sql.insertOrThrow("entities",null,v); }
            }
            if (restoredMedia != null) for (MediaRecord m : restoredMedia) sql.insertOrThrow("media",null,mediaValues(m)); sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
    }

    private static ContentValues mediaValues(MediaRecord m) {
        ContentValues v = new ContentValues(); v.put("media_id", m.mediaId); v.put("owner_kind", m.ownerKind); v.put("owner_id", m.ownerId); v.put("file_name", m.fileName); v.put("relative_path", m.relativePath); v.put("mime", m.mime); v.put("width", m.width); v.put("height", m.height); v.put("sha256", m.sha256); v.put("created_at", m.createdAt); return v;
    }

    public synchronized String migrationState(String key) { try (Cursor c = getReadableDatabase().query("migration_state", new String[]{"mvalue"}, "mkey=?", new String[]{key}, null, null, null)) { return c.moveToFirst() ? c.getString(0) : null; } }
    public synchronized void setMigrationState(String key, String value) { ContentValues v = new ContentValues(); v.put("mkey", key); v.put("mvalue", value); v.put("updated_at", System.currentTimeMillis()); getWritableDatabase().insertWithOnConflict("migration_state", null, v, SQLiteDatabase.CONFLICT_REPLACE); }

    public static final class EntityRow { public final String kind, id, json; EntityRow(String kind, String id, String json) { this.kind = kind; this.id = id; this.json = json; } }
    public static final class DeleteResult {
        public final int entitiesDeleted; public final List<MediaRecord> media;
        DeleteResult(int entitiesDeleted,List<MediaRecord> media){this.entitiesDeleted=entitiesDeleted;this.media=media;}
    }
    public static final class MediaRecord {
        public String mediaId, ownerKind, ownerId, fileName, relativePath, mime, sha256; public int width, height; public long createdAt;
        static MediaRecord fromCursor(Cursor c) { MediaRecord m = new MediaRecord(); m.mediaId = c.getString(c.getColumnIndexOrThrow("media_id")); m.ownerKind = c.getString(c.getColumnIndexOrThrow("owner_kind")); m.ownerId = c.getString(c.getColumnIndexOrThrow("owner_id")); m.fileName = c.getString(c.getColumnIndexOrThrow("file_name")); m.relativePath = c.getString(c.getColumnIndexOrThrow("relative_path")); m.mime = c.getString(c.getColumnIndexOrThrow("mime")); m.width = c.getInt(c.getColumnIndexOrThrow("width")); m.height = c.getInt(c.getColumnIndexOrThrow("height")); m.sha256 = c.getString(c.getColumnIndexOrThrow("sha256")); m.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")); return m; }
    }
}
