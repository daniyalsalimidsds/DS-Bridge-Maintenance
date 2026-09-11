package ir.bridge.maintenance;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Offline credential and short-lived session manager.
 *
 * PIN material never crosses back to JavaScript after enrollment and plaintext is
 * never persisted. The verifier uses a per-user random salt and PBKDF2-HMAC-SHA256.
 */
final class CredentialManager {
    private static final int PBKDF2_ITERATIONS = 310_000;
    private static final int DERIVED_BITS = 256;
    private static final long SESSION_IDLE_MS = 15L * 60L * 1000L;
    private static final int MAX_FAILURES_BEFORE_LOCK = 5;
    private static final long BASE_LOCK_MS = 60_000L;
    private static final long MAX_LOCK_MS = 15L * 60L * 1000L;

    private final AppDb db;
    private final SecureRandom random = new SecureRandom();
    private String sessionUserId = "";
    private long sessionExpiresAt = 0L;

    CredentialManager(AppDb db) {
        this.db = db;
    }

    synchronized JSONObject status() throws Exception {
        JSONObject user = currentUser(false);
        JSONArray enrolled = new JSONArray();
        for (JSONObject candidate : users()) {
            if (db.credential(candidate.optString("id")) != null) enrolled.put(candidate.optString("id"));
        }
        JSONObject result = new JSONObject()
                .put("ok", true)
                .put("authenticated", user != null)
                .put("bootstrapRequired", db.credentialCount() == 0)
                .put("credentialUserIds", enrolled)
                .put("expiresAt", user == null ? 0 : sessionExpiresAt);
        if (user != null) result.put("user", publicUser(user));
        return result;
    }

    synchronized JSONObject enroll(String userId, char[] pin) throws Exception {
        try {
            JSONObject target = db.find("users", userId);
            if (target == null || target.optBoolean("archived", false)) throw new SecurityException("user-not-available");
            boolean bootstrap = db.credentialCount() == 0;
            if (bootstrap) {
                if (!"مدیر سیستم".equals(target.optString("role"))) throw new SecurityException("bootstrap-manager-required");
            } else {
                requireRole("مدیر سیستم");
            }
            validatePin(pin);
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            byte[] verifier = derive(pin, salt, PBKDF2_ITERATIONS);
            db.saveCredential(new AppDb.CredentialRecord(userId, salt, verifier, PBKDF2_ITERATIONS, 0, 0L));
            if (bootstrap) {
                sessionUserId = userId;
                sessionExpiresAt = System.currentTimeMillis() + SESSION_IDLE_MS;
            }
            JSONObject result = status().put("enrolledUserId", userId);
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(verifier, (byte) 0);
            return result;
        } finally {
            if (pin != null) Arrays.fill(pin, '\0');
        }
    }

    synchronized JSONObject authenticate(String userId, char[] pin) throws Exception {
        long now = System.currentTimeMillis();
        try {
            JSONObject user = db.find("users", userId);
            AppDb.CredentialRecord credential = db.credential(userId);
            if (user == null || user.optBoolean("archived", false) || credential == null)
                throw new SecurityException("invalid-credentials");
            if (credential.lockedUntil > now)
                return new JSONObject().put("ok", false).put("error", "locked").put("lockedUntil", credential.lockedUntil);
            byte[] derived = derive(pin, credential.salt, credential.iterations);
            boolean matches = MessageDigest.isEqual(derived, credential.verifier);
            Arrays.fill(derived, (byte) 0);
            if (!matches) {
                int failures = credential.failedAttempts + 1;
                long lockedUntil = 0L;
                if (failures >= MAX_FAILURES_BEFORE_LOCK) {
                    int exponent = Math.min(4, failures - MAX_FAILURES_BEFORE_LOCK);
                    lockedUntil = now + Math.min(MAX_LOCK_MS, BASE_LOCK_MS * (1L << exponent));
                }
                db.updateCredentialFailure(userId, failures, lockedUntil);
                return new JSONObject().put("ok", false).put("error", "invalid-credentials")
                        .put("lockedUntil", lockedUntil).put("remainingBeforeLock", Math.max(0, MAX_FAILURES_BEFORE_LOCK - failures));
            }
            db.updateCredentialFailure(userId, 0, 0L);
            sessionUserId = userId;
            sessionExpiresAt = now + SESSION_IDLE_MS;
            return status();
        } finally {
            if (pin == null) {
                // No-op; kept symmetric with enroll so callers can always discard input.
            } else {
                Arrays.fill(pin, '\0');
            }
        }
    }

    synchronized JSONObject lock() throws Exception {
        sessionUserId = "";
        sessionExpiresAt = 0L;
        return status();
    }

    synchronized JSONObject requireAuthenticated() throws Exception {
        JSONObject user = currentUser(true);
        if (user == null) throw new SecurityException("authentication-required");
        return user;
    }

    synchronized JSONObject requireRole(String... allowedRoles) throws Exception {
        JSONObject user = requireAuthenticated();
        String role = user.optString("role");
        for (String allowed : allowedRoles) if (allowed.equals(role)) return user;
        throw new SecurityException("role-not-authorized");
    }

    synchronized String currentUserId() throws Exception {
        return requireAuthenticated().optString("id");
    }

    synchronized void touch() throws Exception {
        if (currentUser(false) != null) sessionExpiresAt = System.currentTimeMillis() + SESSION_IDLE_MS;
    }

    private JSONObject currentUser(boolean touch) throws Exception {
        long now = System.currentTimeMillis();
        if (sessionUserId.isEmpty() || sessionExpiresAt <= now) {
            sessionUserId = "";
            sessionExpiresAt = 0L;
            return null;
        }
        JSONObject user = db.find("users", sessionUserId);
        if (user == null || user.optBoolean("archived", false) || db.credential(sessionUserId) == null) {
            sessionUserId = "";
            sessionExpiresAt = 0L;
            return null;
        }
        if (touch) sessionExpiresAt = now + SESSION_IDLE_MS;
        return user;
    }

    private Iterable<JSONObject> users() throws Exception {
        JSONArray rows = new JSONArray(db.list("users"));
        java.util.List<JSONObject> result = new java.util.ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject user = rows.optJSONObject(index);
            if (user != null && !user.optBoolean("archived", false)) result.add(user);
        }
        return result;
    }

    private static JSONObject publicUser(JSONObject user) throws Exception {
        JSONObject copy = new JSONObject(user.toString());
        copy.remove("credential");
        return copy;
    }

    private static void validatePin(char[] pin) {
        if (pin == null) throw new IllegalArgumentException("pin-required");
        boolean allDigits = true;
        for (char value : pin) if (value < '0' || value > '9') { allDigits = false; break; }
        boolean numeric = allDigits && pin.length >= 6 && pin.length <= 12;
        boolean passphrase = pin.length >= 8 && pin.length <= 64;
        if (!numeric && !passphrase) throw new IllegalArgumentException("weak-pin");
    }

    private static byte[] derive(char[] pin, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, iterations, DERIVED_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
