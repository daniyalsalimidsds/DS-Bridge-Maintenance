package ir.bridge.maintenance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class GestureInstrumentationTest {
    @Test
    public void physicalHorizontalSwipesOpenAndCloseDrawersFromPageContent() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            waitForWebAppReady(s);
            UiDevice d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            d.waitForIdle();
            eval(s, "closeDrawers();'ok'");
            assertEquals("none", waitForValue(s, drawerStateJs(), "none", 2_000));

            int w = d.getDisplayWidth(), h = d.getDisplayHeight(), y = h * 2 / 3;

            // Right-to-left swipe from ordinary page content must open the right drawer.
            d.swipe(w * 3 / 4, y, w / 4, y, 30);
            assertEquals("right", waitForValue(s, drawerStateJs(), "right", 3_000));

            // Reverse swipe closes it.
            d.swipe(w / 4, y, w * 3 / 4, y, 30);
            assertEquals("none", waitForValue(s, drawerStateJs(), "none", 3_000));

            // Left-to-right swipe from ordinary page content must open the left drawer.
            d.swipe(w / 4, y, w * 3 / 4, y, 30);
            assertEquals("left", waitForValue(s, drawerStateJs(), "left", 3_000));

            // Reverse swipe closes it.
            d.swipe(w * 3 / 4, y, w / 4, y, 30);
            assertEquals("none", waitForValue(s, drawerStateJs(), "none", 3_000));
        }
    }

    @Test
    public void verticalAndSystemTopEdgeDoNotOpenAppDrawer() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            waitForWebAppReady(s);
            eval(s, "closeDrawers();'ok'");
            UiDevice d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            d.waitForIdle();
            int w = d.getDisplayWidth(), h = d.getDisplayHeight();
            d.swipe(w / 2, h / 3, w / 2, h * 2 / 3, 20);
            SystemClock.sleep(250);
            assertEquals("none", drawerState(s));
            d.swipe(w / 2, 1, w / 2, Math.min(h / 2, 500), 18);
            SystemClock.sleep(250);
            assertEquals("none", drawerState(s));
        }
    }

    @Test
    public void newInspectionCategoriesAreCollapsedAndToggle() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            waitForWebAppReady(s);
            seedBridge(s);eval(s, "newInspection('qa-bridge');'started'");
            assertEquals("present", waitForValue(s,
                    "document.querySelector('#checklistHost .checkcat')?'present':'missing'",
                    "present", 3_000));
            assertEquals("collapsed", eval(s,
                    "(()=>{const c=document.querySelector('#checklistHost .checkcat');" +
                    "return c&&c.classList.contains('collapsed')&&c.querySelector('.checkcat-body')?.hidden&&" +
                    "c.querySelector('.checkcat-title')?.getAttribute('aria-expanded')==='false'?'collapsed':'bad'})()"));
            assertEquals("expanded", eval(s,
                    "(()=>{const b=document.querySelector('#checklistHost .checkcat .checkcat-title');" +
                    "b.click();const c=b.closest('.checkcat');return !c.classList.contains('collapsed')&&" +
                    "!c.querySelector('.checkcat-body').hidden&&b.getAttribute('aria-expanded')==='true'?'expanded':'bad'})()"));
            assertEquals("collapsed", eval(s,
                    "(()=>{const b=document.querySelector('#checklistHost .checkcat .checkcat-title');" +
                    "b.click();const c=b.closest('.checkcat');return c.classList.contains('collapsed')&&" +
                    "c.querySelector('.checkcat-body').hidden&&b.getAttribute('aria-expanded')==='false'?'collapsed':'bad'})()"));
        }
    }

    @Test
    public void checklistOrderUsesNumericCodes() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            waitForWebAppReady(s);
            seedBridge(s);eval(s, "newInspection('qa-bridge');'started'");
            assertEquals("expanded",eval(s,"(()=>{const button=document.querySelector('#checklistHost .checkcat-title');if(!button)return 'missing';button.click();return button.getAttribute('aria-expanded')==='true'?'expanded':'bad'})()"));
            assertEquals("present", waitForValue(s,
                    "document.querySelector('#checklistHost .checkitem[data-code]')?'present':'missing'",
                    "present", 3_000));
            assertEquals("sorted", eval(s,
                    "(()=>{const a=[...document.querySelectorAll('#checklistHost .checkitem')].map(x=>x.dataset.code).filter(Boolean);" +
                    "const b=[...a].sort(compareChecklistCodes);return a.length>0&&JSON.stringify(a)===JSON.stringify(b)?'sorted':'bad'})()"));
        }
    }

    @Test
    public void physicalBottomSheetDragClosesSheet() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            waitForWebAppReady(s);
            eval(s, "showGeneric('آزمون','<p>gesture</p>',[]);'ok'");
            assertEquals("visible",waitForValue(s,"(()=>{const r=document.querySelector('#genericModal .sheet').getBoundingClientRect();return r.top>=0&&r.top<innerHeight&&r.bottom<=innerHeight+1?'visible':'outside'})()","visible",2_000));
            eval(s, "window.__qaSheetEvents=[];['touchstart','touchmove','touchend','touchcancel','pointerdown','pointermove','pointerup','pointercancel'].forEach(t=>document.addEventListener(t,e=>{const p=e.touches?.[0]||e.changedTouches?.[0]||e;window.__qaSheetEvents.push(t+':'+Math.round(p.clientX)+','+Math.round(p.clientY));},{capture:true,passive:true}));'armed'");
            UiDevice d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            d.waitForIdle();
            int[] viewport = webViewportOnScreen(s);
            String pos=eval(s,"(()=>{const b=document.querySelector('#genericModal .sheetbar');const r=b.getBoundingClientRect();return ((r.left+r.width/2)/innerWidth)+','+((r.top+r.height/2)/innerHeight)})()");
            String[] xy=pos.split(",");
            int sx=viewport[0]+(int)(Double.parseDouble(xy[0])*viewport[2]);
            int sy=viewport[1]+(int)(Double.parseDouble(xy[1])*viewport[3]);
            d.swipe(sx,sy,sx,Math.min(viewport[1]+viewport[3]-40,sy+viewport[3]/3),24);
            String state=waitForValue(s,
                    "document.getElementById('genericModal').classList.contains('open')?'open':'closed'",
                    "closed", 3_000);
            String debug=eval(s,"JSON.stringify({events:window.__qaSheetEvents,native:window.__bridgeNativeSheetDebug,rect:(()=>{const r=document.querySelector('#genericModal .sheet').getBoundingClientRect();return {top:r.top,bottom:r.bottom,height:r.height,innerHeight}})()})");
            assertEquals("sheet gesture diagnostics: "+debug+"; screen="+sx+","+sy+" viewport="+java.util.Arrays.toString(viewport),"closed",state);
        }
    }

    private static void waitForWebAppReady(ActivityScenario<MainActivity> s) throws Exception {
        String readyJs = "(()=>{try{return document.readyState==='complete'&&" +
                "typeof window.BridgeNativeClient==='object'&&" +
                "window.BridgeNativeClient.appVersion().indexOf('1.5.0')===0&&" +
                "typeof window.newInspection==='function'&&typeof window.openDrawer==='function'&&" +
                "typeof window.closeDrawers==='function'&&typeof window.compareChecklistCodes==='function'&&" +
                "typeof window.initSwipeNavigation==='function'&&window.initSwipeNavigation._done===true?" +
                "'ready':'wait'}catch(e){return 'wait'}})()";
        assertEquals("ready", waitForValue(s, readyJs, "ready", 30_000));
        SystemClock.sleep(250);
    }

    private static void seedBridge(ActivityScenario<MainActivity> s) throws Exception {
        eval(s,"(()=>{const fs=BRIDGE_PROFILE_SCHEMA.sections.flatMap(x=>x.fields),fid=l=>fs.find(x=>x.label===l).id,b={id:'qa-bridge',name:'پل آزمون',code:'QA-01',use:'راه',profile:{}};b.profile[fid('نام پل')]=b.name;b.profile[fid('کد پل')]=b.code;b.profile[fid('کاربری اصلی')]=b.use;b.profile[fid('مصالح اَبَرسازه')]='بتن درجا - بتن مسلح';b.profile[fid('نوع روسازی')]='آسفالت';dbSave('bridges',b);return 'ok'})()");
    }

    private static String drawerStateJs() {
        return "document.getElementById('leftDrawer').classList.contains('open')?'left':" +
                "(document.getElementById('rightDrawer').classList.contains('open')?'right':'none')";
    }

    private static String drawerState(ActivityScenario<MainActivity> s) throws Exception {
        return eval(s, drawerStateJs());
    }

    private static int[] webViewportOnScreen(ActivityScenario<MainActivity> s) {
        AtomicReference<int[]> out = new AtomicReference<>();
        s.onActivity(a -> {
            WebView v = findWebView(a.findViewById(android.R.id.content));
            assertTrue(v != null);
            int[] location = new int[2];v.getLocationOnScreen(location);
            out.set(new int[]{location[0]+v.getPaddingLeft(),location[1]+v.getPaddingTop(),
                    v.getWidth()-v.getPaddingLeft()-v.getPaddingRight(),
                    v.getHeight()-v.getPaddingTop()-v.getPaddingBottom()});
        });
        return out.get();
    }

    private static String waitForValue(ActivityScenario<MainActivity> s, String js, String wanted, long timeoutMs) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        String value = "";
        while (SystemClock.elapsedRealtime() < deadline) {
            value = eval(s, js);
            if (wanted.equals(value)) return value;
            SystemClock.sleep(150);
        }
        return value;
    }

    private static String eval(ActivityScenario<MainActivity> s, String js) throws Exception {
        AtomicReference<String> out = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        s.onActivity(a -> {
            WebView v = findWebView(a.findViewById(android.R.id.content));
            assertTrue(v != null);
            v.evaluateJavascript(js, r -> {
                String value = r == null ? "" : r;
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1)
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                            .replace("\\\\", "\\");
                }
                out.set(value);
                done.countDown();
            });
        });
        assertTrue(done.await(20, TimeUnit.SECONDS));
        return out.get();
    }

    private static WebView findWebView(android.view.View v) {
        if (v instanceof WebView) return (WebView) v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                WebView w = findWebView(g.getChildAt(i));
                if (w != null) return w;
            }
        }
        return null;
    }
}
