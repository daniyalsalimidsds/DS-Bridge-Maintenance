package ir.bridge.maintenance;

import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Safety-critical inspection governance transactions used by the trusted local UI. */
final class InspectionGovernance {
    private static final Set<String> USER_ROLES = immutableSet("بازرس", "بازبین کنترل کیفیت", "سرپرست", "مدیر سیستم");
    private static final Set<String> SEVERITY_IDS = immutableSet("none", "low", "medium", "emergency", "uninspectable");
    private static final Set<String> PROGRAM_BASES = immutableSet("owner-policy", "risk-based", "regulatory", "engineering-review");
    private static final Set<String> RESTRICTIONS = immutableSet("none", "load", "lane", "closure", "shoring", "other");

    private static Set<String> immutableSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    private final AppDb db;
    private final CredentialManager credentials;

    InspectionGovernance(AppDb db, CredentialManager credentials) {
        this.db = db;
        this.credentials = credentials;
    }

    JSONObject dispatch(String type, JSONObject payload) throws Exception {
        if ("authStatus".equals(type)) return credentials.status();
        if ("authenticate".equals(type)) return credentials.authenticate(
                bounded(payload.optString("userId"), 180), payload.optString("pin").toCharArray());
        if ("enrollPin".equals(type)) return credentials.enroll(
                bounded(payload.optString("userId"), 180), payload.optString("pin").toCharArray());
        if ("lockSession".equals(type)) return credentials.lock();
        if ("saveGovernedUser".equals(type)) return saveUser(payload.optJSONObject("user"));
        if ("deactivateGovernedUser".equals(type)) return deactivateUser(payload.optString("userId"), payload.optString("reason"));
        if ("submitInspection".equals(type)) return submit(payload);
        if ("reviewInspection".equals(type)) return review(payload);
        if ("voidInspection".equals(type)) return voidInspection(payload);
        if ("createCorrectionDraft".equals(type)) return createCorrectionDraft(payload);
        if ("saveInspectionProgram".equals(type)) return saveInspectionProgram(payload.optJSONObject("program"));
        if ("updateCriticalFinding".equals(type)) return updateCriticalFinding(payload);
        if ("appendAudit".equals(type)) return appendAudit(payload);
        throw new IllegalArgumentException("unsupported-governance-operation");
    }

    boolean reportPayloadIsOfficial(JSONObject report) {
        try {
            JSONArray records = report == null ? null : report.optJSONArray("records");
            if (records == null || records.length() == 0 || records.length() > 1000) return false;
            Set<String> inspectionIds = new HashSet<>();
            for (int index = 0; index < records.length(); index++) {
                JSONObject supplied = records.optJSONObject(index);
                if (supplied == null) return false;
                String id = requiredId(supplied, "inspection");
                if (!inspectionIds.add(id)) return false;
                JSONObject stored = db.find("inspections", id);
                if (stored == null || !"approved".equals(stored.optString("workflowStatus"))
                        || stored.optBoolean("officialReport", true) == false) return false;
                String reportNo = stored.optString("reportNo", stored.optString("no", ""));
                if (!reportNo.equals(supplied.optString("reportNo", supplied.optString("no", "")))
                        || !db.isRegisteredReport(id, reportNo)
                        || db.find("inspectionVoids", "void-" + id) != null) return false;
                String hash = stored.optString("fieldHash", "");
                if (!hash.isEmpty()) {
                    if (!hash.equals(supplied.optString("fieldHash"))
                            || !hash.equals(inspectionHash(stored))
                            || !hash.equals(inspectionHash(supplied))) return false;
                } else if (!"legacy-unverified".equals(stored.optString("identityStatus"))
                        || !canonicalJson(stored).equals(canonicalJson(supplied))) return false;
            }
            JSONArray suppliedCritical = report.optJSONArray("criticalFindings");
            return suppliedCritical != null && suppliedCritical.length() <= 5000
                    && sameEntitySet(suppliedCritical, relatedEntitiesForInspections("criticalFindings", inspectionIds));
        } catch (Exception error) {
            return false;
        }
    }

    private JSONArray relatedEntitiesForInspections(String kind, Set<String> inspectionIds) throws Exception {
        JSONArray source = new JSONArray(db.list(kind));
        JSONArray result = new JSONArray();
        for (int index = 0; index < source.length(); index++) {
            JSONObject value = source.optJSONObject(index);
            if (value != null && inspectionIds.contains(value.optString("inspectionId")))
                result.put(new JSONObject(value.toString()));
        }
        return result;
    }

    private static boolean sameEntitySet(JSONArray supplied, JSONArray expected) throws Exception {
        if (supplied.length() != expected.length()) return false;
        Map<String, String> canonicalExpected = new HashMap<>();
        for (int index = 0; index < expected.length(); index++) {
            JSONObject value = expected.optJSONObject(index);
            if (value == null) return false;
            String id = requiredId(value, "related-entity");
            if (canonicalExpected.put(id, canonicalJson(value)) != null) return false;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < supplied.length(); index++) {
            JSONObject value = supplied.optJSONObject(index);
            if (value == null) return false;
            String id = requiredId(value, "related-entity");
            if (!seen.add(id) || !canonicalJson(value).equals(canonicalExpected.get(id))) return false;
        }
        return true;
    }

