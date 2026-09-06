package ir.bridge.maintenance;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Complete backup/export plus staged, validated and transactional restore. */
public final class BackupManager {
    private static final long MAX_BACKUP = 700_000_000L;
    private static final long MAX_TEXT = 30_000_000L;
    private static final long MAX_MEDIA_FILE = 50_000_000L;
    private static final int MAX_ENTRIES = 20_000;
    private static final int BACKUP_SCHEMA = 3;

    private final Context context;
    private final AppDb db;
    private final AppMediaStore mediaStore;

    public BackupManager(Context context, AppDb db, AppMediaStore mediaStore) {
        this.context = context.getApplicationContext();
        this.db = db;
        this.mediaStore = mediaStore;
    }

    public File createPackage() throws Exception {
        File dir = new File(context.getCacheDir(), "backups");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create backup cache");
        File out = new File(dir, "bridge-maintenance-backup-" + System.currentTimeMillis() + ".zip");

        String entitiesText = db.snapshotJson();
        byte[] entitiesBytes = entitiesText.getBytes(StandardCharsets.UTF_8);
        Counts counts = validateEntities(new JSONObject(entitiesText));
        List<AppDb.MediaRecord> rows = db.mediaRows();
        JSONArray media = new JSONArray();
        Map<String, File> mediaFiles = new HashMap<>();
        long mediaBytes = 0L;
        Set<String> relativePaths = new HashSet<>();

        for (AppDb.MediaRecord m : rows) {
            File f = mediaStore.resolveMedia(m.mediaId);
            if (f == null || !f.isFile()) throw new IllegalStateException("Backup references missing media: " + m.mediaId);
            if (f.length() > MAX_MEDIA_FILE) throw new IllegalArgumentException("Media file too large for backup: " + m.fileName);
            String rel = PathSecurity.sanitizeRelative(m.relativePath);
            if (!relativePaths.add(rel)) throw new SecurityException("Duplicate media path in backup: " + rel);
            String actualSha = sha256(f);
            if (m.sha256 != null && !m.sha256.trim().isEmpty() && !actualSha.equalsIgnoreCase(m.sha256)) {
                throw new SecurityException("Stored media checksum mismatch: " + m.mediaId);
            }
            mediaBytes += f.length();
            if (mediaBytes > MAX_BACKUP) throw new IllegalArgumentException("Backup media too large");
            mediaFiles.put(rel, f);
            media.put(new JSONObject()
                    .put("mediaId", m.mediaId)
                    .put("ownerKind", m.ownerKind)
                    .put("ownerId", m.ownerId)
                    .put("fileName", m.fileName)
                    .put("relativePath", rel)
                    .put("mime", m.mime)
                    .put("width", m.width)
                    .put("height", m.height)
                    .put("sha256", actualSha)
                    .put("size", f.length())
                    .put("createdAt", m.createdAt));
        }

        JSONObject manifest = new JSONObject()
                .put("applicationId", BuildConfig.APPLICATION_ID)
                .put("backupSchema", BACKUP_SCHEMA)
                .put("versionName", BuildConfig.VERSION_NAME)
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("createdAt", System.currentTimeMillis())
                .put("databaseSchema", AppDb.DB_VERSION)
                .put("integrityAlgorithm", "SHA-256")
                .put("entitiesSha256", sha256(entitiesBytes))
                .put("entityCount", counts.total)
                .put("mediaFiles", rows.size())
                .put("mediaBytes", mediaBytes)
                .put("media", media);

        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out))) {
            putText(z, "manifest.json", manifest.toString(2));
            z.putNextEntry(new ZipEntry("entities.json"));
            z.write(entitiesBytes);
            z.closeEntry();
            byte[] buf = new byte[65536];
            for (int i = 0; i < media.length(); i++) {
                JSONObject x = media.getJSONObject(i);
                String rel = x.getString("relativePath");
                File f = mediaFiles.get(rel);
                if (f == null) throw new IllegalStateException("Missing prepared media: " + rel);
                z.putNextEntry(new ZipEntry("media/" + rel));
                try (FileInputStream in = new FileInputStream(f)) {
                    int n;
                    while ((n = in.read(buf)) != -1) z.write(buf, 0, n);
                }
                z.closeEntry();
            }
        }
        return out;
    }

    public BackupSummary inspect(Uri uri) throws Exception {
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IllegalArgumentException("Backup stream unavailable");
            BufferedInputStream in = new BufferedInputStream(raw);
            in.mark(8);
            int a = in.read(), b = in.read();
            in.reset();
            return a == 'P' && b == 'K' ? inspectZip(in) : inspectLegacyJson(in);
        }
    }

    public RestoreResult restore(Uri uri) throws Exception {
        BackupSummary summary = inspect(uri);
        File safety = createPackage();
        return summary.format.equals("zip") ? restoreZip(uri, summary, safety) : restoreLegacyJson(uri, summary, safety);
    }

    private BackupSummary inspectLegacyJson(InputStream in) throws Exception {
        String text = new String(readLimited(in, MAX_TEXT), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(text);
        String app = root.optString("app", root.optString("applicationId", ""));
        if (!BuildConfig.APPLICATION_ID.equals(app)) throw new SecurityException("Backup applicationId mismatch");
        JSONObject entities = root.optJSONObject("entities");
        if (entities == null) throw new IllegalArgumentException("Missing entities");
        Counts c = validateEntities(entities);
        return new BackupSummary("json", root.optInt("version", 1), root.optLong("created", 0),
                c.inspections, c.defects, c.images, c.total, 0, 0,
                root.optString("versionName", "legacy"), root.optInt("versionCode", 0), false);
    }

    private BackupSummary inspectZip(InputStream in) throws Exception {
        JSONObject manifest = null, entities = null;
        byte[] entityBytes = null;
        long totalMedia = 0;
        int entries = 0, mediaCount = 0;
        Set<String> names = new HashSet<>();
        try (ZipInputStream z = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IllegalArgumentException("Too many backup entries");
                String name = PathSecurity.sanitizeZipEntry(e.getName());
                if (!names.add(name)) throw new SecurityException("Duplicate ZIP entry: " + name);
                if (e.isDirectory()) { z.closeEntry(); continue; }
                if (name.equals("manifest.json")) {
                    manifest = new JSONObject(new String(readLimited(z, MAX_TEXT), StandardCharsets.UTF_8));
                } else if (name.equals("entities.json")) {
                    entityBytes = readLimited(z, MAX_TEXT);
                    entities = new JSONObject(new String(entityBytes, StandardCharsets.UTF_8));
                } else if (name.startsWith("media/")) {
                    mediaCount++;
                    totalMedia += drainCount(z, MAX_MEDIA_FILE);
                    if (totalMedia > MAX_BACKUP) throw new IllegalArgumentException("Backup too large");
                } else {
                    drainCount(z, 2_000_000L);
                }
                z.closeEntry();
            }
        }
        if (manifest == null || entities == null || entityBytes == null) throw new IllegalArgumentException("Incomplete backup package");
        validateManifest(manifest, mediaCount, totalMedia);
        Counts c = validateEntities(entities);
        boolean integrity = false;
        int schema = manifest.optInt("backupSchema", 0);
        if (schema >= 3) {
            String expected = manifest.optString("entitiesSha256", "");
            if (expected.length() != 64 || !expected.equalsIgnoreCase(sha256(entityBytes))) {
                throw new SecurityException("Entities checksum mismatch");
            }
            if (manifest.optInt("entityCount", -1) != c.total) throw new SecurityException("Entity count mismatch");
            integrity = true;
        }
        return new BackupSummary("zip", schema, manifest.optLong("createdAt", 0),
                c.inspections, c.defects, c.images, c.total, mediaCount, totalMedia,
                manifest.optString("versionName", ""), manifest.optInt("versionCode", 0), integrity);
    }

    private RestoreResult restoreLegacyJson(Uri uri, BackupSummary summary, File safety) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalArgumentException("Backup stream unavailable");
            JSONObject root = new JSONObject(new String(readLimited(in, MAX_TEXT), StandardCharsets.UTF_8));
            JSONObject entities = root.getJSONObject("entities");
            validateEntities(entities);
            db.restoreSnapshot(entities, new ArrayList<>());
            return new RestoreResult(summary, safety, 0, true, false);
        }
    }

    private RestoreResult restoreZip(Uri uri, BackupSummary summary, File safety) throws Exception {
        File stage = new File(context.getCacheDir(), "restore-stage-" + UUID.randomUUID());
        if (!stage.mkdirs()) throw new IllegalStateException("Cannot create restore stage");
        File restoreDir = null;
        try {
            JSONObject manifest = null, entities = null;
            byte[] entityBytes = null;
            Map<String, File> files = new HashMap<>();
            long total = 0;
            int entries = 0;
            Set<String> zipNames = new HashSet<>();
            try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
                if (raw == null) throw new IllegalArgumentException("Backup stream unavailable");
                try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(raw))) {
                    ZipEntry e;
                    byte[] buf = new byte[65536];
                    while ((e = z.getNextEntry()) != null) {
                        if (++entries > MAX_ENTRIES) throw new IllegalArgumentException("Too many backup entries");
                        String name = PathSecurity.sanitizeZipEntry(e.getName());
                        if (!zipNames.add(name)) throw new SecurityException("Duplicate ZIP entry: " + name);
                        if (e.isDirectory()) { z.closeEntry(); continue; }
                        if (name.equals("manifest.json")) {
                            manifest = new JSONObject(new String(readLimited(z, MAX_TEXT), StandardCharsets.UTF_8));
                        } else if (name.equals("entities.json")) {
                            entityBytes = readLimited(z, MAX_TEXT);
                            entities = new JSONObject(new String(entityBytes, StandardCharsets.UTF_8));
                        } else if (name.startsWith("media/")) {
                            String rel = PathSecurity.sanitizeRelative(name.substring(6));
                            File f = new File(stage, UUID.randomUUID() + ".media");
                            long one = 0;
                            try (FileOutputStream out = new FileOutputStream(f)) {
                                int n;
                                while ((n = z.read(buf)) != -1) {
                                    one += n; total += n;
                                    if (one > MAX_MEDIA_FILE || total > MAX_BACKUP) throw new IllegalArgumentException("Backup media too large");
                                    out.write(buf, 0, n);
                                }
                                out.getFD().sync();
                            }
                            if (files.put(rel, f) != null) throw new SecurityException("Duplicate media relative path");
                        } else {
                            drainCount(z, 2_000_000L);
                        }
                        z.closeEntry();
                    }
                }
            }
            if (manifest == null || entities == null || entityBytes == null) throw new IllegalArgumentException("Incomplete backup package");
            validateManifest(manifest, files.size(), total);
            Counts counts = validateEntities(entities);
            if (manifest.optInt("backupSchema", 0) >= 3) {
                String expectedEntities = manifest.optString("entitiesSha256", "");
                if (expectedEntities.length() != 64 || !expectedEntities.equalsIgnoreCase(sha256(entityBytes))) throw new SecurityException("Entities checksum mismatch");
                if (manifest.optInt("entityCount", -1) != counts.total) throw new SecurityException("Entity count mismatch");
            }

            int backupSchema = manifest.optInt("backupSchema", 0);
            JSONArray media = manifest.getJSONArray("media");
            List<AppDb.MediaRecord> records = new ArrayList<>();
            restoreDir = new File(mediaStore.root(), "Restored/restored-" + System.currentTimeMillis());
            if (!restoreDir.mkdirs()) throw new IllegalStateException("Cannot create restore media directory");
            Set<String> mediaIds = new HashSet<>();
            for (int i = 0; i < media.length(); i++) {
                JSONObject x = media.getJSONObject(i);
                String originalRel = PathSecurity.sanitizeRelative(x.getString("relativePath"));
                File staged = files.get(originalRel);
                if (staged == null || !staged.isFile()) throw new IllegalArgumentException("Missing media entry: " + originalRel);
                long expectedSize = x.optLong("size", -1);
                if (backupSchema >= 3 && (expectedSize < 0 || expectedSize != staged.length())) throw new SecurityException("Media size mismatch: " + originalRel);
                if (backupSchema < 3 && expectedSize >= 0 && expectedSize != staged.length()) throw new SecurityException("Media size mismatch: " + originalRel);
                String expected = x.optString("sha256", "");
                String actualSha = sha256(staged);
                if (backupSchema >= 3 && (expected.length() != 64 || !expected.equalsIgnoreCase(actualSha))) throw new SecurityException("Media checksum mismatch");
                if (backupSchema < 3 && !expected.isEmpty() && !expected.equalsIgnoreCase(actualSha)) throw new SecurityException("Media checksum mismatch");
                String mid = x.getString("mediaId");
                if (mid.isEmpty() || mid.length() > 180 || mid.indexOf('\0') >= 0 || !mediaIds.add(mid)) throw new SecurityException("Unsafe or duplicate media id");
                String fname = PathSecurity.safeFileName(x.optString("fileName", "image.jpg"));
                File dest = new File(restoreDir, mid.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + fname);
                copy(staged, dest);
                AppDb.MediaRecord m = new AppDb.MediaRecord();
                m.mediaId = mid; m.ownerKind = x.optString("ownerKind", ""); m.ownerId = x.optString("ownerId", "");
                m.fileName = fname; m.relativePath = relativeToRoot(dest); m.mime = x.optString("mime", "image/jpeg");
                m.width = x.optInt("width", 0); m.height = x.optInt("height", 0); m.sha256 = actualSha;
                m.createdAt = x.optLong("createdAt", System.currentTimeMillis());
                records.add(m);
            }
            db.restoreSnapshot(entities, records);
            return new RestoreResult(summary, safety, records.size(), false, true);
        } catch (Exception e) {
            if (restoreDir != null) deleteRecursive(restoreDir);
            throw e;
        } finally {
            deleteRecursive(stage);
        }
    }

    private String relativeToRoot(File f) throws Exception {
        String base = mediaStore.root().getCanonicalFile().toURI().toString();
        String u = f.getCanonicalFile().toURI().toString();
        if (!u.startsWith(base)) throw new SecurityException("Restore path outside media root");
        return Uri.decode(base.equals(u) ? "" : u.substring(base.length()));
    }

    private static void validateManifest(JSONObject m, int mediaEntries, long mediaBytes) throws Exception {
        if (!BuildConfig.APPLICATION_ID.equals(m.optString("applicationId"))) throw new SecurityException("Backup applicationId mismatch");
        int schema = m.optInt("backupSchema", 0);
        if (schema < 1 || schema > BACKUP_SCHEMA) throw new IllegalArgumentException("Unsupported backup schema");
        JSONArray a = m.optJSONArray("media");
        if (a == null) throw new IllegalArgumentException("Missing media manifest");
        if (a.length() != mediaEntries) throw new IllegalArgumentException("Media manifest/ZIP entry mismatch");
        if (schema >= 3) {
            if (!"SHA-256".equals(m.optString("integrityAlgorithm"))) throw new IllegalArgumentException("Unsupported integrity algorithm");
            if (m.optInt("mediaFiles", -1) != mediaEntries) throw new SecurityException("Media count mismatch");
            if (m.optLong("mediaBytes", -1) != mediaBytes) throw new SecurityException("Media byte count mismatch");
        }
        Set<String> paths = new HashSet<>(), ids = new HashSet<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.getJSONObject(i);
            String rel = PathSecurity.sanitizeRelative(x.getString("relativePath"));
            String mid = x.optString("mediaId", "");
            if (!paths.add(rel)) throw new SecurityException("Duplicate media path in manifest");
            if (mid.isEmpty() || mid.length() > 180 || !ids.add(mid)) throw new SecurityException("Invalid or duplicate media id in manifest");
            String sha = x.optString("sha256", "");
            if (schema >= 3 && sha.length() != 64) throw new SecurityException("Missing media checksum");
        }
    }

    private static Counts validateEntities(JSONObject entities) throws Exception {
        Counts c = new Counts();
        Iterator<String> it = entities.keys();
        while (it.hasNext()) {
            String kind = it.next();
            if (!kind.matches("[A-Za-z0-9_\\-]{1,80}")) throw new SecurityException("Unsafe entity kind");
            JSONArray a = entities.optJSONArray(kind);
            if (a == null) throw new IllegalArgumentException("Invalid entity array");
            c.total += a.length();
            if (c.total > 200_000) throw new IllegalArgumentException("Too many entities");
            if ("inspections".equals(kind)) c.inspections = a.length();
            if ("defects".equals(kind)) c.defects = a.length();
            if ("images".equals(kind)) c.images = a.length();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) throw new IllegalArgumentException("Invalid entity");
                String id = o.optString("id", "");
                if (id.isEmpty() || id.length() > 180 || id.indexOf('\0') >= 0) throw new SecurityException("Unsafe entity id");
            }
        }
        return c;
    }

    private static void putText(ZipOutputStream z, String name, String text) throws Exception {
        z.putNextEntry(new ZipEntry(name)); z.write(text.getBytes(StandardCharsets.UTF_8)); z.closeEntry();
    }
    private static byte[] readLimited(InputStream in, long max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[65536]; int n; long total = 0;
        while ((n = in.read(b)) != -1) { total += n; if (total > max) throw new IllegalArgumentException("Entry too large"); out.write(b, 0, n); }
        return out.toByteArray();
    }
    private static long drainCount(InputStream in, long max) throws Exception {
        byte[] b = new byte[65536]; int n; long total = 0;
        while ((n = in.read(b)) != -1) { total += n; if (total > max) throw new IllegalArgumentException("Entry too large"); }
        return total;
    }
    private static void copy(File a, File b) throws Exception {
        File parent = b.getParentFile(); if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Cannot create restore directory");
        try (FileInputStream in = new FileInputStream(a); FileOutputStream out = new FileOutputStream(b)) {
            byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n); out.getFD().sync();
        }
    }
    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(f)) { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) != -1) md.update(b, 0, n); }
        return hex(md.digest());
    }
    private static String sha256(byte[] b) throws Exception { MessageDigest md = MessageDigest.getInstance("SHA-256"); return hex(md.digest(b)); }
    private static String hex(byte[] bytes) { StringBuilder s = new StringBuilder(); for (byte x : bytes) s.append(String.format(Locale.ROOT, "%02x", x)); return s.toString(); }
    private static void deleteRecursive(File f) { if (f == null || !f.exists()) return; if (f.isDirectory()) { File[] kids = f.listFiles(); if (kids != null) for (File k : kids) deleteRecursive(k); } f.delete(); }

    private static final class Counts { int inspections, defects, images, total; }

    public static final class BackupSummary {
        public final String format, versionName;
        public final int schema, inspections, defects, legacyImages, totalEntities, mediaFiles, versionCode;
        public final long createdAt, mediaBytes;
        public final boolean integrityVerified;
        BackupSummary(String format, int schema, long createdAt, int inspections, int defects, int legacyImages,
                      int total, int mediaFiles, long mediaBytes, String versionName, int versionCode, boolean integrityVerified) {
            this.format = format; this.schema = schema; this.createdAt = createdAt; this.inspections = inspections; this.defects = defects;
            this.legacyImages = legacyImages; this.totalEntities = total; this.mediaFiles = mediaFiles; this.mediaBytes = mediaBytes;
            this.versionName = versionName; this.versionCode = versionCode; this.integrityVerified = integrityVerified;
        }
        public JSONObject json() throws Exception {
            return new JSONObject().put("format", format).put("schema", schema).put("createdAt", createdAt)
                    .put("inspections", inspections).put("defects", defects).put("legacyImages", legacyImages)
                    .put("totalEntities", totalEntities).put("mediaFiles", mediaFiles).put("mediaBytes", mediaBytes)
                    .put("versionName", versionName).put("versionCode", versionCode).put("integrityVerified", integrityVerified);
        }
    }

    public static final class RestoreResult {
        public final BackupSummary summary; public final File safetyBackup; public final int restoredMedia;
        public final boolean needsLegacyMediaMigration, integrityVerified;
        RestoreResult(BackupSummary s, File b, int m, boolean legacy, boolean integrityVerified) {
            summary = s; safetyBackup = b; restoredMedia = m; needsLegacyMediaMigration = legacy; this.integrityVerified = integrityVerified;
        }
    }
}
