package ir.bridge.maintenance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Regression coverage for the user-visible delete path. The final assertion
 * is made after destroying and relaunching the activity, so an optimistic UI
 * update cannot make a failed SQLite delete look successful. */
@RunWith(AndroidJUnit4.class)
public class DeletionPersistenceInstrumentationTest {
    @Test public void directDeleteIsPersistedAcrossActivityRestart() throws Exception {
        String id = "qa-v150-delete-" + System.currentTimeMillis();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitReady(scenario);
            assertEquals("ready", eval(scenario, "typeof BridgeAndroid?.deleteBatch==='function'?'ready':'missing'"));
            eval(scenario, "(()=>{dbSave('bridges',{id:'" + id + "-bridge',name:'پل حذف مستقیم',code:'QA-DEL',profile:{}});dbSave('inspections',{id:'" + id + "-inspection',status:'پیش‌نویس',bridgeId:'" + id + "-bridge'});dbSave('defects',{id:'" + id + "-defect',inspectionId:'" + id + "-inspection'});dbSave('reminders',{id:'" + id + "-reminder',ownerId:'" + id + "-inspection'});dbSave('audit',{id:'" + id + "-audit',eid:'" + id + "-inspection'});return 'saved'})()");
            // dbSave uses the WebMessage write path; allow its transaction to
            // reach SQLite before exercising the synchronous delete endpoint.
            SystemClock.sleep(700);
            assertEquals("started", eval(scenario, "(()=>{dbDeleteBatch([{kind:'bridges',id:'" + id + "-bridge'},{kind:'inspections',id:'" + id + "-inspection'},{kind:'defects',id:'" + id + "-defect'},{kind:'reminders',id:'" + id + "-reminder'},{kind:'audit',id:'" + id + "-audit'}]).then(r=>window.__qaDeleteResult=r.ok?'ok':'bad');return 'started'})()"));
            assertEquals("ok", waitForValue(scenario, "window.__qaDeleteResult||'wait'", "ok", 5000));
            assertEquals("gone", eval(scenario, "['bridges','inspections','defects','reminders','audit'].every(k=>!dbList(k).some(x=>String(x.id).startsWith('" + id + "-')))?'gone':'present'"));
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitReady(scenario);
            assertEquals("gone", eval(scenario, "['bridges','inspections','defects','reminders','audit'].every(k=>!dbList(k).some(x=>String(x.id).startsWith('" + id + "-')))?'gone':'present'"));
        }
    }

    private static String waitForValue(ActivityScenario<MainActivity> scenario, String js, String expected, long timeoutMs) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        String value = "";
        while (SystemClock.elapsedRealtime() < deadline) {
            value = eval(scenario, js);
            if (expected.equals(value)) return value;
            SystemClock.sleep(100);
        }
        return value;
    }

    private static void waitReady(ActivityScenario<MainActivity> scenario) throws Exception {
        // The first WebView process on a newly booted CI emulator needs more
        // time than subsequent launches; keep the functional assertions intact.
        long deadline = SystemClock.elapsedRealtime() + 30000;
        String value = "";
        while (SystemClock.elapsedRealtime() < deadline) {
            value = eval(scenario, "document.readyState==='complete'&&typeof dbList==='function'&&typeof newInspection==='function'?'ready':'wait'");
            if ("ready".equals(value)) return;
            SystemClock.sleep(150);
        }
        assertEquals("Startup diagnostics: " + eval(scenario, "JSON.stringify({state:document.readyState,url:location.href,db:typeof dbList,inspection:typeof newInspection,version:window.BridgeNativeClient?.appVersion()})"), "ready", value);
    }

    private static String eval(ActivityScenario<MainActivity> scenario, String js) throws Exception {
        AtomicReference<String> out = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        scenario.onActivity(activity -> {
            WebView web = findWebView(activity.findViewById(android.R.id.content));
            assertTrue(web != null);
            web.evaluateJavascript(js, result -> {
                String value = result == null ? "" : result;
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
                }
                out.set(value);
                done.countDown();
            });
        });
        assertTrue(done.await(20, TimeUnit.SECONDS));
        return out.get();
    }

    private static WebView findWebView(android.view.View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView found = findWebView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