    private JSONObject saveUser(JSONObject proposed) throws Exception {
        if (proposed == null) throw new IllegalArgumentException("user-required");
        JSONObject actor = credentials.requireRole("مدیر سیستم");
        String targetId = bounded(proposed.optString("id"), 180);
        if (targetId.isEmpty()) targetId = "user-" + UUID.randomUUID();
        String name = bounded(proposed.optString("name").trim(), 120);
        String role = proposed.optString("role");
        if (name.length() < 2 || !USER_ROLES.contains(role)) throw new IllegalArgumentException("invalid-user");
        JSONObject existing = db.find("users", targetId);
        JSONObject user = existing == null ? new JSONObject() : new JSONObject(existing.toString());
        user.put("id", targetId).put("name", name).put("role", role).put("archived", false).put("active", false);
        user.put("personnelId", bounded(proposed.optString("personnelId"), 80));

        JSONObject qualification = proposed.optJSONObject("qualification");
        if (qualification == null) qualification = new JSONObject().put("status", "needs_review");
        else qualification = new JSONObject(qualification.toString());
        boolean self = targetId.equals(actor.optString("id"));
        boolean approve = "approved".equals(qualification.optString("status"));
        if (self && approve) {
            qualification.put("status", "needs_review");
            qualification.remove("approvedBy");
            qualification.remove("approvedAt");
        } else if (approve) {
            validateQualificationFields(qualification);
            qualification.put("approvedBy", actor.optString("id"));
            qualification.put("approvedAt", System.currentTimeMillis());
        } else {
            qualification.put("status", "needs_review");
            qualification.remove("approvedBy");
            qualification.remove("approvedAt");
        }
        user.put("qualification", qualification).put("updatedAt", System.currentTimeMillis());

        SQLiteDatabase sql = db.getWritableDatabase();
        sql.beginTransaction();
        JSONObject event;
        try {
            AppDb.upsertEntity(sql, "users", user);
            event = audit(sql, actor, existing == null ? "user-created" : "user-updated", "users", targetId,
                    self && approve ? "Self-approval refused; qualification remains pending independent approval" : role,
                    qualification.optString("status"));
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
        credentials.touch();
        return successPatch("users", user, event);
    }

    private JSONObject deactivateUser(String userId, String reason) throws Exception {
        JSONObject actor = credentials.requireRole("مدیر سیستم");
        JSONObject user = db.find("users", bounded(userId, 180));
        if (user == null) throw new IllegalArgumentException("user-not-found");
        if (userId.equals(actor.optString("id"))) throw new SecurityException("cannot-deactivate-current-session");
        if (reason == null || reason.trim().length() < 8) throw new IllegalArgumentException("deactivation-reason-required");
        if ("مدیر سیستم".equals(user.optString("role")) && countActiveManagers(userId) == 0)
            throw new SecurityException("last-manager-required");
        user.put("archived", true).put("active", false).put("deactivatedAt", System.currentTimeMillis())
                .put("deactivatedBy", actor.optString("id")).put("deactivationReason", bounded(reason.trim(), 800))
                .put("updatedAt", System.currentTimeMillis());
        SQLiteDatabase sql = db.getWritableDatabase();
        sql.beginTransaction();
        JSONObject event;
        try {
            AppDb.upsertEntity(sql, "users", user);
            event = audit(sql, actor, "user-deactivated", "users", userId, reason, "");
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
        return successPatch("users", user, event);
    }

    private JSONObject submit(JSONObject payload) throws Exception {
        JSONObject actor = credentials.requireAuthenticated();
        JSONObject inspectionInput = payload.optJSONObject("inspection");
        JSONArray defects = payload.optJSONArray("defects");
        JSONArray criticalCases = payload.optJSONArray("criticalFindings");
        if (inspectionInput == null || defects == null || criticalCases == null || defects.length() > 2000 || criticalCases.length() > 500)
            throw new IllegalArgumentException("invalid-submission-bundle");
        JSONObject inspection = new JSONObject(inspectionInput.toString());
        String inspectionId = requiredId(inspection, "inspection");
        if (blank(inspection.optString("bridgeId"))) throw new IllegalArgumentException("bridge-required");
        String visitType = inspection.optString("visitType");
        requireQualified(actor, visitType, false);
        if (!actor.optString("id").equals(inspection.optString("inspectorId")))
            throw new SecurityException("inspector-session-mismatch");
        JSONObject signature = inspection.optJSONObject("signatureAttachment");
        if (signature == null || blank(signature.optString("mediaId"))
                || !actor.optString("id").equals(signature.optString("signerId"))
                || !actor.optString("name").equals(signature.optString("signer")))
            throw new SecurityException("signature-identity-mismatch");
        AppDb.MediaRecord signatureMedia = db.mediaById(signature.optString("mediaId"));
        if (signatureMedia == null || !"signature".equals(signatureMedia.ownerKind)
                || !inspectionId.equals(signatureMedia.ownerId) || !"image/png".equals(signatureMedia.mime))
            throw new SecurityException("signature-evidence-not-found");
        validateInspectionItems(inspection, criticalCases);

        JSONObject stored = db.find("inspections", inspectionId);
        boolean inspectorReassigned = false;
        String inspectorReassignmentReason = "";
        if (stored != null) {
            String workflow = stored.optString("workflowStatus", "draft");
            if (!"draft".equals(workflow) && !"returned".equals(workflow) && !"پیش‌نویس".equals(stored.optString("status")))
                throw new SecurityException("inspection-field-record-locked");
            if ("returned".equals(workflow) && signatureMedia.createdAt <= stored.optLong("returnedAt", 0L))
                throw new SecurityException("signature-must-be-recaptured-after-return");
            String existingInspector = stored.optString("inspectorId", "");
            if (!existingInspector.isEmpty() && !existingInspector.equals(actor.optString("id"))) {
                inspectorReassigned = true;
                inspectorReassignmentReason = bounded(inspection.optString("inspectorReassignmentReason").trim(), 1200);
                if (inspectorReassignmentReason.length() < 10)
                    throw new SecurityException("inspector-reassignment-reason-required");
                JSONArray history = inspection.optJSONArray("inspectorReassignments");
                if (history == null) history = new JSONArray();
                history.put(new JSONObject().put("fromInspectorId", existingInspector)
                        .put("toInspectorId", actor.optString("id"))
                        .put("reason", inspectorReassignmentReason)
                        .put("authorizedBy", actor.optString("id"))
                        .put("at", System.currentTimeMillis()));
                inspection.put("inspectorReassignments", history);
            }
        }

        long now = System.currentTimeMillis();
        int cycle = stored == null ? 1 : Math.max(1, stored.optInt("submissionCycle", 0) + 1);
        inspection.remove("no");
        inspection.remove("reportNo");
        inspection.remove("approvedAt");
        inspection.remove("approvedBy");
        inspection.put("status", "ارسال‌شده")
                .put("workflowStatus", "submitted")
                .put("submissionCycle", cycle)
                .put("submittedAt", now)
                .put("submittedByUserId", actor.optString("id"))
                .put("inspector", actor.optString("name"))
                .put("fieldLocked", true)
                .put("officialReport", false)
                .put("updatedAt", now);
        signature.put("boundInspectorId", actor.optString("id"));
        String fieldHash = inspectionHash(inspection);
        inspection.put("fieldHash", fieldHash);
        signature
                .put("boundInspectionHash", fieldHash)
                .put("boundAt", now);

        JSONArray normalizedDefects = validateRelatedEntities(defects, inspectionId, "defect");
        JSONArray normalizedCritical = normalizeCriticalCases(criticalCases, inspection, normalizedDefects, now);
        JSONObject snapshotBundle = new JSONObject()
                .put("inspection", new JSONObject(inspection.toString()))
                .put("defects", new JSONArray(normalizedDefects.toString()))
                .put("criticalFindings", new JSONArray(normalizedCritical.toString()));
        JSONObject revision = new JSONObject()
                .put("id", "submission-" + inspectionId + "-" + cycle)
                .put("inspectionId", inspectionId)
                .put("revision", cycle)
                .put("type", "field-submission")
                .put("inspectionHash", fieldHash)
                .put("submittedBy", actor.optString("id"))
                .put("submittedAt", now)
                .put("snapshot", new JSONObject(inspection.toString()))
                .put("snapshotBundle", snapshotBundle)
                .put("updatedAt", now);

        SQLiteDatabase sql = db.getWritableDatabase();
        sql.beginTransaction();
        JSONObject event;
        try {
            AppDb.upsertEntity(sql, "inspections", inspection);
            upsertAll(sql, "defects", normalizedDefects);
            upsertAll(sql, "criticalFindings", normalizedCritical);
            AppDb.upsertEntity(sql, "reportRevisions", revision);
            event = audit(sql, actor, inspectorReassigned ? "inspection-reassigned-and-submitted" : "inspection-submitted",
                    "inspections", inspectionId, inspectorReassigned
                            ? "Inspector reassignment: " + inspectorReassignmentReason : "Submitted for independent QC",
                    String.valueOf(cycle));
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
        credentials.touch();
        return successPatches(new JSONObject()
                .put("inspections", new JSONArray().put(inspection))
                .put("defects", normalizedDefects)
                .put("criticalFindings", normalizedCritical)
                .put("reportRevisions", new JSONArray().put(revision))
                .put("audit", new JSONArray().put(event)));
    }

    private JSONObject review(JSONObject payload) throws Exception {
        JSONObject reviewer = credentials.requireRole("بازبین کنترل کیفیت", "سرپرست", "مدیر سیستم");
        String inspectionId = bounded(payload.optString("inspectionId"), 180);
        String decision = payload.optString("decision");
        String comment = bounded(payload.optString("comment").trim(), 2400);
        if (!"approve".equals(decision) && !"return".equals(decision)) throw new IllegalArgumentException("invalid-review-decision");
        if (comment.length() < 5) throw new IllegalArgumentException("review-comment-required");
        JSONObject inspection = db.find("inspections", inspectionId);
        if (inspection == null || !"submitted".equals(inspection.optString("workflowStatus")))
            throw new SecurityException("inspection-not-awaiting-review");
        requireQualified(reviewer, inspection.optString("visitType"), true);
        if (reviewer.optString("id").equals(inspection.optString("inspectorId")))
            throw new SecurityException("independent-reviewer-required");
        String submittedHash = inspection.optString("fieldHash");
        if (submittedHash.isEmpty() || !submittedHash.equals(inspectionHash(inspection)))
            throw new SecurityException("submitted-record-hash-mismatch");

        long now = System.currentTimeMillis();
        int cycle = inspection.optInt("submissionCycle", 1);
        JSONObject review = new JSONObject()
                .put("id", "review-" + inspectionId + "-" + cycle + "-" + UUID.randomUUID())
                .put("inspectionId", inspectionId)
                .put("reviewerId", reviewer.optString("id"))
                .put("reviewerName", reviewer.optString("name"))
                .put("reviewType", "independent-qc")
                .put("decision", decision)
                .put("comment", comment)
                .put("inspectionHash", submittedHash)
                .put("reviewedAt", now)
                .put("updatedAt", now);
        inspection.put("qcReviewId", review.optString("id"))
                .put("qcReviewerId", reviewer.optString("id"))
                .put("qcReviewer", reviewer.optString("name"))
                .put("qcComment", comment)
                .put("qcReviewedAt", now)
                .put("updatedAt", now);

        JSONObject reportRevision = null;
        JSONObject program = null;
        if ("return".equals(decision)) {
            inspection.put("status", "بازگشت برای اصلاح")
                    .put("workflowStatus", "returned")
                    .put("returnedAt", now)
                    .put("fieldLocked", false)
                    .put("officialReport", false);
        } else {
            String dayKey = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
            SQLiteDatabase sql = db.getWritableDatabase();
            sql.beginTransaction();
            JSONObject event;
            try {
                String reportNo = db.issueReportNumber(sql, inspectionId, dayKey, now);
                inspection.put("no", reportNo).put("reportNo", reportNo)
                        .put("status", "نهایی").put("workflowStatus", "approved")
                        .put("approvedAt", now).put("approvedBy", reviewer.optString("id"))
                        .put("finalizedAt", now).put("fieldLocked", true).put("officialReport", true);
                reportRevision = new JSONObject()
                        .put("id", "report-" + inspectionId + "-" + cycle)
                        .put("inspectionId", inspectionId)
                        .put("reportNo", reportNo)
                        .put("revision", cycle)
                        .put("type", "approved-report")
                        .put("inspectionHash", submittedHash)
                        .put("approvalReviewId", review.optString("id"))
                        .put("approvedBy", reviewer.optString("id"))
                        .put("approvedAt", now)
                        .put("snapshotBundle", approvedSnapshotBundle(inspection))
                        .put("supersedesInspectionId", inspection.optString("supersedesInspectionId", ""))
                        .put("updatedAt", now);
                AppDb.upsertEntity(sql, "reviews", review);
                AppDb.upsertEntity(sql, "inspections", inspection);
                AppDb.upsertEntity(sql, "reportRevisions", reportRevision);
                program = updateProgramAfterApproval(sql, inspection, now);
                event = audit(sql, reviewer, "inspection-approved", "inspections", inspectionId,
                        comment, reportNo);
                sql.setTransactionSuccessful();
            } finally { sql.endTransaction(); }
            JSONObject patches = new JSONObject()
                    .put("reviews", new JSONArray().put(review))
                    .put("inspections", new JSONArray().put(inspection))
                    .put("reportRevisions", new JSONArray().put(reportRevision))
                    .put("audit", new JSONArray().put(event));
            if (program != null) patches.put("inspectionPrograms", new JSONArray().put(program));
            credentials.touch();
            return successPatches(patches).put("reportNo", inspection.optString("reportNo"));
        }

        SQLiteDatabase sql = db.getWritableDatabase();
        sql.beginTransaction();
        JSONObject event;
        try {
            AppDb.upsertEntity(sql, "reviews", review);
            AppDb.upsertEntity(sql, "inspections", inspection);
            event = audit(sql, reviewer, "inspection-returned", "inspections", inspectionId, comment, String.valueOf(cycle));
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
        credentials.touch();
        return successPatches(new JSONObject()
                .put("reviews", new JSONArray().put(review))
                .put("inspections", new JSONArray().put(inspection))
                .put("audit", new JSONArray().put(event)));
    }

    private JSONObject voidInspection(JSONObject payload) throws Exception {
        JSONObject approver = credentials.requireRole("بازبین کنترل کیفیت", "سرپرست", "مدیر سیستم");
        requireQualified(approver, "*", true);
        String inspectionId = bounded(payload.optString("inspectionId"), 180);
        String reason = bounded(payload.optString("reason").trim(), 2400);
        if (reason.length() < 10) throw new IllegalArgumentException("void-reason-required");
        JSONObject inspection = db.find("inspections", inspectionId);
        if (inspection == null || !"approved".equals(inspection.optString("workflowStatus")))
            throw new SecurityException("only-approved-report-can-be-voided");
        if (approver.optString("id").equals(inspection.optString("inspectorId")))
            throw new SecurityException("independent-void-approval-required");
        String voidId = "void-" + inspectionId;
        if (db.find("inspectionVoids", voidId) != null) throw new IllegalStateException("report-already-void");
        long now = System.currentTimeMillis();
        JSONObject record = new JSONObject()
                .put("id", voidId)
                .put("inspectionId", inspectionId)
                .put("reportNo", inspection.optString("reportNo", inspection.optString("no")))
                .put("reason", reason)
                .put("approvedBy", approver.optString("id"))
                .put("approvedByName", approver.optString("name"))
                .put("voidedAt", now)
                .put("status", "void")
                .put("updatedAt", now);
        SQLiteDatabase sql = db.getWritableDatabase();
        sql.beginTransaction();
        JSONObject event;
        try {
            AppDb.upsertEntity(sql, "inspectionVoids", record);
            event = audit(sql, approver, "report-voided", "inspections", inspectionId, reason, inspection.optString("reportNo"));
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
        credentials.touch();
        return successPatches(new JSONObject()
                .put("inspectionVoids", new JSONArray().put(record))
                .put("audit", new JSONArray().put(event)));
    }

    private JSONObject createCorrectionDraft(JSONObject payload) throws Exception {
        JSONObject inspector = credentials.requireAuthenticated();
        String sourceId = bounded(payload.optString("inspectionId"), 180);
        String reason = bounded(payload.optString("reason").trim(), 1200);
        if (reason.length() < 8) throw new IllegalArgumentException("correction-reason-required");
        JSONObject source = db.find("inspections", sourceId);
        if (source == null || !"approved".equals(source.optString("workflowStatus")))
            throw new SecurityException("approved-source-required");
        requireQualified(inspector, source.optString("visitType"), false);
        JSONObject draft = new JSONObject(source.toString());
        String draftId = "ins-" + UUID.randomUUID();
        long now = System.currentTimeMillis();
        draft.put("id", draftId)
                .put("status", "پیش‌نویس")
                .put("workflowStatus", "draft")
                .put("supersedesInspectionId", sourceId)
                .put("correctionReason", reason)
                .put("revisionNumber", source.optInt("revisionNumber", 1) + 1)
                .put("inspectorId", inspector.optString("id"))
                .put("inspector", inspector.optString("name"))
                .put("createdByUserId", inspector.optString("id"))
                .put("createdAt", now)
                .put("updatedAt", now)
                .put("fieldLocked", false)
                .put("officialReport", false);
        for (String key : new String[]{"no", "reportNo", "approvedAt", "approvedBy", "finalizedAt", "submittedAt",
                "returnedAt", "qcReviewId", "qcReviewerId", "qcReviewer", "qcComment", "qcReviewedAt",
                "fieldHash", "signatureAttachment", "signature", "submissionCycle"}) draft.remove(key);
        SQLiteDatabase sql = db.getWritableDatabase();
        sql.beginTransaction();
        JSONObject event;
        try {
            AppDb.upsertEntity(sql, "inspections", draft);
            event = audit(sql, inspector, "correction-draft-created", "inspections", draftId, reason, sourceId);
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
        credentials.touch();
        return successPatches(new JSONObject()
                .put("inspections", new JSONArray().put(draft))
                .put("audit", new JSONArray().put(event))).put("draftId", draftId);
    }

    private JSONObject saveInspectionProgram(JSONObject proposed) throws Exception {
        JSONObject manager = credentials.requireRole("سرپرست", "مدیر سیستم");
        if (proposed == null) throw new IllegalArgumentException("program-required");
        String bridgeId = bounded(proposed.optString("bridgeId"), 180);
        if (db.find("bridges", bridgeId) == null) throw new IllegalArgumentException("bridge-not-found");
        int intervalMonths = proposed.optInt("intervalMonths", 0);
        String basis = proposed.optString("intervalBasis");
        LocalDate dueDate = parseDate(proposed.optString("dueDate"), "due-date-required");
        if (intervalMonths < 1 || intervalMonths > 120 || !PROGRAM_BASES.contains(basis))
            throw new IllegalArgumentException("invalid-program");
        String overrideDateText = proposed.optString("riskOverrideDueDate", "").trim();
        String overrideReason = bounded(proposed.optString("riskOverrideReason").trim(), 1200);
        if (!overrideDateText.isEmpty()) {
            parseDate(overrideDateText, "invalid-risk-override-date");
            if (overrideReason.length() < 8) throw new IllegalArgumentException("risk-override-reason-required");
        }
        LocalDate effectiveDue = overrideDateText.isEmpty() ? dueDate : LocalDate.parse(overrideDateText);
        String lateReason = bounded(proposed.optString("lateReason").trim(), 1200);
        if (effectiveDue.isBefore(LocalDate.now(ZoneOffset.UTC)) && lateReason.length() < 8)
            throw new IllegalArgumentException("late-reason-required");
        long now = System.currentTimeMillis();
        String programId = "program-" + bridgeId;
        JSONObject existing = db.find("inspectionPrograms", programId);
        JSONObject program = existing == null ? new JSONObject() : new JSONObject(existing.toString());
        program.put("id", programId).put("bridgeId", bridgeId)
                .put("intervalMonths", intervalMonths).put("intervalBasis", basis)
                .put("dueDate", dueDate.toString()).put("riskOverrideDueDate", overrideDateText)
                .put("riskOverrideReason", overrideReason)
                .put("lateReason", lateReason)
                .put("effectiveDueDate", effectiveDue.toString())
                .put("status", dueStatus(effectiveDue))
                .put("managedBy", manager.optString("id")).put("updatedAt", now);
        SQLiteDatabase sql = db.getWritableDatabase();
        sql.beginTransaction();
        JSONObject event;
        try {
            AppDb.upsertEntity(sql, "inspectionPrograms", program);
            event = audit(sql, manager, "inspection-program-saved", "inspectionPrograms", programId,
                    basis + " / " + intervalMonths + " months", program.optString("effectiveDueDate"));
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
        credentials.touch();
        return successPatch("inspectionPrograms", program, event);
    }

    private JSONObject updateCriticalFinding(JSONObject payload) throws Exception {
        JSONObject actor = credentials.requireAuthenticated();
        String caseId = bounded(payload.optString("caseId"), 180);
        String action = payload.optString("action");
        JSONObject critical = db.find("criticalFindings", caseId);
        if (critical == null) throw new IllegalArgumentException("critical-case-not-found");
        if ("closed".equals(critical.optString("status")))
            throw new SecurityException("critical-case-already-closed");
        boolean manager = USER_ROLES.contains(actor.optString("role"))
                && !"بازرس".equals(actor.optString("role"));
        boolean owner = actor.optString("id").equals(critical.optString("ownerId"));
        if (!owner && !manager) throw new SecurityException("critical-case-not-authorized");
        long now = System.currentTimeMillis();
        String auditAction;
        if ("acknowledge".equals(action)) {
            critical.put("acknowledgmentStatus", "acknowledged").put("acknowledgedAt", now)
                    .put("acknowledgedBy", actor.optString("id")).put("status", "acknowledged");
            auditAction = "critical-finding-acknowledged";
        } else if ("interim".equals(action)) {
            String summary = bounded(payload.optString("summary").trim(), 1600);
            if (summary.length() < 8) throw new IllegalArgumentException("interim-action-required");
            critical.put("interimAction", summary).put("interimActionAt", now)
                    .put("interimActionBy", actor.optString("id")).put("status", "interim-action");
            auditAction = "critical-finding-interim-action";
        } else if ("resolve".equals(action)) {
            String summary = bounded(payload.optString("summary").trim(), 1600);
            String evidence = bounded(payload.optString("evidenceReference").trim(), 1000);
            if (summary.length() < 8 || evidence.length() < 3) throw new IllegalArgumentException("resolution-evidence-required");
            critical.put("resolutionSummary", summary).put("resolutionEvidence", evidence)
                    .put("resolvedAt", now).put("resolvedBy", actor.optString("id"))
                    .put("status", "resolution-pending-verification");
            auditAction = "critical-finding-resolution-submitted";
        } else if ("close".equals(action)) {
            credentials.requireRole("بازبین کنترل کیفیت", "سرپرست", "مدیر سیستم");
            requireQualified(actor, "*", true);
            if (!"resolution-pending-verification".equals(critical.optString("status")))
                throw new SecurityException("resolution-required-before-close");
            if (actor.optString("id").equals(critical.optString("resolvedBy")))
                throw new SecurityException("independent-closure-verification-required");
            critical.put("status", "closed").put("closedAt", now).put("closedBy", actor.optString("id"));
            auditAction = "critical-finding-closed";
        } else throw new IllegalArgumentException("invalid-critical-action");
        critical.put("updatedAt", now);
        SQLiteDatabase sql = db.getWritableDatabase();
        sql.beginTransaction();
        JSONObject event;
        try {
            AppDb.upsertEntity(sql, "criticalFindings", critical);
            event = audit(sql, actor, auditAction, "criticalFindings", caseId,
                    payload.optString("summary", ""), critical.optString("status"));
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
        credentials.touch();
        return successPatch("criticalFindings", critical, event);
    }

    private JSONObject appendAudit(JSONObject payload) throws Exception {
        JSONObject actor = credentials.requireAuthenticated();
        String kind = bounded(payload.optString("kind"), 80);
        String entityId = bounded(payload.optString("entityId"), 180);
        String action = bounded(payload.optString("action"), 160);
        if (!kind.matches("[A-Za-z0-9_\\-]{1,80}") || entityId.isEmpty() || action.isEmpty())
            throw new IllegalArgumentException("invalid-audit-event");
        SQLiteDatabase sql = db.getWritableDatabase();
        sql.beginTransaction();
        JSONObject event;
        try {
            event = audit(sql, actor, action, kind, entityId,
                    bounded(payload.optString("reason", payload.optString("detail", "")), 2400),
                    bounded(payload.optString("revision", ""), 120));
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
        credentials.touch();
        return successPatches(new JSONObject().put("audit", new JSONArray().put(event)));
    }

    private JSONObject updateProgramAfterApproval(SQLiteDatabase sql, JSONObject inspection, long now) throws Exception {
        String bridgeId = inspection.optString("bridgeId");
        String id = "program-" + bridgeId;
        JSONObject program = findInTransaction(sql, "inspectionPrograms", id);
        if (program == null) return null;
        LocalDate approvedDate;
        try { approvedDate = LocalDate.parse(inspection.optString("gdate")); }
        catch (Exception ignored) { approvedDate = LocalDate.now(ZoneOffset.UTC); }
        int months = Math.max(1, program.optInt("intervalMonths", 12));
        LocalDate next = approvedDate.plusMonths(months);
        String overrideText = program.optString("riskOverrideDueDate", "");
        if (!overrideText.isEmpty()) {
            program.put("fulfilledRiskOverrideDueDate", overrideText)
                    .put("fulfilledRiskOverrideReason", program.optString("riskOverrideReason"))
                    .put("riskOverrideDueDate", "")
                    .put("riskOverrideReason", "");
        }
        program.put("lastInspectionId", inspection.optString("id"))
                .put("lastInspectionDate", approvedDate.toString())
                .put("lastApprovedAt", now)
                .put("dueDate", next.toString())
                .put("effectiveDueDate", next.toString())
                .put("status", dueStatus(next))
                .put("lateReason", "")
                .put("updatedAt", now);
        AppDb.upsertEntity(sql, "inspectionPrograms", program);
        return program;
    }

    private static JSONArray validateRelatedEntities(JSONArray input, String inspectionId, String label) throws Exception {
        JSONArray result = new JSONArray();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < input.length(); index++) {
            JSONObject value = input.optJSONObject(index);
            if (value == null || !inspectionId.equals(value.optString("inspectionId")))
                throw new IllegalArgumentException(label + "-inspection-mismatch");
            String id = requiredId(value, label);
            if (!ids.add(id)) throw new IllegalArgumentException("duplicate-" + label);
            result.put(new JSONObject(value.toString()));
        }
        return result;
    }

    private static void validateInspectionItems(JSONObject inspection, JSONArray criticalCases) throws Exception {
        JSONArray items = inspection.optJSONArray("items");
        if (items == null || items.length() == 0) throw new IllegalArgumentException("inspection-checklist-empty");
        Set<String> emergencyKeys = new HashSet<>();
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) throw new IllegalArgumentException("invalid-inspection-item");
            boolean applicable = item.optBoolean("applicable", true);
            String severity = item.optString("statusId", "");
            if (!SEVERITY_IDS.contains(severity) || item.optBoolean("needsSeverityReview", false))
                throw new IllegalArgumentException("severity-needs-review");
            if (applicable && !item.optBoolean("assessed", false)) throw new IllegalArgumentException("unassessed-item");
            if ((!applicable || "uninspectable".equals(severity) || "emergency".equals(severity))
                    && blank(item.optString("note"))) throw new IllegalArgumentException("item-note-required");
            if ("emergency".equals(severity))
                emergencyKeys.add(item.optString("itemId") + "::" + Math.max(1, item.optInt("occurrenceIndex", 1)));
        }
        Set<String> caseKeys = new HashSet<>();
        for (int index = 0; index < criticalCases.length(); index++) {
            JSONObject critical = criticalCases.optJSONObject(index);
            if (critical == null) throw new IllegalArgumentException("invalid-critical-case");
            String key = critical.optString("itemId") + "::" + Math.max(1, critical.optInt("occurrenceIndex", 1));
            if (!caseKeys.add(key)) throw new IllegalArgumentException("duplicate-critical-case");
        }
        if (!caseKeys.equals(emergencyKeys)) throw new IllegalArgumentException("critical-case-required-for-every-emergency");
    }

    private JSONArray normalizeCriticalCases(JSONArray cases, JSONObject inspection, JSONArray defects, long now) throws Exception {
        Map<String, String> defectByKey = new HashMap<>();
        for (int index = 0; index < defects.length(); index++) {
            JSONObject defect = defects.getJSONObject(index);
            defectByKey.put(defect.optString("itemId") + "::" + Math.max(1, defect.optInt("occurrenceIndex", 1)), defect.optString("id"));
        }
        JSONArray result = new JSONArray();
        for (int index = 0; index < cases.length(); index++) {
            JSONObject value = new JSONObject(cases.getJSONObject(index).toString());
            String key = value.optString("itemId") + "::" + Math.max(1, value.optInt("occurrenceIndex", 1));
            String restriction = value.optString("operatingRestriction");
            if (!RESTRICTIONS.contains(restriction)
                    || blank(value.optString("restrictionRationale"))
                    || blank(value.optString("immediateAction"))
                    || blank(value.optString("notifiedContact"))
                    || blank(value.optString("notifiedAt"))
                    || blank(value.optString("ownerId"))
                    || blank(value.optString("dueAt"))
                    || blank(value.optString("discoveredAt")))
                throw new IllegalArgumentException("critical-case-safety-fields-required");
            Instant notified = Instant.parse(value.optString("notifiedAt"));
            Instant due = Instant.parse(value.optString("dueAt"));
            Instant discovered = Instant.parse(value.optString("discoveredAt"));
            if (notified.isBefore(discovered) || due.isBefore(discovered))
                throw new IllegalArgumentException("invalid-critical-timeline");
            if (notified.toEpochMilli() - discovered.toEpochMilli() > 86_400_000L
                    && bounded(value.optString("notificationLateReason").trim(), 1200).length() < 8)
                throw new IllegalArgumentException("critical-notification-late-reason-required");
            if (due.toEpochMilli() < now
                    && bounded(value.optString("overdueReason").trim(), 1200).length() < 8)
                throw new IllegalArgumentException("critical-overdue-reason-required");
            JSONObject owner = db.find("users", value.optString("ownerId"));
            if (owner == null || owner.optBoolean("archived", false))
                throw new IllegalArgumentException("critical-owner-not-available");
            String defectId = defectByKey.get(key);
            if (blank(defectId)) throw new IllegalArgumentException("critical-defect-link-required");
            String criticalId = "critical-" + inspection.optString("id") + "-" + sha256(key).substring(0, 16);
            JSONObject existing = db.find("criticalFindings", criticalId);
            JSONObject normalized = existing == null ? new JSONObject() : new JSONObject(existing.toString());
            for (String field : new String[]{"discoveredAt", "immediateAction", "operatingRestriction",
                    "restrictionRationale", "notifiedContact", "notifiedAt", "notificationLateReason",
                    "overdueReason", "ownerId", "dueAt", "itemId", "itemCode", "occurrenceIndex", "title"})
                if (value.has(field)) normalized.put(field, value.opt(field));
            normalized.put("id", criticalId)
                    .put("inspectionId", inspection.optString("id"))
                    .put("bridgeId", inspection.optString("bridgeId"))
                    .put("defectId", defectId)
                    .put("escalationStatus", due.toEpochMilli() < now ? "overdue" : "active")
                    .put("updatedAt", now);
            if (existing == null) normalized.put("status", "open").put("acknowledgmentStatus", "pending").put("createdAt", now);
            result.put(normalized);
        }
        return result;
    }

    private static void requireQualified(JSONObject user, String inspectionType, boolean review) throws Exception {
        JSONObject qualification = user.optJSONObject("qualification");
        if (qualification == null || !"approved".equals(qualification.optString("status")))
            throw new SecurityException("approved-qualification-required");
        validateQualificationFields(qualification);
        if (user.optString("id").equals(qualification.optString("approvedBy")))
            throw new SecurityException("independent-qualification-approval-required");
        LocalDate expiry = LocalDate.parse(qualification.optString("expiresAt"));
        if (expiry.isBefore(LocalDate.now(ZoneOffset.UTC))) throw new SecurityException("qualification-expired");
        if (review && !qualification.optBoolean("canReview", false)) throw new SecurityException("review-qualification-required");
        if (!review && !qualification.optBoolean("canInspect", true)) throw new SecurityException("inspection-qualification-required");
        if (!"*".equals(inspectionType)) {
            JSONArray allowed = qualification.optJSONArray("allowedInspectionTypes");
            boolean match = false;
            if (allowed != null) for (int index = 0; index < allowed.length(); index++) {
                String value = allowed.optString(index);
                if ("*".equals(value) || inspectionType.equals(value)) { match = true; break; }
            }
            if (!match) throw new SecurityException("inspection-type-not-authorized");
        }
    }

    private static void validateQualificationFields(JSONObject q) {
        if (blank(q.optString("discipline")) || blank(q.optString("degree"))
                || q.optInt("experienceYears", -1) < 0 || blank(q.optString("trainingCourse"))
                || blank(q.optString("certificateNo")) || blank(q.optString("expiresAt"))
                || q.optJSONArray("allowedInspectionTypes") == null
                || q.optJSONArray("allowedInspectionTypes").length() == 0)
            throw new IllegalArgumentException("qualification-fields-required");
        try { LocalDate.parse(q.optString("expiresAt")); }
        catch (Exception error) { throw new IllegalArgumentException("invalid-qualification-expiry"); }
    }

    private int countActiveManagers(String excludingId) throws Exception {
        JSONArray users = new JSONArray(db.list("users"));
        int count = 0;
        for (int index = 0; index < users.length(); index++) {
            JSONObject user = users.optJSONObject(index);
            if (user != null && !excludingId.equals(user.optString("id"))
                    && !user.optBoolean("archived", false) && "مدیر سیستم".equals(user.optString("role"))) count++;
        }
        return count;
    }

    private static JSONObject audit(SQLiteDatabase sql, JSONObject actor, String action, String kind,
                                    String entityId, String reason, String revision) throws Exception {
        return AuditLog.append(sql, new JSONObject()
                .put("id", "aud-" + UUID.randomUUID())
                .put("actorId", actor.optString("id"))
                .put("actorName", actor.optString("name"))
                .put("action", action)
                .put("kind", kind)
                .put("eid", entityId)
                .put("reason", bounded(reason, 2400))
                .put("revision", bounded(revision, 120))
                .put("at", System.currentTimeMillis()));
    }

    private static JSONObject successPatch(String kind, JSONObject entity, JSONObject event) throws Exception {
        return successPatches(new JSONObject()
                .put(kind, new JSONArray().put(entity))
                .put("audit", new JSONArray().put(event)));
    }

    private static JSONObject successPatches(JSONObject patches) throws Exception {
        return new JSONObject().put("ok", true).put("entities", patches);
    }

    private static void upsertAll(SQLiteDatabase sql, String kind, JSONArray entities) throws Exception {
        for (int index = 0; index < entities.length(); index++) AppDb.upsertEntity(sql, kind, entities.getJSONObject(index));
    }

    private static JSONObject findInTransaction(SQLiteDatabase sql, String kind, String id) {
        try (android.database.Cursor cursor = sql.query("entities", new String[]{"json"}, "kind=? AND eid=?", new String[]{kind, id}, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            return new JSONObject(cursor.getString(0));
        } catch (Exception error) { return null; }
    }

    private JSONObject approvedSnapshotBundle(JSONObject inspection) throws Exception {
        return new JSONObject()
                .put("inspection", new JSONObject(inspection.toString()))
                .put("defects", relatedEntities("defects", inspection.optString("id")))
                .put("criticalFindings", relatedEntities("criticalFindings", inspection.optString("id")));
    }

    private JSONArray relatedEntities(String kind, String inspectionId) throws Exception {
        JSONArray source = new JSONArray(db.list(kind));
        JSONArray result = new JSONArray();
        for (int index = 0; index < source.length(); index++) {
            JSONObject value = source.optJSONObject(index);
            if (value != null && inspectionId.equals(value.optString("inspectionId")))
                result.put(new JSONObject(value.toString()));
        }
        return result;
    }

    private static LocalDate parseDate(String value, String error) {
        try { return LocalDate.parse(value); }
        catch (Exception ignored) { throw new IllegalArgumentException(error); }
    }

    private static String dueStatus(LocalDate due) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (due.isBefore(today)) return "overdue";
        if (!due.isAfter(today.plusDays(30))) return "due-soon";
        return "scheduled";
    }

    private static String inspectionHash(JSONObject inspection) throws Exception {
        JSONObject copy = new JSONObject(inspection.toString());
        for (String key : new String[]{"fieldHash", "updatedAt", "qcReviewId", "qcReviewerId", "qcReviewer",
                "qcComment", "qcReviewedAt", "approvedAt", "approvedBy", "finalizedAt", "reportNo", "no",
                "officialReport", "status", "workflowStatus", "fieldLocked", "returnedAt"}) copy.remove(key);
        JSONObject signature = copy.optJSONObject("signatureAttachment");
        if (signature != null) {
            signature.remove("boundInspectionHash");
            signature.remove("boundAt");
        }
        return sha256(canonicalJson(copy));
    }

    private static String canonicalJson(Object value) throws Exception {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> keys = new ArrayList<>();
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            Collections.sort(keys);
            StringBuilder out = new StringBuilder("{");
            for (int index = 0; index < keys.size(); index++) {
                if (index > 0) out.append(',');
                String key = keys.get(index);
                out.append(JSONObject.quote(key)).append(':').append(canonicalJson(object.opt(key)));
            }
            return out.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder out = new StringBuilder("[");
            for (int index = 0; index < array.length(); index++) {
                if (index > 0) out.append(',');
                out.append(canonicalJson(array.opt(index)));
            }
            return out.append(']').toString();
        }
        if (value instanceof Number) return JSONObject.numberToString((Number) value);
        if (value instanceof Boolean) return value.toString();
        return JSONObject.quote(String.valueOf(value));
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(64);
        for (byte item : digest) out.append(String.format(Locale.US, "%02x", item & 0xff));
        return out.toString();
    }

    private static String requiredId(JSONObject object, String label) {
        String value = bounded(object.optString("id"), 180);
        if (value.isEmpty() || value.indexOf('\0') >= 0) throw new IllegalArgumentException(label + "-id-required");
        return value;
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static String bounded(String value, int length) {
        if (value == null) return "";
        return value.length() > length ? value.substring(0, length) : value;
    }
}
