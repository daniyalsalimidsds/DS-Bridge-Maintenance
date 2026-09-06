package ir.bridge.maintenance;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TransactionalFinalizerInstrumentationTest {
    @Test public void invalidBundleLeavesExistingDraftUntouchedThenValidBundleCommitsTogether() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();
        c.deleteDatabase(AppDb.DB_NAME);
        try(AppDb db=new AppDb(c)) {
            JSONObject draft=new JSONObject().put("id","ins-tx-1").put("status","پیش‌نویس").put("updatedAt",1L);
            assertTrue(db.save("inspections","ins-tx-1",draft.toString()));

            JSONObject fin=new JSONObject(draft.toString()).put("status","نهایی").put("no","B-TX-1").put("bridgeId","bridge-tx-1").put("updatedAt",2L)
                    .put("inspector","بازرس نمونه")
                    .put("signatureAttachment",new JSONObject().put("mediaId","signature-tx-1").put("signer","بازرس نمونه"));
            JSONArray badDefects=new JSONArray().put(new JSONObject().put("title","missing id"));
            JSONObject audit=new JSONObject().put("id","aud-tx-1").put("action","نهایی‌سازی بازدید").put("kind","inspections").put("eid","ins-tx-1").put("updatedAt",2L);
            boolean failed=false;
            try { TransactionalFinalizer.commit(db,fin,badDefects,new JSONArray(),audit); }
            catch(IllegalArgumentException expected){ failed=true; }
            assertTrue("invalid finalization bundle must fail",failed);
            JSONObject stillDraft=find(db,"inspections","ins-tx-1");
            assertNotNull(stillDraft); assertEquals("پیش‌نویس",stillDraft.getString("status"));
            assertEquals(0,new JSONArray(db.list("defects")).length());

            JSONObject defect=new JSONObject().put("id","def-tx-1").put("inspectionId","ins-tx-1").put("severityId","emergency").put("updatedAt",2L);
            JSONObject reminder=new JSONObject().put("id","deadline-ins-tx-1").put("ownerId","ins-tx-1").put("timestamp",System.currentTimeMillis()+86400000L).put("active",true).put("updatedAt",2L);
            TransactionalFinalizer.commit(db,fin,new JSONArray().put(defect),new JSONArray().put(reminder),audit);

            assertEquals("نهایی",find(db,"inspections","ins-tx-1").getString("status"));
            assertNotNull(find(db,"defects","def-tx-1"));
            assertNotNull(find(db,"reminders","deadline-ins-tx-1"));
            assertNotNull(find(db,"audit","aud-tx-1"));
        }
    }

    @Test public void missingInspectorSignatureIsRejectedWithoutOverwritingDraft() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();
        c.deleteDatabase(AppDb.DB_NAME);
        try(AppDb db=new AppDb(c)) {
            JSONObject draft=new JSONObject().put("id","ins-tx-2").put("status","پیش‌نویس").put("updatedAt",1L);
            assertTrue(db.save("inspections","ins-tx-2",draft.toString()));
            JSONObject fin=new JSONObject(draft.toString()).put("status","نهایی").put("bridgeId","bridge-tx-2").put("updatedAt",2L)
                    .put("inspector","بازرس نمونه")
                    .put("items",new JSONArray().put(new JSONObject().put("itemId","steel-1").put("statusId","emergency")));
            JSONObject audit=new JSONObject().put("id","aud-tx-2").put("kind","inspections").put("eid","ins-tx-2").put("updatedAt",2L);
            boolean failed=false;
            try { TransactionalFinalizer.commit(db,fin,new JSONArray(),new JSONArray(),audit); }
            catch(IllegalArgumentException expected){ failed=true; }
            assertTrue(failed);
            assertEquals("پیش‌نویس",find(db,"inspections","ins-tx-2").getString("status"));
            assertNull(find(db,"audit","aud-tx-2"));
        }
    }

    @Test public void emergencyBundleNeedsOnlyInspectorSignatureAndValidSeverity() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();
        c.deleteDatabase(AppDb.DB_NAME);
        try(AppDb db=new AppDb(c)) {
            JSONObject fin=new JSONObject().put("id","ins-tx-3").put("status","نهایی").put("bridgeId","bridge-tx-3").put("updatedAt",3L)
                    .put("inspector","بازرس نمونه")
                    .put("signatureAttachment",new JSONObject().put("mediaId","signature-tx-3").put("signer","بازرس نمونه"))
                    .put("items",new JSONArray().put(new JSONObject().put("itemId","steel-1").put("statusId","emergency")));
            JSONObject audit=new JSONObject().put("id","aud-tx-3").put("kind","inspections").put("eid","ins-tx-3").put("updatedAt",3L);
            TransactionalFinalizer.commit(db,fin,new JSONArray(),new JSONArray(),audit);
            assertEquals("نهایی",find(db,"inspections","ins-tx-3").getString("status"));
            assertNotNull(find(db,"audit","aud-tx-3"));
        }
    }

    @Test public void inspectorNameMustMatchStoredSignatureSigner() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();c.deleteDatabase(AppDb.DB_NAME);
        try(AppDb db=new AppDb(c)) {
            JSONObject fin=new JSONObject().put("id","ins-tx-4").put("status","نهایی").put("bridgeId","bridge-tx-4").put("updatedAt",4L)
                    .put("inspector","بازرس دوم")
                    .put("signatureAttachment",new JSONObject().put("mediaId","signature-tx-4").put("signer","بازرس اول"));
            JSONObject audit=new JSONObject().put("id","aud-tx-4").put("kind","inspections").put("eid","ins-tx-4").put("updatedAt",4L);
            boolean rejected=false;try{TransactionalFinalizer.commit(db,fin,new JSONArray(),new JSONArray(),audit);}catch(IllegalArgumentException expected){rejected=true;}
            assertTrue(rejected);assertNull(find(db,"inspections","ins-tx-4"));assertNull(find(db,"audit","aud-tx-4"));
        }
    }

    private static JSONObject find(AppDb db,String kind,String id)throws Exception {
        JSONArray a=new JSONArray(db.list(kind));
        for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(id.equals(o.optString("id")))return o;}
        return null;
    }
}
