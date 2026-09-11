package ir.bridge.maintenance;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Native append-only, hash-chained audit storage. */
final class AuditLog {
    private AuditLog() {}

    static void createTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS audit_events (seq INTEGER PRIMARY KEY AUTOINCREMENT, event_id TEXT NOT NULL UNIQUE, actor_id TEXT NOT NULL, actor_name TEXT NOT NULL, action TEXT NOT NULL, entity_kind TEXT NOT NULL, entity_id TEXT NOT NULL, reason TEXT NOT NULL, detail TEXT NOT NULL, revision TEXT NOT NULL, occurred_at INTEGER NOT NULL, previous_hash TEXT NOT NULL, event_hash TEXT NOT NULL UNIQUE, json TEXT NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_events(entity_kind,entity_id,seq)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_audit_time ON audit_events(occurred_at DESC,seq DESC)");
        createImmutabilityTriggers(db);
    }

    private static void createImmutabilityTriggers(SQLiteDatabase db) {
        db.execSQL("CREATE TRIGGER IF NOT EXISTS audit_events_no_update BEFORE UPDATE ON audit_events BEGIN SELECT RAISE(ABORT,'audit-events-append-only'); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS audit_events_no_delete BEFORE DELETE ON audit_events BEGIN SELECT RAISE(ABORT,'audit-events-append-only'); END");
    }

    /** The only controlled exception to append-only storage is an atomic, verified full restore. */
    static void clearForVerifiedRestore(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS audit_events_no_update");
        db.execSQL("DROP TRIGGER IF EXISTS audit_events_no_delete");
        db.delete("audit_events", null, null);
        createImmutabilityTriggers(db);
    }

    static JSONObject append(SQLiteDatabase db, JSONObject source) throws Exception {
        if (db == null || source == null) throw new IllegalArgumentException("Missing audit event");
        String eventId = bounded(source.optString("id", source.optString("eventId", "")), 180);
        String actorId = bounded(source.optString("actorId", ""), 180);
        String actorName = bounded(source.optString("actorName", source.optString("user", actorId.isEmpty() ? "سیستم" : actorId)), 120);
        String action = bounded(source.optString("action", ""), 160);
        String entityKind = bounded(source.optString("kind", source.optString("entityKind", "")), 80);
        String entityId = bounded(source.optString("eid", source.optString("entityId", "")), 180);
        String reason = bounded(source.optString("reason", ""), 1200);
        String detail = bounded(source.optString("detail", ""), 2400);
        String revision = bounded(source.optString("revision", ""), 120);
        long occurredAt = source.optLong("at", source.optLong("occurredAt", System.currentTimeMillis()));
        if (!safeId(eventId) || action.isEmpty() || !safeKind(entityKind) || !safeId(entityId) || occurredAt <= 0)
            throw new IllegalArgumentException("Invalid audit event");

        String previousHash = "";
        try (Cursor cursor = db.rawQuery("SELECT event_hash FROM audit_events ORDER BY seq DESC LIMIT 1", null)) {
            if (cursor.moveToFirst()) previousHash = cursor.getString(0);
        }
        String material = previousHash + "\n" + eventId + "\n" + actorId + "\n" + actorName + "\n"
                + action + "\n" + entityKind + "\n" + entityId + "\n" + reason + "\n"
                + detail + "\n" + revision + "\n" + occurredAt;
        String eventHash = sha256(material);
        JSONObject event = new JSONObject()
                .put("id", eventId)
                .put("actorId", actorId)
                .put("actorName", actorName)
                .put("user", actorName)
                .put("action", action)
                .put("kind", entityKind)
                .put("eid", entityId)
                .put("reason", reason)
                .put("detail", detail)
                .put("revision", revision)
                .put("at", occurredAt)
                .put("occurredAt", occurredAt)
                .put("previousHash", previousHash)
                .put("eventHash", eventHash)
                .put("appendOnly", true)
                .put("schemaVersion", 1);

        ContentValues values = new ContentValues();
        values.put("event_id", eventId);
        values.put("actor_id", actorId);
        values.put("actor_name", actorName);
        values.put("action", action);
        values.put("entity_kind", entityKind);
        values.put("entity_id", entityId);
        values.put("reason", reason);
        values.put("detail", detail);
        values.put("revision", revision);
        values.put("occurred_at", occurredAt);
        values.put("previous_hash", previousHash);
        values.put("event_hash", eventHash);
        values.put("json", event.toString());
        db.insertOrThrow("audit_events", null, values);
        return event;
    }

    static JSONArray list(SQLiteDatabase db) {
        JSONArray result = new JSONArray();
        try (Cursor cursor = db.rawQuery("SELECT json FROM audit_events ORDER BY seq DESC", null)) {
            while (cursor.moveToNext()) {
                try { result.put(new JSONObject(cursor.getString(0))); }
                catch (Exception ignored) { }
            }
        }
        return result;
    }

    static JSONObject find(SQLiteDatabase db, String eventId) {
        try (Cursor cursor = db.query("audit_events", new String[]{"json"}, "event_id=?", new String[]{eventId}, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            return new JSONObject(cursor.getString(0));
        } catch (Exception error) { return null; }
    }

    static void restore(SQLiteDatabase db, JSONArray rows) throws Exception {
        for (int index = rows.length() - 1; index >= 0; index--) {
            JSONObject event = rows.optJSONObject(index);
            if (event == null) throw new IllegalArgumentException("Audit row is not an object");
            String expectedPrevious = event.optString("previousHash", "");
            String expectedHash = event.optString("eventHash", "");
            JSONObject restored = append(db, event);
            if (!expectedPrevious.isEmpty() && !expectedPrevious.equals(restored.optString("previousHash")))
                throw new SecurityException("Audit chain predecessor mismatch");
            if (!expectedHash.isEmpty() && !expectedHash.equals(restored.optString("eventHash")))
                throw new SecurityException("Audit chain hash mismatch");
        }
        if (!verify(db)) throw new SecurityException("Audit chain verification failed");
    }

    static boolean verify(SQLiteDatabase db) {
        String previous = "";
        try (Cursor cursor = db.rawQuery("SELECT event_id,actor_id,actor_name,action,entity_kind,entity_id,reason,detail,revision,occurred_at,previous_hash,event_hash FROM audit_events ORDER BY seq ASC", null)) {
            while (cursor.moveToNext()) {
                String material = previous + "\n" + cursor.getString(0) + "\n" + cursor.getString(1) + "\n"
                        + cursor.getString(2) + "\n" + cursor.getString(3) + "\n" + cursor.getString(4)
                        + "\n" + cursor.getString(5) + "\n" + cursor.getString(6) + "\n"
                        + cursor.getString(7) + "\n" + cursor.getString(8) + "\n" + cursor.getLong(9);
                if (!previous.equals(cursor.getString(10)) || !sha256(material).equals(cursor.getString(11))) return false;
                previous = cursor.getString(11);
            }
            return true;
        } catch (Exception error) { return false; }
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(64);
        for (byte item : digest) out.append(String.format(java.util.Locale.US, "%02x", item & 0xff));
        return out.toString();
    }

    private static boolean safeId(String value) {
        return value != null && !value.isEmpty() && value.length() <= 180 && value.indexOf('\0') < 0;
    }

    private static boolean safeKind(String value) {
        return value != null && value.matches("[A-Za-z0-9_\\-]{1,80}");
    }

    private static String bounded(String value, int length) {
        if (value == null) return "";
        return value.length() > length ? value.substring(0, length) : value;
    }
}
