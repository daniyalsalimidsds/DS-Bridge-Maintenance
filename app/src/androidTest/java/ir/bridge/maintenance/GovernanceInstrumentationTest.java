package ir.bridge.maintenance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class GovernanceInstrumentationTest {
    private static final String PIN_ADMIN = "135790";
    private static final String PIN_INSPECTOR = "246802";
    private static final String PIN_INSPECTOR_2 = "357913";
    private static final String PIN_REVIEWER = "975310";
    private static final String PIN_REVIEWER_2 = "864209";
    private static final String VISIT_TYPE = "بازدید ویژه";

    @Test public void independentQcIssuesUniqueNumberAndOfficialRecordsCannotBeDeleted() throws Exception {
        Context context = freshContext();
        try (AppDb db = new AppDb(context)) {
            CredentialManager credentials = new CredentialManager(db);
            InspectionGovernance governance = new InspectionGovernance(db, credentials);
            seedBridge(db, "bridge-qc");
            bootstrapTeam(governance, credentials);
            JSONObject limitedReviewer = qualifiedUser("reviewer-limited", "بازبین با دامنه محدود", "بازبین کنترل کیفیت", false, true);
            limitedReviewer.getJSONObject("qualification").put("allowedInspectionTypes", new JSONArray().put("بازدید ادواری"));
            governance.dispatch("saveGovernedUser", new JSONObject().put("user", limitedReviewer));
            credentials.enroll("reviewer-limited", "753159".toCharArray());

            JSONObject programResult = governance.dispatch("saveInspectionProgram", new JSONObject().put("program",
                    new JSONObject().put("bridgeId", "bridge-qc").put("intervalMonths", 6)
                            .put("intervalBasis", "owner-policy")
                            .put("dueDate", LocalDate.now(ZoneOffset.UTC).plusMonths(1).toString())
                            .put("riskOverrideDueDate", LocalDate.now(ZoneOffset.UTC).plusWeeks(1).toString())
                            .put("riskOverrideReason", "بازدید زودتر به علت پایش محافظه‌کارانه وضعیت عضو")));
            assertTrue(programResult.getBoolean("ok"));

            credentials.authenticate("inspector-1", PIN_INSPECTOR.toCharArray());
            JSONObject first = submit(governance, db, "inspection-qc-1", "bridge-qc", "low", false);
            assertEquals("submitted", first.getJSONObject("entities").getJSONArray("inspections")
                    .getJSONObject(0).getString("workflowStatus"));
            assertFalse(db.delete("inspections", "inspection-qc-1"));
            assertNotNull(db.find("reportRevisions", "submission-inspection-qc-1-1")
                    .getJSONObject("snapshotBundle"));

            expectFailure("independent-reviewer-required", () -> governance.dispatch("reviewInspection",
                    reviewPayload("inspection-qc-1", "approve")));

            credentials.authenticate("reviewer-limited", "753159".toCharArray());
            expectFailure("inspection-type-not-authorized", () -> governance.dispatch("reviewInspection",
                    reviewPayload("inspection-qc-1", "approve")));

            credentials.authenticate("reviewer-1", PIN_REVIEWER.toCharArray());
            JSONObject approvedFirst = governance.dispatch("reviewInspection", reviewPayload("inspection-qc-1", "approve"));
            String firstNumber = approvedFirst.getString("reportNo");
            assertTrue(firstNumber.matches("B-[0-9]{8}-[0-9]{3}"));
            assertTrue(db.isRegisteredReport("inspection-qc-1", firstNumber));
            assertEquals("approved", db.find("inspections", "inspection-qc-1").getString("workflowStatus"));
            assertFalse(db.delete("inspections", "inspection-qc-1"));
            assertFalse(db.delete("defects", "defect-inspection-qc-1"));
            JSONObject updatedProgram = db.find("inspectionPrograms", "program-bridge-qc");
            assertEquals("inspection-qc-1", updatedProgram.getString("lastInspectionId"));
            assertEquals(LocalDate.now(ZoneOffset.UTC).plusMonths(6).toString(), updatedProgram.getString("effectiveDueDate"));
            assertEquals("", updatedProgram.getString("riskOverrideDueDate"));
            assertEquals(LocalDate.now(ZoneOffset.UTC).plusWeeks(1).toString(), updatedProgram.getString("fulfilledRiskOverrideDueDate"));

            credentials.authenticate("inspector-1", PIN_INSPECTOR.toCharArray());
            submit(governance, db, "inspection-qc-2", "bridge-qc", "none", false);
            credentials.authenticate("reviewer-1", PIN_REVIEWER.toCharArray());
            String secondNumber = governance.dispatch("reviewInspection", reviewPayload("inspection-qc-2", "approve"))
                    .getString("reportNo");
            assertNotEquals(firstNumber, secondNumber);

            JSONObject persisted = db.find("inspections", "inspection-qc-1");
            JSONObject payload = new JSONObject().put("records", new JSONArray().put(new JSONObject(persisted.toString())))
                    .put("criticalFindings", new JSONArray());
            assertTrue(governance.reportPayloadIsOfficial(payload));
            payload.getJSONArray("records").getJSONObject(0).put("shift", "دستکاری‌شده");
            assertFalse(governance.reportPayloadIsOfficial(payload));

            JSONObject voided = governance.dispatch("voidInspection", new JSONObject()
                    .put("inspectionId", "inspection-qc-1")
                    .put("reason", "ابطال کنترل‌شده به علت خطای مستند در ورودی گزارش"));
            assertTrue(voided.getBoolean("ok"));
            assertNotNull(db.find("inspectionVoids", "void-inspection-qc-1"));
            assertFalse(governance.reportPayloadIsOfficial(new JSONObject()
                    .put("records", new JSONArray().put(persisted)).put("criticalFindings", new JSONArray())));
            assertTrue(AuditLog.verify(db.getReadableDatabase()));

            credentials.authenticate("inspector-1", PIN_INSPECTOR.toCharArray());
            JSONObject correction = governance.dispatch("createCorrectionDraft", new JSONObject()
                    .put("inspectionId", "inspection-qc-1")
                    .put("reason", "اصلاح رسمی مقدار اشتباه پس از ابطال گزارش اصلی"));
            JSONObject draft = db.find("inspections", correction.getString("draftId"));
            assertEquals("draft", draft.getString("workflowStatus"));
            assertEquals("inspection-qc-1", draft.getString("supersedesInspectionId"));
            assertFalse(draft.has("reportNo"));
            assertFalse(draft.has("signatureAttachment"));
        }
    }

    @Test public void credentialsAreSaltedLockedAndRoleChecked() throws Exception {
        Context context = freshContext();
        try (AppDb db = new AppDb(context)) {
            CredentialManager credentials = new CredentialManager(db);
            InspectionGovernance governance = new InspectionGovernance(db, credentials);
            JSONObject enrolledAdmin = credentials.enroll("user-admin", PIN_ADMIN.toCharArray());
            assertTrue(enrolledAdmin.getBoolean("authenticated"));
            governance.dispatch("saveGovernedUser", new JSONObject().put("user",
                    qualifiedUser("inspector-1", "بازرس آزمون", "بازرس", true, false)));
            credentials.enroll("inspector-1", PIN_ADMIN.toCharArray());

            AppDb.CredentialRecord admin = db.credential("user-admin");
            AppDb.CredentialRecord inspector = db.credential("inspector-1");
            assertEquals(16, admin.salt.length);
            assertEquals(32, admin.verifier.length);
            assertEquals(310_000, admin.iterations);
            assertFalse(Arrays.equals(admin.salt, inspector.salt));
            assertFalse(Arrays.equals(admin.verifier, PIN_ADMIN.getBytes(StandardCharsets.UTF_8)));

            credentials.lock();
            assertFalse(credentials.status().getBoolean("authenticated"));
            for (int attempt = 0; attempt < 5; attempt++) {
                JSONObject failed = credentials.authenticate("user-admin", "000000".toCharArray());
                assertFalse(failed.getBoolean("ok"));
            }
            JSONObject locked = credentials.authenticate("user-admin", PIN_ADMIN.toCharArray());
            assertFalse(locked.getBoolean("ok"));
            assertEquals("locked", locked.getString("error"));

            credentials.authenticate("inspector-1", PIN_ADMIN.toCharArray());
            expectFailure("role-not-authorized", () -> governance.dispatch("saveGovernedUser",
                    new JSONObject().put("user", qualifiedUser("forbidden-user", "کاربر غیرمجاز", "بازرس", true, false))));
        }
    }

    @Test public void auditChainRejectsMutationAndSurvivesVerifiedRestore() throws Exception {
        Context context = freshContext();
        try (AppDb db = new AppDb(context)) {
            CredentialManager credentials = new CredentialManager(db);
            InspectionGovernance governance = new InspectionGovernance(db, credentials);
            credentials.enroll("user-admin", PIN_ADMIN.toCharArray());
            governance.dispatch("appendAudit", new JSONObject().put("action", "qa-first")
                    .put("kind", "settings").put("entityId", "main").put("reason", "ثبت آزمون نخست"));
            governance.dispatch("appendAudit", new JSONObject().put("action", "qa-second")
                    .put("kind", "settings").put("entityId", "main").put("reason", "ثبت آزمون دوم"));
            JSONArray before = new JSONArray(db.list("audit"));
            assertEquals(2, before.length());
            assertTrue(AuditLog.verify(db.getReadableDatabase()));
            assertFalse(db.delete("audit", before.getJSONObject(0).getString("id")));

            boolean updateRejected = false;
            try { db.getWritableDatabase().execSQL("UPDATE audit_events SET reason='tampered'"); }
            catch (SQLiteException expected) { updateRejected = true; }
            assertTrue("SQL trigger must reject audit mutation", updateRejected);
            assertTrue(AuditLog.verify(db.getReadableDatabase()));

            JSONObject snapshot = new JSONObject(db.snapshotJson());
            db.restoreSnapshot(snapshot, db.mediaRows());
            assertEquals(2, new JSONArray(db.list("audit")).length());
            assertTrue(AuditLog.verify(db.getReadableDatabase()));
            assertEquals(0, db.credentialCount());
        }
    }

    @Test public void signatureMustBelongToAuthenticatedInspectorAndInspection() throws Exception {
        Context context = freshContext();
        try (AppDb db = new AppDb(context)) {
            CredentialManager credentials = new CredentialManager(db);
            InspectionGovernance governance = new InspectionGovernance(db, credentials);
            seedBridge(db, "bridge-signature");
            bootstrapTeam(governance, credentials);
            credentials.authenticate("inspector-1", PIN_INSPECTOR.toCharArray());

            String inspectionId = "inspection-signature";
            saveSignatureMedia(db, inspectionId);
            JSONObject item = new JSONObject().put("itemId", "member-1").put("itemCode", "1.1")
                    .put("itemName", "عضو اصلی").put("occurrenceIndex", 1).put("statusId", "none")
                    .put("assessed", true).put("applicable", true);
            JSONObject inspection = new JSONObject().put("id", inspectionId).put("bridgeId", "bridge-signature")
                    .put("visitType", VISIT_TYPE).put("inspectorId", "inspector-1").put("inspector", "بازرس آزمون")
                    .put("items", new JSONArray().put(item))
                    .put("signatureAttachment", new JSONObject().put("mediaId", "signature-" + inspectionId)
                            .put("signerId", "reviewer-1").put("signer", "بازبین آزمون"));
            JSONObject payload = new JSONObject().put("inspection", inspection)
                    .put("defects", new JSONArray()).put("criticalFindings", new JSONArray());
            expectFailure("signature-identity-mismatch", () -> governance.dispatch("submitInspection", payload));
            assertNull(db.find("inspections", inspectionId));

            inspection.getJSONObject("signatureAttachment")
                    .put("signerId", "inspector-1").put("signer", "بازرس آزمون");
            inspection.put("id", "inspection-signature-other");
            expectFailure("signature-evidence-not-found", () -> governance.dispatch("submitInspection", payload));
            assertNull(db.find("inspections", "inspection-signature-other"));
        }
    }

    @Test public void returnedInspectionReassignmentRequiresReasonAndIsAudited() throws Exception {
        Context context = freshContext();
        try (AppDb db = new AppDb(context)) {
            CredentialManager credentials = new CredentialManager(db);
            InspectionGovernance governance = new InspectionGovernance(db, credentials);
            seedBridge(db, "bridge-reassignment");
            bootstrapTeam(governance, credentials);
            governance.dispatch("saveGovernedUser", new JSONObject().put("user",
                    qualifiedUser("inspector-2", "بازرس دوم آزمون", "بازرس", true, false)));
            credentials.enroll("inspector-2", PIN_INSPECTOR_2.toCharArray());

            credentials.authenticate("inspector-1", PIN_INSPECTOR.toCharArray());
            submit(governance, db, "inspection-reassignment", "bridge-reassignment", "none", false);
            credentials.authenticate("reviewer-1", PIN_REVIEWER.toCharArray());
            governance.dispatch("reviewInspection", reviewPayload("inspection-reassignment", "return"));

            credentials.authenticate("inspector-1", PIN_INSPECTOR.toCharArray());
            JSONObject unchangedReturned = db.find("inspections", "inspection-reassignment");
            expectFailure("signature-must-be-recaptured-after-return", () -> governance.dispatch("submitInspection",
                    new JSONObject().put("inspection", unchangedReturned)
                            .put("defects", new JSONArray()).put("criticalFindings", new JSONArray())));

            credentials.authenticate("inspector-2", PIN_INSPECTOR_2.toCharArray());
            saveSignatureMedia(db, "inspection-reassignment");
            JSONObject returned = db.find("inspections", "inspection-reassignment");
            returned.put("inspectorId", "inspector-2").put("inspector", "بازرس دوم آزمون");
            returned.getJSONObject("signatureAttachment")
                    .put("signerId", "inspector-2").put("signer", "بازرس دوم آزمون");
            JSONObject payload = new JSONObject().put("inspection", returned)
                    .put("defects", new JSONArray()).put("criticalFindings", new JSONArray());
            expectFailure("inspector-reassignment-reason-required",
                    () -> governance.dispatch("submitInspection", payload));
            assertEquals("returned", db.find("inspections", "inspection-reassignment").getString("workflowStatus"));

            returned.put("inspectorReassignmentReason", "تغییر برنامه کاری و تحویل رسمی بازدید به بازرس واجد صلاحیت دوم");
            JSONObject resubmitted = governance.dispatch("submitInspection", payload)
                    .getJSONObject("entities").getJSONArray("inspections").getJSONObject(0);
            assertEquals("submitted", resubmitted.getString("workflowStatus"));
            assertEquals(2, resubmitted.getInt("submissionCycle"));
            JSONObject reassignment = resubmitted.getJSONArray("inspectorReassignments").getJSONObject(0);
            assertEquals("inspector-1", reassignment.getString("fromInspectorId"));
            assertEquals("inspector-2", reassignment.getString("toInspectorId"));
            assertTrue(new JSONArray(db.list("audit")).toString().contains("inspection-reassigned-and-submitted"));
        }
    }

    @Test public void emergencySubmissionRequiresCompleteLinkedCriticalCaseAndIndependentClosure() throws Exception {
        Context context = freshContext();
        try (AppDb db = new AppDb(context)) {
            CredentialManager credentials = new CredentialManager(db);
            InspectionGovernance governance = new InspectionGovernance(db, credentials);
            seedBridge(db, "bridge-critical");
            bootstrapTeam(governance, credentials);
            credentials.authenticate("inspector-1", PIN_INSPECTOR.toCharArray());

            expectFailure("critical-case-required-for-every-emergency",
                    () -> submit(governance, db, "inspection-critical-missing", "bridge-critical", "emergency", true));
            assertNull(db.find("inspections", "inspection-critical-missing"));

            JSONObject duplicatePayload = submissionPayload(db, "inspection-critical-duplicate", "bridge-critical", "emergency", false);
            JSONArray duplicateCases = duplicatePayload.getJSONArray("criticalFindings");
            duplicateCases.put(new JSONObject(duplicateCases.getJSONObject(0).toString()));
            expectFailure("duplicate-critical-case",
                    () -> governance.dispatch("submitInspection", duplicatePayload));
            assertNull(db.find("inspections", "inspection-critical-duplicate"));

            JSONObject overduePayload = submissionPayload(db, "inspection-critical-overdue", "bridge-critical", "emergency", false);
            JSONObject overdueCase = overduePayload.getJSONArray("criticalFindings").getJSONObject(0)
                    .put("discoveredAt", "2020-01-01T00:00:00Z")
                    .put("notifiedAt", "2020-01-01T00:30:00Z")
                    .put("dueAt", "2020-01-02T00:00:00Z");
            expectFailure("critical-overdue-reason-required",
                    () -> governance.dispatch("submitInspection", overduePayload));
            overdueCase.put("overdueReason", "تأخیر تاریخی مستند و ارجاع فوری برای تشدید پیگیری ایمنی");
            assertTrue(governance.dispatch("submitInspection", overduePayload).getBoolean("ok"));

            JSONObject result = submit(governance, db, "inspection-critical-1", "bridge-critical", "emergency", false);
            JSONArray cases = result.getJSONObject("entities").getJSONArray("criticalFindings");
            assertEquals(1, cases.length());
            JSONObject critical = cases.getJSONObject(0);
            assertEquals("open", critical.getString("status"));
            assertEquals("inspector-1", critical.getString("ownerId"));

            governance.dispatch("updateCriticalFinding", new JSONObject()
                    .put("caseId", critical.getString("id")).put("action", "acknowledge"));
            credentials.authenticate("reviewer-1", PIN_REVIEWER.toCharArray());
            governance.dispatch("updateCriticalFinding", new JSONObject()
                    .put("caseId", critical.getString("id")).put("action", "resolve")
                    .put("summary", "عضو موقتاً مهاربندی و مسیر تا کنترل نهایی مسدود شد")
                    .put("evidenceReference", "photo-critical-001"));
            expectFailure("independent-closure-verification-required",
                    () -> governance.dispatch("updateCriticalFinding", new JSONObject()
                            .put("caseId", critical.getString("id")).put("action", "close")));

            credentials.authenticate("reviewer-2", PIN_REVIEWER_2.toCharArray());
            governance.dispatch("updateCriticalFinding", new JSONObject()
                    .put("caseId", critical.getString("id")).put("action", "close"));
            JSONObject closed = db.find("criticalFindings", critical.getString("id"));
            assertEquals("closed", closed.getString("status"));
            governance.dispatch("reviewInspection", reviewPayload("inspection-critical-1", "approve"));
            JSONObject approved = db.find("inspections", "inspection-critical-1");
            JSONObject officialPayload = new JSONObject().put("records", new JSONArray().put(approved))
                    .put("criticalFindings", new JSONArray().put(closed));
            assertTrue(governance.reportPayloadIsOfficial(officialPayload));
            assertFalse(governance.reportPayloadIsOfficial(new JSONObject()
                    .put("records", new JSONArray().put(approved)).put("criticalFindings", new JSONArray())));
            officialPayload.getJSONArray("criticalFindings").getJSONObject(0).put("dueAt", "2099-01-01T00:00:00Z");
            assertFalse(governance.reportPayloadIsOfficial(officialPayload));
            assertTrue(AuditLog.verify(db.getReadableDatabase()));
        }
    }

    @Test public void v2MigrationPreservesAmbiguousSeverityAndRepairsDuplicateReportNumbers() throws Exception {
        Context context = freshContext();
        File dbFile = context.getDatabasePath(AppDb.DB_NAME);
        assertTrue(dbFile.getParentFile().exists() || dbFile.getParentFile().mkdirs());
        SQLiteDatabase raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        raw.execSQL("CREATE TABLE entities (kind TEXT NOT NULL, eid TEXT NOT NULL, json TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(kind,eid))");
        raw.execSQL("CREATE TABLE media (media_id TEXT PRIMARY KEY, owner_kind TEXT, owner_id TEXT, file_name TEXT NOT NULL, relative_path TEXT NOT NULL UNIQUE, mime TEXT NOT NULL, width INTEGER, height INTEGER, sha256 TEXT, created_at INTEGER NOT NULL)");
        raw.execSQL("CREATE TABLE migration_state (mkey TEXT PRIMARY KEY, mvalue TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        insertLegacy(raw, "defects", "legacy-defect", new JSONObject()
                .put("id", "legacy-defect").put("severity", "شدید"));
        insertLegacy(raw, "inspections", "legacy-final-1", new JSONObject()
                .put("id", "legacy-final-1").put("status", "نهایی").put("no", "DUP-100"));
        insertLegacy(raw, "inspections", "legacy-final-2", new JSONObject()
                .put("id", "legacy-final-2").put("status", "نهایی").put("no", "DUP-100"));
        raw.setVersion(2);
        raw.close();

        try (AppDb db = new AppDb(context)) {
            assertEquals(3, db.getWritableDatabase().getVersion());
            JSONObject defect = db.find("defects", "legacy-defect");
            assertEquals("unknown", defect.getString("severityId"));
            assertEquals("شدید", defect.getString("legacySeverityValue"));
            assertTrue(defect.getBoolean("needsSeverityReview"));
            JSONObject first = db.find("inspections", "legacy-final-1");
            JSONObject second = db.find("inspections", "legacy-final-2");
            assertNotEquals(first.getString("reportNo"), second.getString("reportNo"));
            assertEquals("approved", first.getString("workflowStatus"));
            assertEquals("legacy-unverified", first.getString("identityStatus"));
            assertTrue(db.isRegisteredReport(first.getString("id"), first.getString("reportNo")));
            assertTrue(db.isRegisteredReport(second.getString("id"), second.getString("reportNo")));
        }
    }

    private static JSONObject submit(InspectionGovernance governance, AppDb db, String inspectionId,
                                     String bridgeId, String severity, boolean omitCritical) throws Exception {
        return governance.dispatch("submitInspection", submissionPayload(db, inspectionId, bridgeId, severity, omitCritical));
    }

    private static JSONObject submissionPayload(AppDb db, String inspectionId,
                                                String bridgeId, String severity, boolean omitCritical) throws Exception {
        saveSignatureMedia(db, inspectionId);
        JSONObject item = new JSONObject().put("itemId", "member-1").put("itemCode", "1.1")
                .put("itemName", "عضو اصلی").put("occurrenceIndex", 1).put("statusId", severity)
                .put("assessed", true).put("applicable", true)
                .put("note", "مشاهده و مستندسازی میدانی کنترل‌شده");
        JSONObject inspection = new JSONObject().put("id", inspectionId).put("bridgeId", bridgeId)
                .put("bridgeName", "پل آزمون").put("visitType", VISIT_TYPE)
                .put("gdate", LocalDate.now(ZoneOffset.UTC).toString()).put("jdate", "۱۴۰۸/۰۱/۰۱")
                .put("shift", "روز").put("inspectorId", "inspector-1").put("inspector", "بازرس آزمون")
                .put("items", new JSONArray().put(item))
                .put("signatureAttachment", new JSONObject().put("mediaId", "signature-" + inspectionId)
                        .put("signerId", "inspector-1").put("signer", "بازرس آزمون"));
        JSONArray defects = "none".equals(severity) ? new JSONArray() : new JSONArray().put(new JSONObject()
                .put("id", "defect-" + inspectionId).put("inspectionId", inspectionId)
                .put("bridgeId", bridgeId).put("itemId", "member-1").put("occurrenceIndex", 1)
                .put("severityId", severity).put("title", "آسیب عضو اصلی"));
        JSONArray critical = new JSONArray();
        if ("emergency".equals(severity) && !omitCritical) {
            long now = System.currentTimeMillis();
            critical.put(new JSONObject().put("itemId", "member-1").put("itemCode", "1.1")
                    .put("occurrenceIndex", 1).put("title", "یافته بحرانی عضو اصلی")
                    .put("discoveredAt", java.time.Instant.ofEpochMilli(now).toString())
                    .put("immediateAction", "مسیر مسدود و محدوده ایمن‌سازی شد")
                    .put("operatingRestriction", "closure")
                    .put("restrictionRationale", "احتمال ناپایداری و خطر فوری بهره‌برداری")
                    .put("notifiedContact", "مرکز کنترل و مدیریت بحران")
                    .put("notifiedAt", java.time.Instant.ofEpochMilli(now + 60_000L).toString())
                    .put("ownerId", "inspector-1")
                    .put("dueAt", java.time.Instant.ofEpochMilli(now + 86_400_000L).toString()));
        }
        return new JSONObject().put("inspection", inspection)
                .put("defects", defects).put("criticalFindings", critical);
    }

    private static JSONObject reviewPayload(String inspectionId, String decision) throws Exception {
        return new JSONObject().put("inspectionId", inspectionId).put("decision", decision)
                .put("comment", "کنترل مستقل کامل‌بودن شواهد و سازگاری ارزیابی انجام شد");
    }

    private static void bootstrapTeam(InspectionGovernance governance, CredentialManager credentials) throws Exception {
        credentials.enroll("user-admin", PIN_ADMIN.toCharArray());
        governance.dispatch("saveGovernedUser", new JSONObject().put("user",
                qualifiedUser("inspector-1", "بازرس آزمون", "بازرس", true, false)));
        governance.dispatch("saveGovernedUser", new JSONObject().put("user",
                qualifiedUser("reviewer-1", "بازبین آزمون", "بازبین کنترل کیفیت", false, true)));
        governance.dispatch("saveGovernedUser", new JSONObject().put("user",
                qualifiedUser("reviewer-2", "بازبین دوم آزمون", "بازبین کنترل کیفیت", false, true)));
        credentials.enroll("inspector-1", PIN_INSPECTOR.toCharArray());
        credentials.enroll("reviewer-1", PIN_REVIEWER.toCharArray());
        credentials.enroll("reviewer-2", PIN_REVIEWER_2.toCharArray());
    }

    private static JSONObject qualifiedUser(String id, String name, String role,
                                            boolean canInspect, boolean canReview) throws Exception {
        return new JSONObject().put("id", id).put("name", name).put("personnelId", "P-" + id)
                .put("role", role).put("qualification", new JSONObject()
                        .put("status", "approved").put("discipline", "مهندسی پل")
                        .put("degree", "کارشناسی مهندسی").put("experienceYears", 5)
                        .put("trainingCourse", "دوره بازرسی پل سازمانی")
                        .put("certificateNo", "CERT-" + id)
                        .put("expiresAt", LocalDate.now(ZoneOffset.UTC).plusYears(2).toString())
                        .put("allowedInspectionTypes", new JSONArray().put(VISIT_TYPE))
                        .put("canInspect", canInspect).put("canReview", canReview));
    }

    private static void seedBridge(AppDb db, String bridgeId) throws Exception {
        assertTrue(db.save("bridges", bridgeId, new JSONObject().put("id", bridgeId)
                .put("name", "پل آزمون").put("code", "QA-01").toString()));
    }

    private static void saveSignatureMedia(AppDb db, String inspectionId) {
        AppDb.MediaRecord media = new AppDb.MediaRecord();
        media.mediaId = "signature-" + inspectionId;
        media.ownerKind = "signature";
        media.ownerId = inspectionId;
        media.fileName = media.mediaId + ".png";
        media.relativePath = "Signatures/" + media.fileName;
        media.mime = "image/png";
        media.width = 100;
        media.height = 40;
        media.sha256 = "qa-signature";
        media.createdAt = System.currentTimeMillis();
        db.saveMedia(media);
    }

    private static void insertLegacy(SQLiteDatabase db, String kind, String id, JSONObject value) {
        ContentValues row = new ContentValues();
        row.put("kind", kind);
        row.put("eid", id);
        row.put("json", value.toString());
        row.put("updated_at", 1L);
        db.insertOrThrow("entities", null, row);
    }

    private static Context freshContext() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(AppDb.DB_NAME);
        return context;
    }

    private static void expectFailure(String code, ThrowingRunnable operation) throws Exception {
        try {
            operation.run();
            fail("Expected failure: " + code);
        } catch (Exception expected) {
            assertEquals(code, expected.getMessage());
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
