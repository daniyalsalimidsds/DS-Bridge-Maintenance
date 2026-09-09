package ir.bridge.maintenance;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.location.LocationCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends ComponentActivity {
    private static final long MAX_BRIDGE_MESSAGE=6_000_000L;
    private WebView webView;private AppDb db;private AppMediaStore mediaStore;private ReportExporter reportExporter;private BackupManager backupManager;private WebViewAssetLoader assetLoader;private final ExecutorService io=Executors.newSingleThreadExecutor();private volatile boolean migrationDone=false,pageReady=false;
    private File pendingExportFile;private String pendingExportName="bridge-inspection-report.bin";private PhotoContext pendingPhoto;private File pendingCameraFile;private Uri pendingBackupUri;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPicker;private ActivityResultLauncher<Uri> cameraLauncher;private ActivityResultLauncher<String[]> backupPicker,bridgePicker;private ActivityResultLauncher<String> pdfDocumentLauncher,csvDocumentLauncher,xlsxDocumentLauncher,backupDocumentLauncher;private ActivityResultLauncher<String> notificationPermission;private ActivityResultLauncher<String[]> locationPermission;private String pendingLocationTarget="";private long locationRequestToken=0L;

    @Override protected void onCreate(Bundle savedInstanceState){super.onCreate(savedInstanceState);WindowCompat.setDecorFitsSystemWindows(getWindow(),false);registerActivityResults();createNotificationChannel();if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)){new AlertDialog.Builder(this).setTitle("به‌روزرسانی مؤلفه نمایش لازم است").setMessage("Android System WebView یا Chrome را از فروشگاه معتبر گوشی به‌روزرسانی کنید و برنامه را دوباره باز کنید. اطلاعات ذخیره‌شده شما حفظ می‌شود.").setPositiveButton("بستن",(dialog,which)->finish()).setOnCancelListener(dialog->finish()).show();return;}db=new AppDb(getApplicationContext());mediaStore=new AppMediaStore(getApplicationContext(),db);reportExporter=new ReportExporter(getApplicationContext(),mediaStore);backupManager=new BackupManager(getApplicationContext(),db,mediaStore);configureWebView();configureBack();setContentView(webView);applyImmersiveMode();io.execute(()->{InlinePhotoMigrator.MigrationResult r=new InlinePhotoMigrator(db,mediaStore).run();migrationDone=true;runOnUiThread(()->{maybeBootstrap();if(r.failures>0)toast("انتقال برخی تصاویر قدیمی کامل نشد؛ داده اصلی حذف نشده است.");});});webView.loadUrl(BuildConfig.APP_ORIGIN+"/assets/index.html");}

    private void configureWebView(){
        assetLoader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).addPathHandler("/media-thumb/",path->{try{String mid=Uri.decode(path==null?"":path.replaceFirst("^/+",""));File f=mediaStore.resolveThumbnail(mid);if(f==null)return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));return new WebResourceResponse("image/jpeg",null,new FileInputStream(f));}catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}}).addPathHandler("/media/",path->{try{String mid=Uri.decode(path==null?"":path.replaceFirst("^/+",""));File f=mediaStore.resolveMedia(mid);AppDb.MediaRecord m=db.mediaById(mid);if(f==null||m==null)return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));return new WebResourceResponse(m.mime,null,new FileInputStream(f));}catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}}).build();
        webView=new WebView(this);webView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);webView.setOverScrollMode(View.OVER_SCROLL_NEVER);WebSettings s=webView.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(false);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setBlockNetworkLoads(true);s.setBuiltInZoomControls(false);s.setDisplayZoomControls(false);s.setTextZoom(Math.max(100,Math.min(200,Math.round(getResources().getConfiguration().fontScale*100f))));s.setDefaultTextEncodingName("UTF-8");
        // The direct interface is deliberately limited to the local app asset
        // origin and exposes only the idempotent delete transaction.  It avoids
        // losing an asynchronous WebMessage callback on older System WebViews.
        webView.addJavascriptInterface(new DeleteJavascriptBridge(),"BridgeAndroid");
        // WebView does not display JavaScript confirm() dialogs unless a
        // WebChromeClient handles them.  Every destructive action in the
        // HTML UI deliberately asks for confirmation before it reaches the
        // transactional native delete endpoint, so make that contract
        // explicit instead of allowing the default WebView implementation to
        // silently cancel the dialog.
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){
                final AlertDialog dialog=new AlertDialog.Builder(MainActivity.this)
                        .setTitle("تأیید عملیات")
                        .setMessage(message==null?"آیا ادامه داده شود؟":message)
                        .setNegativeButton("انصراف",(d,which)->result.cancel())
                        .setPositiveButton("تأیید",(d,which)->result.confirm())
                        .create();
                dialog.setOnShowListener(ignored->{
                    if(dialog.getWindow()!=null) dialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                });
                dialog.setOnCancelListener(ignored->result.cancel());
                dialog.show();
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient(){@Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){WebResourceResponse r=assetLoader.shouldInterceptRequest(request.getUrl());return r!=null?r:super.shouldInterceptRequest(view,request);}@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){Uri u=request.getUrl();return !("https".equalsIgnoreCase(u.getScheme())&&"appassets.androidplatform.net".equalsIgnoreCase(u.getHost()));}@Override public void onPageFinished(WebView view,String url){super.onPageFinished(view,url);pageReady=true;maybeBootstrap();}});
        WebViewCompat.addWebMessageListener(webView,"BridgeNative",Collections.singleton(BuildConfig.APP_ORIGIN),this::onBridgeMessage);
        WebViewCompat.addWebMessageListener(webView,"BridgeTransferNative",Collections.singleton(BuildConfig.APP_ORIGIN),this::onBridgeTransferMessage);
        installNativeDrawerSwipeObserver();
        ViewCompat.setOnApplyWindowInsetsListener(webView,(v,insets)->{Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()),cut=insets.getInsets(WindowInsetsCompat.Type.displayCutout()),ime=insets.getInsets(WindowInsetsCompat.Type.ime());int left=Math.max(bars.left,cut.left),top=Math.max(bars.top,cut.top),right=Math.max(bars.right,cut.right),bottom=Math.max(Math.max(bars.bottom,cut.bottom),ime.bottom);v.setPadding(left,top,right,bottom);return insets;});
    }
    private void installNativeDrawerSwipeObserver(){
        final float density=getResources().getDisplayMetrics().density;
        final float horizontalThreshold=Math.max(96f,72f*density),sheetThreshold=Math.max(90f,64f*density),edge=Math.max(20f,16f*density);
        final float[] down={0f,0f,0f,0f};
        final boolean[] sheetCloseAttempted={false};
        webView.setOnTouchListener((v,event)->{
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){down[0]=event.getX();down[1]=event.getY();down[2]=down[0];down[3]=down[1];sheetCloseAttempted[0]=false;return false;}
            if(action==MotionEvent.ACTION_MOVE){down[2]=event.getX();down[3]=event.getY();float mdx=down[2]-down[0],mdy=down[3]-down[1];if(!sheetCloseAttempted[0]&&mdy>sheetThreshold&&Math.abs(mdy)>Math.abs(mdx)*1.18f){sheetCloseAttempted[0]=true;requestBottomSheetClose(down[1]/Math.max(1f,webView.getHeight()));}return false;}
            if(action!=MotionEvent.ACTION_UP&&action!=MotionEvent.ACTION_CANCEL)return false;
            float endX=action==MotionEvent.ACTION_UP?event.getX():down[2],endY=action==MotionEvent.ACTION_UP?event.getY():down[3];
            float dx=endX-down[0],dy=endY-down[1];
            float nx=down[0]/Math.max(1f,webView.getWidth()),ny=down[1]/Math.max(1f,webView.getHeight());

            // Native bottom-sheet fallback: only a deliberate downward drag that started
            // on the sheet handle/header can close a bottom sheet. MOVE handling above
            // keeps this reliable even if WebView converts the final UP into CANCEL.
            if(dy>sheetThreshold&&Math.abs(dy)>Math.abs(dx)*1.18f){
                if(!sheetCloseAttempted[0])requestBottomSheetClose(ny);
                return false;
            }

            if(down[0]<=edge||down[0]>=Math.max(edge,webView.getWidth()-edge)||Math.abs(dx)<horizontalThreshold||Math.abs(dx)<=Math.abs(dy)*1.18f)return false;
            String dir=dx>0?"right":"left";
            String js="(()=>{try{const handled=window.__bridgeLastWebHorizontalSwipe;"+
                    "if(handled&&handled.dir==='"+dir+"'&&Date.now()-handled.at<450)return 'web-handled';"+
                    "const el=document.elementFromPoint(innerWidth*"+nx+",innerHeight*"+ny+");"+
                    "if(el&&el.closest('input,textarea,select,option,canvas,[contenteditable=\"true\"],[data-no-swipe],.map-viewport,.network-map,.photo img,.image-viewer,.carousel,input[type=\"range\"],audio,video'))return 'blocked';"+
                    "const l=document.getElementById('leftDrawer'),r=document.getElementById('rightDrawer');"+
                    "if(r?.classList.contains('open')){if('"+dir+"'==='right')closeDrawers();else openDrawer('right');return 'right';}"+
                    "if(l?.classList.contains('open')){if('"+dir+"'==='left')closeDrawers();else openDrawer('left');return 'left';}"+
                    "openDrawer('"+(dx>0?"left":"right")+"');return 'opened';}catch(e){return 'error'}})()";
            webView.postDelayed(()->webView.evaluateJavascript(js,null),120L);
            return false;
        });
    }
    private void requestBottomSheetClose(float normalizedStartY){
        String sheetJs="(()=>{try{const y=innerHeight*"+normalizedStartY+",d=window.__bridgeNativeSheetDebug={startY:y},sheet=document.querySelector('.modal.open [data-swipe-sheet=\"bottom\"]');"+
                "if(!sheet){d.outcome='no-sheet';return d.outcome}const r=sheet.getBoundingClientRect();d.top=r.top;d.bottom=r.bottom;"+
                "if(y<r.top-24||y>r.top+200){d.outcome='outside-handle';return d.outcome}"+
                "sheet.style.transition='';sheet.style.transform='';sheet.classList.remove('open');"+
                "const modal=sheet.closest('.modal');if(modal)modal.classList.remove('open');d.outcome='closed';return d.outcome;"+
                "}catch(e){return 'error'}})()";
        webView.post(()->webView.evaluateJavascript(sheetJs,null));
    }
    private static boolean trusted(Uri o){return o!=null&&"https".equalsIgnoreCase(o.getScheme())&&"appassets.androidplatform.net".equalsIgnoreCase(o.getHost())&&o.getPort()==-1;}
    private void onBridgeTransferMessage(@NonNull WebView view,@NonNull WebMessageCompat message,@NonNull Uri sourceOrigin,boolean isMainFrame,@NonNull JavaScriptReplyProxy reply){
        if(!isMainFrame||!trusted(sourceOrigin))return;
        try{
            JSONObject request=new JSONObject(message.getData());String type=request.optString("type","");
            if("deleteBatch".equals(type)){handleDeleteBatch(request.optJSONObject("payload"));reply.postMessage("{\"ok\":true,\"accepted\":true}");return;}
            if("pickBridge".equals(type)){runOnUiThread(()->bridgePicker.launch(new String[]{"application/json","application/octet-stream"}));reply.postMessage("{\"ok\":true}");return;}
            if("exportBridge".equals(type)){JSONObject payload=request.optJSONObject("payload"),bridge=payload==null?null:payload.optJSONObject("bridge");if(bridge==null)throw new IllegalArgumentException("missing bridge");io.execute(()->shareBridgeFile(bridge));reply.postMessage("{\"ok\":true}");return;}
            reply.postMessage("{\"ok\":false,\"error\":\"unknown-type\"}");
        }catch(Exception e){reply.postMessage("{\"ok\":false,\"error\":\"invalid-payload\"}");}
    }
    private void shareBridgeFile(JSONObject bridge){try{JSONObject envelope=new JSONObject().put("format","bridge-maintenance-bridge").put("version",1).put("appVersion",BuildConfig.VERSION_NAME).put("bridge",bridge);File dir=new File(getCacheDir(),"exports");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("export directory");String name="bridge-"+bounded(bridge.optString("code",bridge.optString("name","record")),60).replaceAll("[^\\p{L}\\p{N}._-]+","_")+".bridge.json";File file=new File(dir,name);try(FileOutputStream out=new FileOutputStream(file)){out.write(envelope.toString(2).getBytes(StandardCharsets.UTF_8));out.getFD().sync();}Uri uri=FileProvider.getUriForFile(this,getString(R.string.provider_authority),file);Intent send=new Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_SUBJECT,"مشخصات پل").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);runOnUiThread(()->startActivity(Intent.createChooser(send,"اشتراک‌گذاری پل")));}catch(Exception e){runOnUiThread(()->toast("ساخت فایل پل ناموفق بود."));}}
    private void importBridgeFile(Uri uri){try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IllegalArgumentException("file");byte[] bytes=readLimited(in,2_000_000);JSONObject envelope=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(!"bridge-maintenance-bridge".equals(envelope.optString("format"))||envelope.optInt("version")!=1)throw new IllegalArgumentException("format");JSONObject bridge=envelope.optJSONObject("bridge");if(bridge==null)throw new IllegalArgumentException("bridge");String id="bridge-import-"+System.currentTimeMillis();bridge.put("id",id).put("updatedAt",System.currentTimeMillis());if(bridge.optString("name").trim().isEmpty()&&bridge.optString("code").trim().isEmpty())throw new IllegalArgumentException("identity");if(!db.save("bridges",id,bridge.toString()))throw new IllegalStateException("save");runOnUiThread(()->{maybeBootstrap();toast("فایل پل با موفقیت افزوده شد.");});}catch(Exception e){runOnUiThread(()->toast("فایل پل معتبر نیست یا قابل خواندن نیست."));}}
    private static byte[] readLimited(InputStream in,int limit)throws Exception{java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];int total=0,n;while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IllegalArgumentException("too large");out.write(b,0,n);}return out.toByteArray();}
    private void onBridgeMessage(@NonNull WebView view,@NonNull WebMessageCompat message,@NonNull Uri sourceOrigin,boolean isMainFrame,@NonNull JavaScriptReplyProxy reply){if(!isMainFrame||!trusted(sourceOrigin))return;String raw=message.getData();if(raw==null||raw.length()>MAX_BRIDGE_MESSAGE){reply.postMessage("{\"ok\":false,\"error\":\"invalid-message\"}");return;}try{JSONObject req=new JSONObject(raw),p=req.optJSONObject("payload");if(p==null)p=new JSONObject();String type=req.optString("type","");switch(type){case"dbSave":{String kind=p.optString("kind"),id=p.optString("id"),json=p.optString("json");boolean ok=safeKind(kind)&&safeId(id)&&json.length()<=5_000_000&&validJson(json)&&db.save(kind,id,json);reply.postMessage(ok?"{\"ok\":true}":"{\"ok\":false,\"error\":\"db-save\"}");break;}case"dbDelete":{String kind=p.optString("kind"),id=p.optString("id");boolean ok=safeKind(kind)&&safeId(id)&&db.delete(kind,id);reply.postMessage(ok?"{\"ok\":true}":"{\"ok\":false,\"error\":\"db-delete\"}");break;}case"deleteBatch":{handleDeleteBatch(p);reply.postMessage("{\"ok\":true,\"accepted\":true}");break;}case"finalizeInspection":{handleFinalizeInspection(p);reply.postMessage("{\"ok\":true,\"accepted\":true}");break;}case"pickImage":pendingPhoto=PhotoContext.from(p);runOnUiThread(()->photoPicker.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));reply.postMessage("{\"ok\":true}");break;case"takePhoto":PhotoContext cameraContext=PhotoContext.from(p);runOnUiThread(()->startCamera(cameraContext));reply.postMessage("{\"ok\":true}");break;case"requestLocation":{requestOccurrenceLocation(p);reply.postMessage("{\"ok\":true,\"accepted\":true}");break;}case"openLocationSettings":{runOnUiThread(()->{try{startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));}catch(Exception e){toast("تنظیمات موقعیت مکانی در دسترس نیست.");}});reply.postMessage("{\"ok\":true}");break;}case"saveSignature":{String dataUri=p.optString("dataUri"),signer=bounded(p.optString("signer","امضا"),80),visitDate=bounded(p.optString("visitDate",""),20),ownerId=bounded(p.optString("ownerId",""),180);if(ownerId.isEmpty()||dataUri.length()>5_800_000){reply.postMessage("{\"ok\":false,\"error\":\"invalid-signature\"}");break;}io.execute(()->{try{JSONObject m=mediaStore.saveSignatureDataUri(dataUri,signer,visitDate,ownerId);m.put("signer",signer);m.put("visitDate",visitDate);runOnUiThread(()->postJsString("window.receiveSignatureSaved",m.toString()));}catch(Exception e){runOnUiThread(()->postJsString("window.receiveSignatureSaved","{\"ok\":false}"));}});reply.postMessage("{\"ok\":true,\"accepted\":true}");break;}case"pickBackup":runOnUiThread(()->backupPicker.launch(new String[]{"application/json","application/zip","application/octet-stream"}));reply.postMessage("{\"ok\":true}");break;case"exportReport":{String format=p.optString("format"),name=p.optString("name");JSONObject report=p.optJSONObject("report");if(report==null)throw new IllegalArgumentException("missing report");io.execute(()->{try{File f=reportExporter.create(format,name,report);runOnUiThread(()->beginExport(f,name,mimeFor(format)));}catch(Exception e){runOnUiThread(()->toast("ساخت فایل خروجی ناموفق بود."));}});reply.postMessage("{\"ok\":true}");break;}case"exportBackup":io.execute(()->{try{File f=backupManager.createPackage();runOnUiThread(()->beginExport(f,"bridge-maintenance-backup.zip","application/zip"));}catch(Exception e){runOnUiThread(()->toast("ساخت فایل پشتیبان ناموفق بود."));}});reply.postMessage("{\"ok\":true}");break;case"restorePendingBackup":{Uri uri=pendingBackupUri;if(uri==null){reply.postMessage("{\"ok\":false,\"error\":\"no-pending-backup\"}");break;}pendingBackupUri=null;io.execute(()->performBackupRestore(uri));reply.postMessage("{\"ok\":true}");break;}case"scheduleReminder":scheduleReminder(p);reply.postMessage("{\"ok\":true}");break;default:reply.postMessage("{\"ok\":false,\"error\":\"unknown-type\"}");}}catch(Exception e){reply.postMessage("{\"ok\":false,\"error\":\"invalid-payload\"}");}}
    /** Execute a delete batch off the WebView thread and always resolve the
     * JavaScript waiter, even when the requested ids are already absent. */
    private void handleDeleteBatch(JSONObject payload){
        final String requestId=bounded(payload==null?"":payload.optString("requestId",""),120);
        final JSONArray entries=payload==null?null:payload.optJSONArray("entries");
        if(requestId.isEmpty()||entries==null||entries.length()>5000){
            JSONObject result=new JSONObject();try{result.put("ok",false).put("requestId",requestId).put("error","invalid-delete-batch");}catch(Exception ignored){}
            runOnUiThread(()->postJsString("window.receiveDbDeleteResult",result.toString()));
            return;
        }
        io.execute(()->{
            JSONObject result=new JSONObject();
            try{
                AppDb.DeleteResult deleted=db.deleteMany(entries);
                int mediaDeleted=mediaStore.deleteFiles(deleted.media);
                result.put("ok",true).put("requestId",requestId).put("deleted",deleted.entitiesDeleted)
                        .put("mediaDeleted",mediaDeleted).put("mediaExpected",deleted.media.size());
            }catch(Exception error){
                try{result.put("ok",false).put("requestId",requestId).put("error","delete-failed");}catch(Exception ignored){}
            }
            runOnUiThread(()->postJsString("window.receiveDbDeleteResult",result.toString()));
        });
    }

    /** Synchronous, idempotent delete endpoint used by the page for reliable
     * persistence. JavaScript-interface calls run off the UI thread; AppDb and
     * AppMediaStore are synchronized and safe for this small bounded batch. */
    private final class DeleteJavascriptBridge {
        @JavascriptInterface public String deleteBatch(String raw) {
            JSONObject result = new JSONObject();
            try {
                JSONObject payload = new JSONObject(raw == null ? "{}" : raw);
                String requestId = bounded(payload.optString("requestId", ""), 120);
                JSONArray entries = payload.optJSONArray("entries");
                if (requestId.isEmpty() || entries == null || entries.length() > 5000) {
                    return result.put("ok", false).put("requestId", requestId).put("error", "invalid-delete-batch").toString();
                }
                AppDb.DeleteResult deleted = db.deleteMany(entries);
                int mediaDeleted = mediaStore.deleteFiles(deleted.media);
                return result.put("ok", true).put("requestId", requestId)
                        .put("deleted", deleted.entitiesDeleted)
                        .put("mediaDeleted", mediaDeleted)
                        .put("mediaExpected", deleted.media.size()).toString();
            } catch (Exception error) {
                try { return result.put("ok", false).put("error", "delete-failed").toString(); }
                catch (Exception ignored) { return "{\"ok\":false,\"error\":\"delete-failed\"}"; }
            }
        }
    }

    private void handleFinalizeInspection(JSONObject payload){
        JSONObject inspection=payload.optJSONObject("inspection"),audit=payload.optJSONObject("audit");
        JSONArray defects=payload.optJSONArray("defects"),reminders=payload.optJSONArray("reminders");
        if(inspection==null||audit==null||defects==null||reminders==null||defects.length()>2000||reminders.length()>10){
            postJsString("window.receiveFinalizeResult","{\"ok\":false}");return;
        }
        io.execute(()->{try{
            TransactionalFinalizer.commit(db,inspection,defects,reminders,audit);
            runOnUiThread(()->postJsString("window.receiveFinalizeResult","{\"ok\":true}"));
        }catch(Exception e){
            runOnUiThread(()->postJsString("window.receiveFinalizeResult","{\"ok\":false}"));
        }});
    }
    private void maybeBootstrap(){if(!pageReady||!migrationDone||webView==null)return;String snapshot=db.snapshotJson(),js="window.BridgeNativeClient&&window.BridgeNativeClient.bootstrap("+JSONObject.quote(snapshot)+","+JSONObject.quote(BuildConfig.VERSION_NAME)+","+BuildConfig.VERSION_CODE+");";webView.evaluateJavascript(js,null);}
    private void registerActivityResults(){photoPicker=registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),uri->{if(uri!=null&&pendingPhoto!=null){PhotoContext pc=pendingPhoto;io.execute(()->{try{JSONObject m=mediaStore.importUri(uri,pc.subsection,pc.visitDate,pc.ownerKind,pc.ownerId);m.put("target",pc.target);m.put("itemKey",pc.itemKey);m.put("requestId",pc.requestId);runOnUiThread(()->postJsString("window.receivePickedImage",m.toString()));}catch(Exception e){runOnUiThread(()->toast("ذخیره تصویر ناموفق بود."));}});}});cameraLauncher=registerForActivityResult(new ActivityResultContracts.TakePicture(),ok->{if(Boolean.TRUE.equals(ok)&&pendingCameraFile!=null&&pendingPhoto!=null){PhotoContext pc=pendingPhoto;File f=pendingCameraFile;io.execute(()->{try{JSONObject m=mediaStore.registerCameraFile(f,pc.subsection,pc.visitDate,pc.ownerKind,pc.ownerId);m.put("target",pc.target);m.put("itemKey",pc.itemKey);m.put("requestId",pc.requestId);runOnUiThread(()->postJsString("window.receivePickedImage",m.toString()));}catch(Exception e){runOnUiThread(()->toast("ثبت تصویر دوربین ناموفق بود."));}});}else if(pendingCameraFile!=null&&!Boolean.TRUE.equals(ok))pendingCameraFile.delete();});backupPicker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{if(uri!=null){pendingBackupUri=uri;io.execute(()->inspectBackup(uri));}});bridgePicker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{if(uri!=null)io.execute(()->importBridgeFile(uri));});pdfDocumentLauncher=registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"),this::onExportDestination);csvDocumentLauncher=registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"),this::onExportDestination);xlsxDocumentLauncher=registerForActivityResult(new ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),this::onExportDestination);backupDocumentLauncher=registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"),this::onExportDestination);notificationPermission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{if(!Boolean.TRUE.equals(granted))toast("مجوز اعلان داده نشد؛ یادآورها ممکن است نمایش داده نشوند.");});locationPermission=registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),result->{boolean granted=Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))||Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));if(granted)captureCurrentLocation(pendingLocationTarget);else postLocationError(pendingLocationTarget,"permission-denied");});}
    private void requestOccurrenceLocation(JSONObject p){
        String target=bounded(p.optString("target",""),160);if(target.isEmpty()){postLocationError(target,"invalid-target");return;}pendingLocationTarget=target;
        boolean fine=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        boolean coarse=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        if(!fine&&!coarse){runOnUiThread(()->locationPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION}));return;}
        captureCurrentLocation(target);
    }
    private void captureCurrentLocation(String target){
        LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);if(lm==null){postLocationError(target,"location-unavailable");return;}
        boolean fine=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        boolean coarse=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        if(!fine&&!coarse){postLocationError(target,"permission-denied");return;}
        boolean gps=false,network=false;try{gps=fine&&lm.isProviderEnabled(LocationManager.GPS_PROVIDER);}catch(Exception ignored){}try{network=(fine||coarse)&&lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);}catch(Exception ignored){}
        if(!gps&&!network){postLocationError(target,"location-disabled");return;}
        captureWithProvider(lm,target,gps?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER,gps&&network);
    }
    private void captureWithProvider(LocationManager lm,String target,String provider,boolean allowNetworkFallback){
        final long token=++locationRequestToken;final CancellationSignal signal=new CancellationSignal();final Runnable timeout=()->{if(token!=locationRequestToken)return;signal.cancel();if(allowNetworkFallback){captureWithProvider(lm,target,LocationManager.NETWORK_PROVIDER,false);return;}postLocationError(target,"location-timeout");};
        runOnUiThread(()->webView.postDelayed(timeout,15_000));
        try{
            LocationManagerCompat.getCurrentLocation(lm,provider,signal,ContextCompat.getMainExecutor(this),location->{if(token!=locationRequestToken)return;webView.removeCallbacks(timeout);if(location==null){if(allowNetworkFallback){captureWithProvider(lm,target,LocationManager.NETWORK_PROVIDER,false);}else postLocationError(target,"location-unavailable");return;}locationRequestToken++;postLocationSuccess(target,location);});
        }catch(SecurityException e){webView.removeCallbacks(timeout);postLocationError(target,"permission-denied");}catch(Exception e){webView.removeCallbacks(timeout);if(allowNetworkFallback)captureWithProvider(lm,target,LocationManager.NETWORK_PROVIDER,false);else postLocationError(target,"location-unavailable");}
    }
    private void postLocationSuccess(String target,Location location){
        try{JSONObject o=new JSONObject();o.put("ok",true);o.put("target",target);o.put("lat",location.getLatitude());o.put("lon",location.getLongitude());o.put("accuracyM",location.hasAccuracy()?location.getAccuracy():JSONObject.NULL);o.put("altitudeM",location.hasAltitude()?location.getAltitude():JSONObject.NULL);o.put("provider",location.getProvider()==null?"":location.getProvider());long captured=location.getTime()>0?location.getTime():System.currentTimeMillis();o.put("capturedAt",captured);o.put("mock",LocationCompat.isMock(location));postJsString("window.receiveLocationResult",o.toString());}catch(Exception e){postLocationError(target,"location-unavailable");}
    }
    private void postLocationError(String target,String error){
        if(webView==null)return;try{JSONObject o=new JSONObject();o.put("ok",false);o.put("target",target==null?"":target);o.put("error",error);runOnUiThread(()->postJsString("window.receiveLocationResult",o.toString()));}catch(Exception ignored){}
    }
    private void startCamera(PhotoContext pc){try{pendingPhoto=pc;pendingCameraFile=mediaStore.createCameraFile(pc.subsection,pc.visitDate);Uri uri=FileProvider.getUriForFile(this,getString(R.string.provider_authority),pendingCameraFile);cameraLauncher.launch(uri);}catch(Exception e){toast("دوربین در دسترس نیست.");}}
    private void beginExport(File file,String requestedName,String mime){pendingExportFile=file;pendingExportName=safeFileName(requestedName);if("application/pdf".equals(mime))pdfDocumentLauncher.launch(pendingExportName);else if("text/csv".equals(mime))csvDocumentLauncher.launch(pendingExportName);else if("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(mime))xlsxDocumentLauncher.launch(pendingExportName);else if("application/zip".equals(mime))backupDocumentLauncher.launch(pendingExportName);else{file.delete();pendingExportFile=null;toast("نوع فایل خروجی پشتیبانی نمی‌شود.");}}
    private void onExportDestination(Uri uri){if(uri==null||pendingExportFile==null)return;File f=pendingExportFile;pendingExportFile=null;io.execute(()->{try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IllegalStateException("Output stream unavailable");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.flush();runOnUiThread(()->toast("فایل با موفقیت ذخیره شد."));}catch(Exception e){runOnUiThread(()->toast("ذخیره فایل ناموفق بود."));}finally{f.delete();}});}
    private void inspectBackup(Uri uri){try{BackupManager.BackupSummary s=backupManager.inspect(uri);runOnUiThread(()->{try{postJsString("window.receiveBackupSummary",s.json().toString());}catch(Exception e){toast("نمایش خلاصه پشتیبان ناموفق بود.");}});}catch(Exception e){pendingBackupUri=null;runOnUiThread(()->toast("فایل پشتیبان معتبر یا متعلق به این برنامه نیست."));}}
    private void performBackupRestore(Uri uri){try{BackupManager.RestoreResult r=backupManager.restore(uri);if(r.needsLegacyMediaMigration)new InlinePhotoMigrator(db,mediaStore).run();runOnUiThread(()->{maybeBootstrap();try{JSONObject o=r.summary.json();o.put("restoredMedia",r.restoredMedia);o.put("safetyBackup",r.safetyBackup.getName());o.put("integrityVerified",r.integrityVerified);postJsString("window.receiveBackupRestoreResult",o.toString());}catch(Exception e){toast("بازیابی انجام شد؛ نمایش خلاصه ناموفق بود.");}});}catch(Exception e){runOnUiThread(()->toast("بازیابی پشتیبان انجام نشد و داده فعلی حفظ شد."));}}
    private void scheduleReminder(JSONObject p){long when=p.optLong("timestamp",0);if(when<=System.currentTimeMillis())return;if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);Intent i=new Intent(this,ReminderReceiver.class);int nid=Math.abs(p.optString("id","1").hashCode());i.putExtra("nid",nid);i.putExtra("title",bounded(p.optString("title"),140));i.putExtra("body",bounded(p.optString("body"),500));PendingIntent pi=PendingIntent.getBroadcast(this,nid,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);if(am!=null){try{if(Build.VERSION.SDK_INT>=31&&am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);}catch(SecurityException e){am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);}}}
    private void configureBack(){getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){if(webView==null||!pageReady){finish();return;}webView.evaluateJavascript("window.handleSystemBack?window.handleSystemBack():'exit'",value->{if("\"exit\"".equals(value)||"exit".equals(value))finish();});}});}
    private void applyImmersiveMode(){WindowInsetsControllerCompat c=WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(WindowInsetsCompat.Type.systemBars());}
    @Override protected void onResume(){super.onResume();applyImmersiveMode();DateStatusNotifier.show(this);if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);}@Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus)applyImmersiveMode();}@Override protected void onDestroy(){io.shutdown();if(webView!=null){webView.stopLoading();webView.destroy();}if(db!=null)db.close();super.onDestroy();}
    private void createNotificationChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.createNotificationChannel(new NotificationChannel(ReminderReceiver.CHANNEL_ID,getString(R.string.notification_channel),NotificationManager.IMPORTANCE_DEFAULT));}}
    private void postJsString(String fn,String arg){if(webView!=null)webView.evaluateJavascript(fn+"("+JSONObject.quote(arg)+")",null);}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}private static boolean safeKind(String s){return s!=null&&s.matches("[A-Za-z0-9_\\-]{1,80}");}private static boolean safeId(String s){return s!=null&&s.length()>0&&s.length()<=180&&s.indexOf('\0')<0;}private static boolean validJson(String s){try{new JSONObject(s);return true;}catch(Exception e){return false;}}private static String mimeFor(String format){return"pdf".equals(format)?"application/pdf":"xlsx".equals(format)?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"zip".equals(format)?"application/zip":"text/csv";}private static String safeFileName(String name){String n=(name==null||name.trim().isEmpty())?"bridge-inspection-report.bin":name.trim();n=n.replaceAll("[\\\\/:*?\"<>|\\r\\n]","_");return n.length()>120?n.substring(0,120):n;}private static String bounded(String s,int n){if(s==null)return"";return s.length()>n?s.substring(0,n):s;}
    private static final class PhotoContext{final String target,subsection,visitDate,ownerKind,ownerId,itemKey,requestId;PhotoContext(String target,String subsection,String visitDate,String ownerKind,String ownerId,String itemKey,String requestId){this.target=target;this.subsection=subsection;this.visitDate=visitDate;this.ownerKind=ownerKind;this.ownerId=ownerId;this.itemKey=itemKey;this.requestId=requestId;}static PhotoContext from(JSONObject p){return new PhotoContext(bounded(p.optString("target","inspection"),30),bounded(p.optString("subsection","تصویر"),80),bounded(p.optString("visitDate",""),20),bounded(p.optString("ownerKind","inspection"),40),bounded(p.optString("ownerId","pending"),180),bounded(p.optString("itemKey",""),180),bounded(p.optString("requestId",""),80));}}
}
