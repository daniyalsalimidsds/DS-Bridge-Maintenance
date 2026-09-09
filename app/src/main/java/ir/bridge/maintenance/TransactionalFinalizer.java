package ir.bridge.maintenance;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Atomically commits an inspection finalization bundle.
 *
 * The existing draft is left untouched if any entity in the bundle is invalid or any
 * SQLite write fails. This keeps inspection, defects, reminders and audit metadata from
 * diverging after a crash or storage error.
 */
public final class TransactionalFinalizer {
    private TransactionalFinalizer() {}

    public static void commit(AppDb db, JSONObject inspection, JSONArray defects,
                              JSONArray reminders, JSONObject audit) throws Exception {
        if (db == null || inspection == null) throw new IllegalArgumentException("Missing inspection finalization payload");
        validateEntity(inspection, "inspection");
        if (!"نهایی".equals(inspection.optString("status"))) throw new IllegalArgumentException("Inspection is not final");
        if (blank(inspection.optString("bridgeId"))) throw new IllegalArgumentException("Inspection bridge is missing");
        validateInspectionBusinessRules(inspection);
        if (defects == null) defects = new JSONArray();
        if (reminders == null) reminders = new JSONArray();
        if (audit == null) throw new IllegalArgumentException("Missing finalization audit record");
        String inspectionId=inspection.optString("id");
        for (int i=0;i<defects.length();i++) {
            JSONObject defect=requireObject(defects,i,"defect"); validateEntity(defect,"defect");
            if(!inspectionId.equals(defect.optString("inspectionId"))) throw new IllegalArgumentException("Defect belongs to another inspection");
        }
        for (int i=0;i<reminders.length();i++) {
            JSONObject reminder=requireObject(reminders,i,"reminder"); validateEntity(reminder,"reminder");
            if(!inspectionId.equals(reminder.optString("ownerId"))) throw new IllegalArgumentException("Reminder belongs to another inspection");
        }
        validateEntity(audit, "audit");
        if(!"inspections".equals(audit.optString("kind")) || !inspectionId.equals(audit.optString("eid"))) throw new IllegalArgumentException("Audit record does not match inspection");

        SQLiteDatabase sql = db.getWritableDatabase();
        sql.beginTransaction();
        try {
            upsert(sql, "inspections", inspection);
            for (int i=0;i<defects.length();i++) upsert(sql, "defects", defects.getJSONObject(i));
            for (int i=0;i<reminders.length();i++) upsert(sql, "reminders", reminders.getJSONObject(i));
            upsert(sql, "audit", audit);
            sql.setTransactionSuccessful();
        } finally {
            sql.endTransaction();
        }
    }

    private static void validateInspectionBusinessRules(JSONObject inspection) {
        JSONObject signature=inspection.optJSONObject("signatureAttachment");
        if(signature==null || blank(signature.optString("mediaId")))
            throw new IllegalArgumentException("Inspector signature is required");
        if(blank(inspection.optString("inspector")))
            throw new IllegalArgumentException("Manual inspector name is required");
        if(!inspection.optString("inspector").trim().equals(signature.optString("signer").trim()))
            throw new IllegalArgumentException("Inspector name does not match signature signer");
        JSONArray items=inspection.optJSONArray("items");
        boolean scored="1.6.0".equals(inspection.optString("checklistSchemaVersion"));
        if(scored && (items==null || items.length()==0))throw new IllegalArgumentException("Inspection checklist is empty");
        if(scored && (!"1.6.0".equals(inspection.optString("scoringVersion")) || inspection.optJSONObject("scores")==null))
            throw new IllegalArgumentException("Scoring snapshot is missing");
        if(items!=null) for(int i=0;i<items.length();i++) {
            JSONObject item=items.optJSONObject(i); if(item==null) throw new IllegalArgumentException("Invalid inspection item");
            String severity=item.optString("statusId","none");
            if(!isSeverityId(severity)) throw new IllegalArgumentException("Invalid severity id");
            if(scored){
                boolean applicable=item.optBoolean("applicable",true);
                if(applicable && !item.optBoolean("assessed",false))throw new IllegalArgumentException("Unassessed inspection item");
                if((!applicable || "uninspectable".equals(severity) || "emergency".equals(severity)) && blank(item.optString("note")))
                    throw new IllegalArgumentException("Evidence or exclusion reason is missing");
            }
        }
    }

    private static boolean blank(String s){return s==null||s.trim().isEmpty();}
    private static boolean isSeverityId(String s){return "none".equals(s)||"low".equals(s)||"medium".equals(s)||"emergency".equals(s)||"uninspectable".equals(s);}

    private static JSONObject requireObject(JSONArray a,int i,String label) throws Exception {
        JSONObject o=a.optJSONObject(i); if(o==null) throw new IllegalArgumentException("Invalid "+label+" object at index "+i); return o;
    }

    private static void validateEntity(JSONObject o,String label) {
        String id=o.optString("id","");
        if(id.isEmpty() || id.length()>180 || id.indexOf('\0')>=0) throw new IllegalArgumentException("Invalid "+label+" id");
    }

    private static void upsert(SQLiteDatabase sql,String kind,JSONObject o) {
        ContentValues v=new ContentValues();
        v.put("kind",kind); v.put("eid",o.optString("id")); v.put("json",o.toString());
        v.put("updated_at",o.optLong("updatedAt",System.currentTimeMillis()));
        long result=sql.insertWithOnConflict("entities",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        if(result==-1) throw new IllegalStateException("Finalization write failed for "+kind+"/"+o.optString("id"));
    }
}
