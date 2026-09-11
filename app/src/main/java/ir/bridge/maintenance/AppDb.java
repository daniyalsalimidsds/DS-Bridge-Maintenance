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
    public static final int DB_VERSION = 3;

    public AppDb(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        createV1(db);
        createV2(db);
        createV3(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            if (oldVersion < 1) createV1(db);
            if (oldVersion < 2) createV2(db);
            if (oldVersion < 3) createV3(db);
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

    private static void createV3(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS credentials (user_id TEXT PRIMARY KEY, salt BLOB NOT NULL, verifier BLOB NOT NULL, iterations INTEGER NOT NULL, failed_attempts INTEGER NOT NULL DEFAULT 0, locked_until INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS report_sequences (day_key TEXT PRIMARY KEY, next_value INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS report_registry (inspection_id TEXT NOT NULL UNIQUE, report_no TEXT NOT NULL UNIQUE, day_key TEXT NOT NULL, sequence_value INTEGER NOT NULL, issued_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_report_registry_issued ON report_registry(issued_at DESC)");
        AuditLog.createTable(db);
        migrateLegacyAudit(db);
        migrateLegacySeverities(db);
        migrateLegacyReports(db);
        ensureBootstrapUser(db);
    }

    private static void migrateLegacySeverities(SQLiteDatabase db) {
        try (Cursor c = db.query("entities", new String[]{"kind", "eid", "json", "updated_at"},
                "kind IN ('defects','inspections')", null, null, null, "kind,eid")) {
            while (c.moveToNext()) {
                try {
                    String kind = c.getString(0), id = c.getString(1);
                    JSONObject record = new JSONObject(c.getString(2));
                    boolean changed = false;
                    if ("defects".equals(kind)) changed = migrateSeverityField(record, "severityId", "severity");
                    if ("inspections".equals(kind)) {
                        JSONArray items = record.optJSONArray("items");
                        if (items != null) for (int index = 0; index < items.length(); index++) {
                            JSONObject item = items.optJSONObject(index);
                            if (item != null) changed = migrateSeverityField(item, "statusId", "status") || changed;
                        }
                        Object overall = record.has("overall") ? record.opt("overall") : null;
                        if (overall != null) {
                            String raw = String.valueOf(overall);
                            String normalized = normalizedSeverity(raw);
                            if (!normalized.equals(raw)) {
                                record.put("overall", normalized);
                                if ("unknown".equals(normalized)) {
                                    record.put("legacyOverallValue", raw);
                                    record.put("needsSeverityReview", true);
                                }
                                changed = true;
                            }
                        }
                    }
                    if (changed) {
                        record.put("severityMigrationVersion", "1.7.0-failsafe-v1");
                        ContentValues values = new ContentValues();
                        values.put("json", record.toString());
                        values.put("updated_at", c.getLong(3));
                        db.update("entities", values, "kind=? AND eid=?", new String[]{kind, id});
                    }
                } catch (Exception error) {
                    throw new IllegalStateException("Cannot migrate severity safely", error);
                }
            }
        }
    }

    private static boolean migrateSeverityField(JSONObject record, String canonical, String legacy) throws Exception {
        if (!record.has(canonical) && !record.has(legacy)) return false;
        String raw = record.optString(canonical, record.optString(legacy, ""));
        String normalized = normalizedSeverity(raw);
        boolean changed = !normalized.equals(raw);
        record.put(canonical, normalized);
        if ("unknown".equals(normalized)) {
            if (!record.has("legacySeverityValue")) record.put("legacySeverityValue", raw);
            record.put("needsSeverityReview", true);
            changed = true;
        }
        if (changed) record.put("severityMigrationVersion", "1.7.0-failsafe-v1");
        return changed;
    }

    private static String normalizedSeverity(String rawValue) {
        String raw = rawValue == null ? "" : rawValue.trim();
        if (raw.isEmpty() || oneOf(raw, "none", "ندارد", "normal", "عادی", "مطابق", "خارج از دامنه")) return "none";
        if (oneOf(raw, "uninspectable", "ع.ا.ب", "عدم امکان بازرسی", "عدم امکان بازرسی (ع.ا.ب)")) return "uninspectable";
        if (oneOf(raw, "low", "کم", "mild", "خفیف", "نقص جزئی")) return "low";
        if (oneOf(raw, "medium", "متوسط", "moderate", "نیازمند اقدام")) return "medium";
        if (oneOf(raw, "emergency", "اضطراری", "very_severe", "خیلی شدید", "بحرانی", "فوری/بحرانی")) return "emergency";
        return "unknown";
    }

    private static boolean oneOf(String value, String... choices) {
        for (String choice : choices) if (choice.equals(value)) return true;
        return false;
    }

    private static void ensureBootstrapUser(SQLiteDatabase db) {
        long users = android.database.DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM entities WHERE kind='users'", null);
        if (users > 0) return;
        long now = System.currentTimeMillis();
        try {
            JSONObject admin = new JSONObject()
                    .put("id", "user-admin")
                    .put("name", "مدیر سیستم")
                    .put("role", "مدیر سیستم")
                    .put("active", true)
                    .put("archived", false)
                    .put("qualification", new JSONObject().put("status", "needs_review"));
            upsertEntity(db, "users", admin);
            JSONObject settings = new JSONObject()
                    .put("id", "main")
                    .put("activeUser", "user-admin")
                    .put("orgName", "شرکت مهندسین مشاور هگزا")
                    .put("theme", "day")
                    .put("updatedAt", now);
            upsertEntity(db, "settings", settings);
        } catch (JSONException error) {
            throw new IllegalStateException("Cannot create bootstrap administrator", error);
        }
    }

    private static void migrateLegacyAudit(SQLiteDatabase db) {
        List<JSONObject> rows = new ArrayList<>();
        try (Cursor c = db.query("entities", new String[]{"json"}, "kind='audit'", null, null, null, "updated_at ASC,eid ASC")) {
            while (c.moveToNext()) {
                try { rows.add(new JSONObject(c.getString(0))); }
                catch (JSONException ignored) { }
            }
        }
        for (JSONObject row : rows) {
            try { AuditLog.append(db, row); }
            catch (Exception error) { throw new IllegalStateException("Cannot migrate audit event", error); }
        }
        if (!rows.isEmpty()) db.delete("entities", "kind='audit'", null);
    }

    private static void migrateLegacyReports(SQLiteDatabase db) {
        Set<String> reportNumbers = new LinkedHashSet<>();
        int migratedSequence = 1, registrySequence = 1;
        try (Cursor c = db.query("entities", new String[]{"eid", "json", "updated_at"}, "kind='inspections'", null, null, null, "updated_at ASC,eid ASC")) {
            while (c.moveToNext()) {
                try {
                    String id = c.getString(0);
                    JSONObject inspection = new JSONObject(c.getString(1));
                    String workflow = inspection.optString("workflowStatus", "");
                    String status = inspection.optString("status", "");
                    if (!"approved".equals(workflow) && !"نهایی".equals(status)) continue;
                    String legacy = inspection.optString("reportNo", inspection.optString("no", "")).trim();
                    String reportNo = legacy;
                    if (reportNo.isEmpty() || reportNumbers.contains(reportNo)) {
                        reportNo = String.format(java.util.Locale.US, "B-MIG-%06d", migratedSequence++);
                        if (!legacy.isEmpty()) inspection.put("legacyReportNo", legacy);
                        inspection.put("reportNumberMigration", "v3-duplicate-or-missing");
                    }
                    while (reportNumbers.contains(reportNo)) {
                        reportNo = String.format(java.util.Locale.US, "B-MIG-%06d", migratedSequence++);
                    }
                    reportNumbers.add(reportNo);
                    inspection.put("reportNo", reportNo).put("no", reportNo);
                    inspection.put("workflowStatus", "approved");
                    inspection.put("status", "نهایی");
                    inspection.put("officialReport", true);
                    inspection.put("approvedAt", inspection.optLong("approvedAt", inspection.optLong("finalizedAt", c.getLong(2))));
                    if (inspection.optString("fieldHash", "").isEmpty()) inspection.put("identityStatus", "legacy-unverified");
                    ContentValues values = new ContentValues();
                    values.put("json", inspection.toString());
                    values.put("updated_at", inspection.optLong("updatedAt", c.getLong(2)));
                    db.update("entities", values, "kind='inspections' AND eid=?", new String[]{id});
                    ContentValues registry = new ContentValues();
                    registry.put("inspection_id", id);
                    registry.put("report_no", reportNo);
                    registry.put("day_key", "legacy");
                    registry.put("sequence_value", registrySequence++);
                    registry.put("issued_at", inspection.optLong("approvedAt", c.getLong(2)));
                    db.insertOrThrow("report_registry", null, registry);
                } catch (Exception error) {
                    throw new IllegalStateException("Cannot migrate legacy report identity", error);
                }
            }
        }
    }

    public synchronized String list(String kind) {
        if ("audit".equals(kind)) return AuditLog.list(getReadableDatabase()).toString();
        JSONArray out = new JSONArray();
        try (Cursor c = getReadableDatabase().query("entities", new String[]{"json"}, "kind=?", new String[]{kind}, null, null, "updated_at DESC")) {
            while (c.moveToNext()) { try { out.put(new JSONObject(c.getString(0))); } catch (JSONException ignored) { } }
        }
        return out.toString();
    }

    public synchronized boolean save(String kind, String id, String json) {
        if ("audit".equals(kind)) {
            try {
                JSONObject event = new JSONObject(json);
                if (!id.equals(event.optString("id", id))) return false;
                AuditLog.append(getWritableDatabase(), event);
                return true;
            } catch (Exception error) { return false; }
        }
        ContentValues v = new ContentValues(); v.put("kind", kind); v.put("eid", id); v.put("json", json); v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().insertWithOnConflict("entities", null, v, SQLiteDatabase.CONFLICT_REPLACE) != -1;
    }

    public synchronized boolean delete(String kind, String id) {
        SQLiteDatabase sql = getWritableDatabase();
        if (isProtectedFromPhysicalDelete(sql, kind, id)) return false;
        return sql.delete("entities", "kind=? AND eid=?", new String[]{kind, id}) > 0;
    }

    public synchronized DeleteResult deleteMany(JSONArray entries) {
        if (entries == null || entries.length() > 5000) throw new IllegalArgumentException("Invalid delete batch");
        List<String[]> validatedEntries = new ArrayList<>(entries.length());
        SQLiteDatabase sql = getWritableDatabase(); int deleted = 0; Map<String,MediaRecord> media = new LinkedHashMap<>(); sql.beginTransaction();
        try {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i); if (entry == null) throw new IllegalArgumentException("Invalid delete entry");
                String kind = entry.optString("kind", ""), id = entry.optString("id", "");
                if (!kind.matches("[A-Za-z0-9_\\-]{1,80}") || id.isEmpty() || id.length() > 180 || id.indexOf('\0') >= 0) throw new SecurityException("Unsafe delete entry");
                if (isProtectedFromPhysicalDelete(sql, kind, id)) throw new SecurityException("Protected record cannot be physically deleted: " + kind);
                validatedEntries.add(new String[]{kind, id});
            }
            for (String[] entry : validatedEntries) {
                String kind = entry[0], id = entry[1];
                deleted += sql.delete("entities", "kind=? AND eid=?", new String[]{kind, id});
                String singular = singularOwnerKind(kind);
                try (Cursor c = sql.query("media", null, "owner_id=? AND (owner_kind=? OR owner_kind=?)", new String[]{id, kind, singular}, null, null, null)) {
                    while (c.moveToNext()) { MediaRecord row = MediaRecord.fromCursor(c); media.put(row.mediaId, row); }
                }
                sql.delete("media", "owner_id=? AND (owner_kind=? OR owner_kind=?)", new String[]{id, kind, singular});
            }
            sql.setTransactionSuccessful(); return new DeleteResult(deleted, new ArrayList<>(media.values()));
        } finally { sql.endTransaction(); }
    }

    private static String singularOwnerKind(String kind) {
        if ("inspections".equals(kind)) return "inspection";
        if ("defects".equals(kind)) return "defect";
        if ("bridges".equals(kind)) return "bridge";
        if ("criticalFindings".equals(kind)) return "criticalFinding";
        return kind;
    }

    private static boolean isProtectedFromPhysicalDelete(SQLiteDatabase sql, String kind, String id) {
        if ("audit".equals(kind) || "reviews".equals(kind) || "criticalFindings".equals(kind)
                || "inspectionVoids".equals(kind) || "reportRevisions".equals(kind) || "users".equals(kind)
                || "inspectionPrograms".equals(kind)) return true;
        if ("defects".equals(kind)) {
            try (Cursor c = sql.query("entities", new String[]{"json"}, "kind='defects' AND eid=?", new String[]{id}, null, null, null)) {
                if (!c.moveToFirst()) return false;
                JSONObject defect = new JSONObject(c.getString(0));
                return isInspectionProtected(sql, defect.optString("inspectionId"));
            } catch (JSONException error) { return true; }
        }
        if ("bridges".equals(kind)) {
            try (Cursor c = sql.query("entities", new String[]{"kind", "json"}, "kind IN ('inspectionPrograms','inspections')", null, null, null, null)) {
                while (c.moveToNext()) {
                    String relatedKind = c.getString(0);
                    JSONObject related = new JSONObject(c.getString(1));
                    if (!id.equals(related.optString("bridgeId"))) continue;
                    if ("inspectionPrograms".equals(relatedKind) || isInspectionProtectedValue(related)) return true;
                }
                return false;
            } catch (JSONException error) { return true; }
        }
        if (!"inspections".equals(kind)) return false;
        return isInspectionProtected(sql, id);
    }

    private static boolean isInspectionProtected(SQLiteDatabase sql, String id) {
        if (id == null || id.isEmpty()) return false;
        try (Cursor c = sql.query("entities", new String[]{"json"}, "kind='inspections' AND eid=?", new String[]{id}, null, null, null)) {
            if (!c.moveToFirst()) return false;
            JSONObject value = new JSONObject(c.getString(0));
            return isInspectionProtectedValue(value);
        } catch (JSONException error) {
            return true;
        }
    }

    private static boolean isInspectionProtectedValue(JSONObject value) {
        String workflow = value.optString("workflowStatus", "");
        return value.has("submittedAt") || "submitted".equals(workflow) || "approved".equals(workflow)
                || "returned".equals(workflow) || "نهایی".equals(value.optString("status"));
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
        try { kinds.put("audit", AuditLog.list(getReadableDatabase())); }
        catch (JSONException error) { throw new IllegalStateException("Cannot serialize audit events", error); }
        return kinds.toString();
    }

    public synchronized List<EntityRow> entityRows() {
        List<EntityRow> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT kind,eid,json FROM entities ORDER BY kind,eid", null)) { while (c.moveToNext()) rows.add(new EntityRow(c.getString(0), c.getString(1), c.getString(2))); }
        return rows;
    }

    public synchronized void replaceEntityJson(String kind, String id, String json) {
        if ("audit".equals(kind)) throw new SecurityException("Audit events are append-only");
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
            sql.delete("entities", null, null); sql.delete("media", null, null); sql.delete("credentials", null, null); AuditLog.clearForVerifiedRestore(sql); sql.delete("report_registry", null, null); sql.delete("report_sequences", null, null); Iterator<String> kinds = snapshot.keys();
            while (kinds.hasNext()) {
                String kind = kinds.next(); if (!kind.matches("[A-Za-z0-9_\\-]{1,80}")) throw new SecurityException("Unsafe entity kind"); JSONArray rows = snapshot.optJSONArray(kind); if (rows == null) throw new IllegalArgumentException("Entity kind is not an array: " + kind);
                if ("audit".equals(kind)) {
                    AuditLog.restore(sql, rows);
                    continue;
                }
                for (int i=0;i<rows.length();i++) { JSONObject obj = rows.optJSONObject(i); if (obj == null) throw new IllegalArgumentException("Entity row is not an object"); String id = obj.optString("id",""); if (id.isEmpty() || id.length() > 180 || id.indexOf('\0') >= 0) throw new SecurityException("Unsafe entity id"); ContentValues v = new ContentValues(); v.put("kind",kind); v.put("eid",id); v.put("json",obj.toString()); v.put("updated_at", obj.optLong("updatedAt", System.currentTimeMillis())); sql.insertOrThrow("entities",null,v); }
            }
            migrateLegacySeverities(sql);
            migrateLegacyReports(sql);
            ensureBootstrapUser(sql);
            if (restoredMedia != null) for (MediaRecord m : restoredMedia) sql.insertOrThrow("media",null,mediaValues(m)); sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
    }

    public synchronized JSONObject find(String kind, String id) {
        if ("audit".equals(kind)) return AuditLog.find(getReadableDatabase(), id);
        try (Cursor c = getReadableDatabase().query("entities", new String[]{"json"}, "kind=? AND eid=?", new String[]{kind, id}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            try { return new JSONObject(c.getString(0)); }
            catch (JSONException error) { return null; }
        }
    }

    static void upsertEntity(SQLiteDatabase sql, String kind, JSONObject object) {
        String id = object.optString("id", "");
        if (id.isEmpty() || id.length() > 180 || id.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid entity id");
        ContentValues values = new ContentValues();
        values.put("kind", kind);
        values.put("eid", id);
        values.put("json", object.toString());
        values.put("updated_at", object.optLong("updatedAt", System.currentTimeMillis()));
        if (sql.insertWithOnConflict("entities", null, values, SQLiteDatabase.CONFLICT_REPLACE) == -1)
            throw new IllegalStateException("Entity write failed: " + kind + "/" + id);
    }

    public synchronized int credentialCount() {
        return (int) android.database.DatabaseUtils.longForQuery(getReadableDatabase(), "SELECT COUNT(*) FROM credentials", null);
    }

    public synchronized CredentialRecord credential(String userId) {
        try (Cursor c = getReadableDatabase().query("credentials", null, "user_id=?", new String[]{userId}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            return new CredentialRecord(c.getString(c.getColumnIndexOrThrow("user_id")),
                    c.getBlob(c.getColumnIndexOrThrow("salt")), c.getBlob(c.getColumnIndexOrThrow("verifier")),
                    c.getInt(c.getColumnIndexOrThrow("iterations")), c.getInt(c.getColumnIndexOrThrow("failed_attempts")),
                    c.getLong(c.getColumnIndexOrThrow("locked_until")));
        }
    }

    public synchronized void saveCredential(CredentialRecord credential) {
        ContentValues values = new ContentValues();
        values.put("user_id", credential.userId);
        values.put("salt", credential.salt);
        values.put("verifier", credential.verifier);
        values.put("iterations", credential.iterations);
        values.put("failed_attempts", credential.failedAttempts);
        values.put("locked_until", credential.lockedUntil);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("credentials", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void updateCredentialFailure(String userId, int failures, long lockedUntil) {
        ContentValues values = new ContentValues();
        values.put("failed_attempts", failures);
        values.put("locked_until", lockedUntil);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("credentials", values, "user_id=?", new String[]{userId});
    }

    public synchronized String issueReportNumber(SQLiteDatabase sql, String inspectionId, String dayKey, long issuedAt) {
        try (Cursor c = sql.query("report_registry", new String[]{"report_no"}, "inspection_id=?", new String[]{inspectionId}, null, null, null)) {
            if (c.moveToFirst()) return c.getString(0);
        }
        if (dayKey == null || !dayKey.matches("[0-9]{8}")) throw new IllegalArgumentException("Invalid report day key");
        int sequence;
        try (Cursor c = sql.query("report_sequences", new String[]{"next_value"}, "day_key=?", new String[]{dayKey}, null, null, null)) {
            if (c.moveToFirst()) sequence = c.getInt(0);
            else sequence = 1;
        }
        ContentValues next = new ContentValues();
        next.put("day_key", dayKey);
        next.put("next_value", sequence + 1);
        sql.insertWithOnConflict("report_sequences", null, next, SQLiteDatabase.CONFLICT_REPLACE);
        String reportNo = String.format(java.util.Locale.US, "B-%s-%03d", dayKey, sequence);
        ContentValues registry = new ContentValues();
        registry.put("inspection_id", inspectionId);
        registry.put("report_no", reportNo);
        registry.put("day_key", dayKey);
        registry.put("sequence_value", sequence);
        registry.put("issued_at", issuedAt);
        sql.insertOrThrow("report_registry", null, registry);
        return reportNo;
    }

    public synchronized boolean isRegisteredReport(String inspectionId, String reportNo) {
        if (inspectionId == null || reportNo == null || inspectionId.isEmpty() || reportNo.isEmpty()) return false;
        try (Cursor cursor = getReadableDatabase().query("report_registry", new String[]{"report_no"},
                "inspection_id=?", new String[]{inspectionId}, null, null, null)) {
            return cursor.moveToFirst() && reportNo.equals(cursor.getString(0));
        }
    }

    private static ContentValues mediaValues(MediaRecord m) {
        ContentValues v = new ContentValues(); v.put("media_id", m.mediaId); v.put("owner_kind", m.ownerKind); v.put("owner_id", m.ownerId); v.put("file_name", m.fileName); v.put("relative_path", m.relativePath); v.put("mime", m.mime); v.put("width", m.width); v.put("height", m.height); v.put("sha256", m.sha256); v.put("created_at", m.createdAt); return v;
    }

    public synchronized String migrationState(String key) { try (Cursor c = getReadableDatabase().query("migration_state", new String[]{"mvalue"}, "mkey=?", new String[]{key}, null, null, null)) { return c.moveToFirst() ? c.getString(0) : null; } }
    public synchronized void setMigrationState(String key, String value) { ContentValues v = new ContentValues(); v.put("mkey", key); v.put("mvalue", value); v.put("updated_at", System.currentTimeMillis()); getWritableDatabase().insertWithOnConflict("migration_state", null, v, SQLiteDatabase.CONFLICT_REPLACE); }

    public static final class EntityRow { public final String kind, id, json; EntityRow(String kind, String id, String json) { this.kind = kind; this.id = id; this.json = json; } }
    public static final class CredentialRecord {
        public final String userId;
        public final byte[] salt;
        public final byte[] verifier;
        public final int iterations;
        public final int failedAttempts;
        public final long lockedUntil;
        public CredentialRecord(String userId, byte[] salt, byte[] verifier, int iterations, int failedAttempts, long lockedUntil) {
            this.userId = userId; this.salt = salt; this.verifier = verifier; this.iterations = iterations;
            this.failedAttempts = failedAttempts; this.lockedUntil = lockedUntil;
        }
    }
    public static final class DeleteResult {
        public final int entitiesDeleted; public final List<MediaRecord> media;
        DeleteResult(int entitiesDeleted,List<MediaRecord> media){this.entitiesDeleted=entitiesDeleted;this.media=media;}
    }
    public static final class MediaRecord {
        public String mediaId, ownerKind, ownerId, fileName, relativePath, mime, sha256; public int width, height; public long createdAt;
        static MediaRecord fromCursor(Cursor c) { MediaRecord m = new MediaRecord(); m.mediaId = c.getString(c.getColumnIndexOrThrow("media_id")); m.ownerKind = c.getString(c.getColumnIndexOrThrow("owner_kind")); m.ownerId = c.getString(c.getColumnIndexOrThrow("owner_id")); m.fileName = c.getString(c.getColumnIndexOrThrow("file_name")); m.relativePath = c.getString(c.getColumnIndexOrThrow("relative_path")); m.mime = c.getString(c.getColumnIndexOrThrow("mime")); m.width = c.getInt(c.getColumnIndexOrThrow("width")); m.height = c.getInt(c.getColumnIndexOrThrow("height")); m.sha256 = c.getString(c.getColumnIndexOrThrow("sha256")); m.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")); return m; }
    }
}
