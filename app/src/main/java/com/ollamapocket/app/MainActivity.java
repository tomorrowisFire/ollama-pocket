package com.ollamapocket.app;

import android.Manifest;
import android.app.Activity;
import android.widget.ProgressBar;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.app.AlertDialog;
import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.animation.ValueAnimator;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.util.TypedValue;
import android.graphics.drawable.ColorDrawable;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.Toast;
import android.text.TextWatcher;
import android.text.Editable;
import java.util.HashMap;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Android UI. Local inference only; optional Google demo banners use the network. */
public final class MainActivity extends Activity {
    private static final int PICK_GGUF = 701;
    private static final int VOICE_PERMISSION = 702;
    private static final int EXPORT_CHAT = 703;
    private static final int PICK_ATTACHMENT = 704;
    private String attachmentScope="";
    private long attachmentSerial;
    private boolean attachmentReading;
    private Button attachFileButton;
    private boolean guestOnly, foreground, speakNextAnswer;
    private OnHandsVoice voice;
    private Button voiceButton, cloudShortcut, exportConversation;
    private final ExecutorService chatSearchIo=Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> chatSearchTask;
    private long chatSearchSerial;
    private String exportScope="",exportDigest="",exportModel="";
    private boolean exportJson;
    private LinearLayout localChatContent;
    private CloudChatPanel cloudPanel;
    private boolean cloudMode;
    private long lastStreamUi;
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(OnHandsLocale.wrap(base));
    }
    // Palette values are mutable only during Activity creation/theme recreation.
    private static int BG = Color.rgb(14, 16, 19);
    private static int CARD = Color.rgb(21, 23, 27);
    private static int FIELD = Color.rgb(38, 40, 45);
    private static int ACCENT = Color.rgb(242, 243, 245);
    private static int WHITE = Color.rgb(242, 244, 247);
    private static int MUTED = Color.rgb(158, 163, 171);
    private static int DANGER = Color.rgb(238, 119, 130);
    private static int BUBBLE = Color.rgb(44,46,51);
    private String uiTheme = "mono_light";
    private String themeMode = "light";
    private boolean activeThemeDark = false;
    private float uiTextScale = 1.0f;
    private int activeTab = 0;
    private boolean showHud = true;
    private boolean smoothMotion = true;
    private String workspaceMode=WorkspaceLayoutPolicy.AUTO;
    private LinearLayout workspace,tabletChats;
    private ScrollView tabletRail;
    private View tabletGap;
    private Button menuButton,workspaceModeButton;
    private EditText tabletSearch;
    private final TextView[] tabletLinks=new TextView[5];
    private int currentRailDp;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private FrameLayout pages;
    private LinearLayout chatPage, modelPage, serverPage, diagnosticPage, nodeRows;
    private TextView localConnectionStatus, connectionSessionStatus, modelListHeader;
    private EditText modelFilter;
    private TextView modelSearchStatus;
    private Spinner familyFilter, quantizationFilter;
    private final List<ModelVariantCatalog.Variant> onlineVariants=new ArrayList<>();
    private final List<ModelVariantCatalog.Variant> recommendedVariants=new ArrayList<>();
    private final Map<String,ModelVariantCatalog.Variant> visibleVariants=new HashMap<>();
    private Spinner sourceFilter,sortFilter;
    private boolean modelListView=true;
    private Button modelListViewButton,modelGridViewButton;
    private String selectedSource="전체에서 찾기", selectedSort="추천 순";
    private java.util.concurrent.Future<?> onlineSearchTask;
    private java.util.concurrent.Future<?> onlineHfTask;
    private final ModelSearchCache searchCache=new ModelSearchCache(20,600_000L);
    private final Map<String,List<ModelVariantCatalog.Variant>> searchParts=new java.util.LinkedHashMap<>();
    private final java.util.Set<String> expandedGroups=new java.util.LinkedHashSet<>();
    private final List<String> searchFailures=new ArrayList<>();
    private int searchRequestsRemaining=0;
    private boolean recommendationLoading=false, recommendationLoaded=false;
    private String recommendationError="";
    private int lastModelGridWidth=0;
    private long onlineSearchSerial = 0;
    private boolean onlineSearchPending = false;
    private String onlineSearchError = "";
    private Runnable onlineSearchRunnable;
    private String selectedModelFamily="전체 모델";
    private String selectedQuantization="전체 양자화";
    private final Map<String,String> modelMetadata = new HashMap<>();
    private final Map<String,String> modelQuantization = new HashMap<>();
    private final Map<String,Long> modelBytes = new HashMap<>();
    private final Map<String,TextView> tileStatusLabels = new HashMap<>();
    private final Map<String,ProgressBar> tileDownloadBars = new HashMap<>();
    private boolean animateNextTileUpdate = false;
    private volatile String downloadingModel = "";
    private volatile String downloadingCardId = "";
    private volatile int downloadingPercent = -1;
    private TextView suitabilitySummary;
    private Button installedPickerButton;
    private DeviceProfiler.Profile deviceProfile;
    private ComputeProfiler.Report computeReport;
    private TextView computeSummary;
    private TextView deviceSummary;

    private ProgressBar downloadProgress;
    private FrameLayout downloadAdSlot;
    private AdSupport ads;
    private long lastPauseElapsed;
    private TextView topPageTitle, topPageSubtitle;
    private Dialog navigationDrawer;
    private LinearLayout messages, modelRows;
    private ScrollView messageScroll;
    private Spinner modelChooser;
    private TextView serverStatus, miniStatus, progressStatus, logText, activeEngineLabel;
    private EditText chatInput, modelInput;
    private Button sendButton, stopChatButton, pullButton, cancelPullButton, downloadRetryButton;
    private String lastFailedPullModel="";
    private boolean diagnosticBusy;
    private volatile boolean benchmarkBusy;
    private TextView diagnosticStatus, performanceHudView;
    private volatile PerformanceHud.Sample lastHud;
    private final Runnable hudPoll=new Runnable() {
        @Override public void run() {
            io.execute(()->{
                try{
                    PerformanceHud.Sample reading=PerformanceHud.sample(getApplicationContext());
                    ui.post(()->{
                        if(isFinishing()||isDestroyed())return;
                        lastHud=reading;
                        if(performanceHudView!=null){
                            String identity=deviceProfile==null?getString(R.string.hud_check):getString(R.string.hud_identity,deviceProfile.device,deviceProfile.cores);
                            performanceHudView.setText(identity+"\n"+reading.compact(MainActivity.this));
                            performanceHudView.setContentDescription(getString(R.string.hud_accessibility,reading.detail(MainActivity.this)));
                        }
                        if(activeTab==3)refreshDiagnosticViews();
                    });
                }catch(Exception e){
                    ui.post(()->{
                        if(performanceHudView!=null&&!isFinishing())
                            performanceHudView.setText(getString(R.string.hud_retry));
                    });
                }
            });
            ui.postDelayed(this,2500);
        }
    };
    private volatile HttpURLConnection chatConnection, pullConnection;
    private volatile boolean chatBusy, pullBusy, chatCancelled, pullCancelled;
    private volatile boolean serverPollBusy;
    private JSONArray history = new JSONArray();
    private boolean historyReadable=true;
    private String loadedHistoryScope="",pendingLocalPrompt="";
    private String currentModel = "";
    private final List<String> knownModels = new ArrayList<>();
    private boolean ignoreSelection;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            serverStatus.setText(OllamaForegroundService.isRunning() ?
                getString(R.string.server_running) : getString(R.string.server_wait));
            logText.setText(OllamaForegroundService.logs() + "\n--- GPU Vulkan ---\n" + GpuRunner.logs());
            if (!serverPollBusy) {
                serverPollBusy = true;
                io.execute(() -> {
                    String label;
                    try {
                        JSONObject version = OllamaHttp.get("/api/version");
                        label = "● "+getString(R.string.engine_connected)+" "+version.optString("version", "");
                    } catch (Exception ex) { label = "○ "+getString(R.string.engine_waiting); }
                    final String value = label;
                    ui.post(() -> { miniStatus.setText(value); serverPollBusy = false; });
                });
            }
            ui.postDelayed(this, 3500);
        }
    };

    /** Existing explicitly chosen mono_* and dark modes retain their meaning. */
    private boolean resolvedDarkTheme(String choice){
        return "auto".equals(choice)?
            SolarTheme.isNight(java.time.Instant.now(),java.time.ZoneId.systemDefault(),
                getPreferences(MODE_PRIVATE).getString("sunset_city","device")):
            "dark".equals(choice)||"mono_dark".equals(choice);
    }
    private boolean lightTheme(){return !activeThemeDark;}
    private void applyUiPreferences() {
        themeMode=getPreferences(MODE_PRIVATE).getString("ui_theme","light");
        activeThemeDark=resolvedDarkTheme(themeMode);
        uiTheme=activeThemeDark?"mono_dark":"mono_light";
        String font=getPreferences(MODE_PRIVATE).getString("font_size","normal");
        uiTextScale="large".equals(font)?1.13f:"extra".equals(font)?1.26f:1.0f;
        showHud=getPreferences(MODE_PRIVATE).getBoolean("show_hud",getPreferences(MODE_PRIVATE).getBoolean("welcome_v2_seen",false));
        smoothMotion=getPreferences(MODE_PRIVATE).getBoolean("smooth_motion",true);
        modelListView=getPreferences(MODE_PRIVATE).getBoolean("model_list_view",true);
        workspaceMode=WorkspaceLayoutPolicy.normalize(getPreferences(MODE_PRIVATE).getString("workspace_mode",WorkspaceLayoutPolicy.AUTO));
        // Neutral and flatter system palette. Avoid the blue-tinted boxes
        // and outlines previously repeated around every setting and button.
        if(lightTheme()){
            BG=Color.rgb(250,250,251); CARD=Color.WHITE; FIELD=Color.rgb(243,244,246);
            ACCENT=Color.rgb(34,36,41); WHITE=Color.rgb(37,39,44);
            MUTED=Color.rgb(111,114,121); DANGER=Color.rgb(174,53,68);
            BUBBLE=Color.rgb(243,244,246);
        } else {
            BG=Color.rgb(18,19,22); CARD=Color.rgb(27,28,32); FIELD=Color.rgb(40,42,47);
            ACCENT=Color.rgb(237,238,240); WHITE=Color.rgb(238,239,242);
            MUTED=Color.rgb(157,160,168); DANGER=Color.rgb(239,143,153);
            BUBBLE=Color.rgb(40,42,47);
        }
    }
    private int[] palette(){
        return new int[]{BG,CARD,FIELD,ACCENT,WHITE,MUTED,DANGER,BUBBLE};
    }
    private int mappedColor(int current,int[] from,int[] to){
        for(int i=0;i<from.length;i++)if(current==from[i])return to[i];
        return current;
    }
    /** Update the existing view tree in place: no Activity restart, lost scroll or dark flash. */
    private void recolorViewTree(View v,int[] before,int[] after){
        if(v instanceof TextView){
            TextView text=(TextView)v;
            int old=text.getCurrentTextColor();
            if(mappedColor(old,before,after)!=old)
                text.setTextColor(mappedColor(old,before,after));
            if(v instanceof EditText){
                EditText input=(EditText)v;
                int hint=input.getCurrentHintTextColor();
                if(mappedColor(hint,before,after)!=hint)
                    input.setHintTextColor(mappedColor(hint,before,after));
            }
        }
        android.graphics.drawable.Drawable drawable=v.getBackground();
        if(drawable instanceof GradientDrawable){
            GradientDrawable shape=(GradientDrawable)drawable.mutate();
            android.content.res.ColorStateList c=shape.getColor();
            if(c!=null){
                int old=c.getDefaultColor(),next=mappedColor(old,before,after);
                if(next!=old){
                    shape.setColor(next);
                    // Do not bring back universal borders during auto theme changes.
                    if(old==before[1]||old==before[2])
                        shape.setStroke(0,Color.TRANSPARENT);
                }
            }
        }else if(drawable instanceof ColorDrawable){
            ColorDrawable solid=(ColorDrawable)drawable.mutate();
            int old=solid.getColor(),next=mappedColor(old,before,after);
            if(next!=old)solid.setColor(next);
        }
        if(v instanceof ViewGroup){
            ViewGroup group=(ViewGroup)v;
            for(int i=0;i<group.getChildCount();i++)
                recolorViewTree(group.getChildAt(i),before,after);
        }
    }
    private void switchAutoThemeWithoutRestart(){
        int[] before=palette();
        applyUiPreferences();
        int[] after=palette();
        Window window=getWindow();
        if(window!=null){
            recolorViewTree(window.getDecorView(),before,after);
            window.setStatusBarColor(BG);window.setNavigationBarColor(BG);
            window.getDecorView().setSystemUiVisibility(lightTheme()?
                (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR):0);
        }
        updateModelViewButtons();
        renderInstalledModels();
        if(activeTab==3)refreshDiagnosticViews();
    }
    /** Check at most once per minute; defer transition while foreground work or drawers run. */
    private final Runnable sunsetThemePoll=new Runnable(){
        @Override public void run(){
            if(isFinishing()||isDestroyed())return;
            if("auto".equals(themeMode)&&resolvedDarkTheme("auto")!=activeThemeDark
                &&!isUiBusy()&&(navigationDrawer==null||!navigationDrawer.isShowing())){
                switchAutoThemeWithoutRestart();
            }
            ui.postDelayed(this,60_000);
        }
    };
    @Override public void onCreate(Bundle state) {
        String wanted=getPreferences(MODE_PRIVATE).getString("ui_theme","light");
        setTheme(resolvedDarkTheme(wanted)?
            android.R.style.Theme_Material_NoActionBar:
            android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        applyUiPreferences();
        guestOnly=getPreferences(MODE_PRIVATE).getBoolean("guest_compute_only",false);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
            "mono_light".equals(uiTheme)?
                (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR):0);
        if(guestOnly){createGuestPage();return;}
        deviceProfile = DeviceProfiler.scan(this);
        computeReport = ComputeProfiler.scan(this);
        ads = new AdSupport(this);
        currentModel = getPreferences(MODE_PRIVATE).getString("model", "");
        createUi();
        loadHistory();
        renderHistory();
        showTab(state==null?0:state.getInt("activeTab",0));
        if(state!=null){
            attachmentScope=state.getString("attachmentScope","");
            exportScope=state.getString("exportScope","");
            exportDigest=state.getString("exportDigest","");
            exportModel=state.getString("exportModel","");
            exportJson=state.getBoolean("exportJson",false);
        }
        // Draft recovery uses the current per-conversation preference only.
        // A saved Bundle from before a completed turn must not resurrect its prompt.
        startServer();
        ui.postDelayed(this::refreshModels, 2000);
        if (!getPreferences(MODE_PRIVATE).getBoolean("welcome_v2_seen", false)) {
            ui.postDelayed(this::showFirstRunGuide, 400);
        } else {
            ads.prepareConsent();
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("activeTab",activeTab);
        out.putString("attachmentScope",attachmentScope);
        out.putString("exportScope",exportScope);out.putString("exportDigest",exportDigest);
        out.putString("exportModel",exportModel);out.putBoolean("exportJson",exportJson);
        saveLocalDraft();
    }
    @Override protected void onResume() {
        super.onResume();
        foreground=true;
        if(guestOnly)return;
        if(cloudMode&&cloudPanel!=null)cloudPanel.resume();
        ui.post(poll);
        ui.post(hudPoll);
        ui.removeCallbacks(sunsetThemePoll);
        ui.post(sunsetThemePoll);
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastPauseElapsed > 0 && now - lastPauseElapsed > 120_000
                && getPreferences(MODE_PRIVATE).getBoolean("welcome_v2_seen", false)
                && !cloudMode && !pullBusy && !chatBusy && !benchmarkBusy && (voice==null||(!voice.listening()&&!voice.speaking()))) {
            ads.showAppOpenIfReady();
        }
    }
    @Override protected void onPause() {
        foreground=false;
        saveLocalDraft();
        if(cloudPanel!=null)cloudPanel.pause();
        if(voice!=null)voice.cancel();
        lastPauseElapsed = android.os.SystemClock.elapsedRealtime();
        ui.removeCallbacks(poll);
        ui.removeCallbacks(hudPoll);
        ui.removeCallbacks(sunsetThemePoll);
        super.onPause();
    }
    @Override protected void onDestroy() {
        saveLocalDraft();
        chatCancelled=true;
        if(navigationDrawer!=null&&navigationDrawer.isShowing())navigationDrawer.dismiss();
        if (chatConnection != null) chatConnection.disconnect();
        if (pullConnection != null) pullConnection.disconnect();
        ui.removeCallbacks(hudPoll);
        ui.removeCallbacks(sunsetThemePoll);
        if(onlineSearchRunnable!=null)ui.removeCallbacks(onlineSearchRunnable);
        if(onlineSearchTask!=null)onlineSearchTask.cancel(true);
        if(onlineHfTask!=null)onlineHfTask.cancel(true);
        chatSearchSerial++;attachmentSerial++;
        if(chatSearchTask!=null)chatSearchTask.cancel(true);
        chatSearchIo.shutdownNow();
        io.shutdownNow();
        if(cloudPanel!=null)cloudPanel.destroy();
        if(voice!=null)voice.close();
        if(ads!=null)ads.destroy();
        super.onDestroy();
    }

    private void enterGuestMode(){
        if(isUiBusy()){toast(getString(R.string.busy));return;}
        if("host".equals(CoopSession.get(this).role())){toast(getString(R.string.guest_stop_first));return;}
        new AlertDialog.Builder(this).setTitle(R.string.guest)
            .setMessage(R.string.guest_switch).setNegativeButton(R.string.cancel,null)
            .setPositiveButton(R.string.ok,(d,w)->{
                if(isUiBusy()||"host".equals(CoopSession.get(this).role()))return;
                if(voice!=null)voice.cancel();
                GpuRunner.stop();DistributedRunner.stop();
                stopService(new Intent(this,OllamaForegroundService.class));
                getPreferences(MODE_PRIVATE).edit().putBoolean("guest_compute_only",true).apply();
                recreate();
            }).show();
    }
    private void createGuestPage(){
        // No local inference server, chat/model downloads, ad SDK initialization or model polling here.
        LinearLayout page=column();page.setBackgroundColor(BG);page.setPadding(dp(22),dp(50),dp(22),dp(24));
        setContentView(page);
        margin(text(getString(R.string.app_name),27,WHITE,true),page,0,15);
        margin(text(getString(R.string.guest),21,WHITE,true),page,0,12);
        margin(text(getString(R.string.guest_desc),15,MUTED,false),page,0,20);
        Button join=button(getString(R.string.guest_session),true);margin(join,page,0,10);
        join.setOnClickListener(v->CoopUi.show(this,true));
        Button language=button(getString(R.string.language),false);margin(language,page,0,10);
        language.setOnClickListener(v->OnHandsLocale.choose(this));
        Button exit=button(getString(R.string.guest_exit),false);margin(exit,page,0,10);
        exit.setOnClickListener(v->{
            if("guest".equals(CoopSession.get(this).role())||"host".equals(CoopSession.get(this).role())){
                toast(getString(R.string.guest_stop_first));return;
            }
            getPreferences(MODE_PRIVATE).edit().putBoolean("guest_compute_only",false).apply();recreate();
        });
    }
    private void openCloud(){
        if(isUiBusy()||guestOnly){toast(getString(R.string.busy));return;}
        if(cloudMode)return;
        if(voice!=null)voice.cancel();
        if(cloudPanel==null){
            cloudPanel=new CloudChatPanel(this,this::returnToLocal);
            chatPage.addView(cloudPanel,new LinearLayout.LayoutParams(-1,0,1));
        }
        cloudMode=true;
        localChatContent.setVisibility(View.GONE);cloudPanel.setVisibility(View.VISIBLE);
        cloudPanel.activate();showTab(0);
    }
    private void returnToLocal(){
        if(!cloudMode)return;
        cloudMode=false;
        if(cloudPanel!=null){cloudPanel.pause();cloudPanel.setVisibility(View.GONE);}
        if(localChatContent!=null)localChatContent.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if(activeTab==0&&topPageSubtitle!=null)topPageSubtitle.setText(getString(R.string.chat)+"  ·  "+getString(R.string.local));
        refreshTabletRail();
    }
    @Override public void onBackPressed(){
        if(cloudMode){returnToLocal();return;}
        super.onBackPressed();
    }
    private void openVoice(){
        if(isUiBusy()||guestOnly)return;
        if(voice!=null&&(voice.listening()||voice.speaking())){voice.cancel();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},VOICE_PERMISSION);
            toast(getString(R.string.voice_permission));return;
        }
        new AlertDialog.Builder(this).setTitle(R.string.voice_options)
            .setMessage(R.string.voice_notice)
            .setNegativeButton(R.string.cancel,null)
            .setNeutralButton(R.string.dictate,(d,w)->startVoice(false))
            .setPositiveButton(R.string.voice_turn,(d,w)->startVoice(true)).show();
    }
    private void startVoice(boolean conversational){
        if(!foreground||isUiBusy()||guestOnly)return;
        if(conversational&&(currentModel.isEmpty()||knownModels.isEmpty())){
            toast(getString(R.string.need_model));showTab(1);return;
        }
        if(voice==null)voice=new OnHandsVoice(this);
        voice.listen(value->{
            if(!foreground||isFinishing()||isDestroyed()||activeTab!=0||isUiBusy())return;
            chatInput.setText(value);chatInput.setSelection(chatInput.length());
            if(conversational){speakNextAnswer=true;sendChat();speakNextAnswer=false;}
        });
    }

    private int dp(int size) { return (int)(size * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout.LayoutParams lp(int width, int height) { return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height)); }
    private int borderTone(){
        return lightTheme()?Color.rgb(237,238,241):Color.rgb(53,55,61);
    }
    private GradientDrawable bg(int color, int radius) {
        // Flat surfaces by default; only interactive model rows get a light outline.
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);g.setCornerRadius(dp(radius));
        return g;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value);
        t.setTextSize(size*uiTextScale); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private EditText edit(String hint, String value, boolean multiline) {
        EditText e = new EditText(this); e.setTextColor(WHITE); e.setHintTextColor(MUTED);
        e.setTextSize(15*uiTextScale); e.setHint(hint); e.setText(value); e.setPadding(dp(12), dp(9), dp(12), dp(9));
        e.setBackground(bg(FIELD, 12)); e.setSingleLine(!multiline);
        if (multiline) { e.setMinLines(2); e.setMaxLines(4); }
        return e;
    }
    private Button button(String label, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setTextSize(13*uiTextScale); b.setTextColor(primary ? BG : WHITE);
        b.setPadding(dp(11), dp(6), dp(11), dp(6)); b.setMinHeight(dp(44)); b.setMinimumHeight(dp(44));
        b.setBackground(bg(primary ? ACCENT : FIELD, 11));
        b.setElevation(0);return b;
    }
    private void margin(View v, LinearLayout host, int top, int bottom) {
        LinearLayout.LayoutParams p = lp(-1, -2); p.topMargin = dp(top); p.bottomMargin = dp(bottom); host.addView(v, p);
    }
    private void rowItem(LinearLayout r, View view, int weight, int marginRight) {
        LinearLayout.LayoutParams p = weight > 0 ? new LinearLayout.LayoutParams(0, -2, weight) : lp(-2, -2);
        p.rightMargin = dp(marginRight); r.addView(view, p);
    }
    private void createUi() {
        LinearLayout root = column(); root.setBackgroundColor(BG);
        root.setPadding(dp(12),dp(15),dp(12),dp(10));
        // Android 15+ enforces edge-to-edge. Respect real status-bar/cutout
        // insets instead of allowing the menu / chat title to be drawn under it.
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top=0,bottom=0,left=0,right=0;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.statusBars()|
                    WindowInsets.Type.navigationBars()|WindowInsets.Type.displayCutout());
                top=bars.top;bottom=bars.bottom;left=bars.left;right=bars.right;
            }else{
                top=insets.getSystemWindowInsetTop();
                bottom=insets.getSystemWindowInsetBottom();
            }
            boolean edge=Build.VERSION.SDK_INT>=35;
            int safeTop=dp(15)+(edge?top:0),safeBottom=dp(10)+(edge?bottom:0);
            int safeLeft=dp(12)+(edge?left:0),safeRight=dp(12)+(edge?right:0);
            if(view.getPaddingTop()!=safeTop||view.getPaddingBottom()!=safeBottom||
               view.getPaddingLeft()!=safeLeft||view.getPaddingRight()!=safeRight)
                view.setPadding(safeLeft,safeTop,safeRight,safeBottom);
            return insets;
        });
        setContentView(root);
        root.post(root::requestApplyInsets);
        // Mobile-friendly WebUI-inspired navigation; independent Android widgets and branding.
        LinearLayout header = row();
        Button menu = button("☰", false);
        menuButton=menu;
        menu.setContentDescription(getString(R.string.menu));
        menu.setTextSize(20);
        menu.setMinWidth(dp(46));menu.setMinimumWidth(dp(46));
        rowItem(header, menu, 0, 9);
        menu.setOnClickListener(v -> showNavigationDrawer());
        LinearLayout branding = column();
        topPageTitle = text("OnHand AI", 19, WHITE, true);
        branding.addView(topPageTitle);
        topPageSubtitle=text(getString(R.string.chat)+"  ·  "+getString(R.string.local), 10, MUTED, false);
        branding.addView(topPageSubtitle);
        rowItem(header, branding, 1, 5);
        Button fresh = button("＋ "+getString(R.string.new_chat), false);
        rowItem(header, fresh, 0, 0);
        fresh.setOnClickListener(v -> newChat());
        header.setMinimumHeight(dp(58));
        root.addView(header, lp(-1, -2));
        miniStatus = text("● "+getString(R.string.engine_check), 11, MUTED, false);
        margin(miniStatus, root, 2, 6);
        performanceHudView=text(getString(R.string.hud_wait),11,MUTED,false);
        performanceHudView.setPadding(dp(12),dp(9),dp(12),dp(9));
        performanceHudView.setMaxLines(4);
        performanceHudView.setTextSize(11*uiTextScale);
        performanceHudView.setBackground(bg(FIELD,11));
        margin(performanceHudView,root,0,5);
        performanceHudView.setVisibility(showHud?View.VISIBLE:View.GONE);
        performanceHudView.setOnClickListener(v->{
            PerformanceHud.Sample sample=lastHud;
            String status=sample==null?getString(R.string.hud_check):sample.detail(this);
            if(deviceProfile!=null)status=deviceProfile.description(this)+"\n\n"+status;
            if(computeReport!=null)status+="\n\n"+computeReport.description(this);
            if("host".equals(CoopSession.get(this).role()))
                status+="\n"+getString(R.string.node_count_hud,CoopSession.get(this).readyNodes().size());
            new AlertDialog.Builder(this).setTitle(getString(R.string.hud_title))
                .setMessage(status).setPositiveButton(getString(R.string.close),null).show();
        });
        pages = new FrameLayout(this);
        workspace=new LinearLayout(this);
        workspace.setOrientation(LinearLayout.HORIZONTAL);
        tabletRail=createTabletRail();
        tabletGap=new View(this);
        tabletRail.setVisibility(View.GONE);tabletGap.setVisibility(View.GONE);
        workspace.addView(tabletRail,new LinearLayout.LayoutParams(0,-1));
        workspace.addView(tabletGap,new LinearLayout.LayoutParams(dp(WorkspaceLayoutPolicy.GAP_DP),-1));
        workspace.addView(pages,new LinearLayout.LayoutParams(0,-1,1f));
        root.addView(workspace,new LinearLayout.LayoutParams(-1,0,1f));
        createChatPage(); createModelPage(); createServerPage(); createDiagnosticsPage();
        pages.addView(chatPage); pages.addView(modelPage);
        pages.addView(serverPage); pages.addView(diagnosticPage);
        // Measure the actual usable window, including multi-window and landscape cutouts.
        // Switch panes without recreating the Activity, engine, draft or cloud panel.
        workspace.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(r-l!=or-ol)applyWorkspaceLayout();
        });
        workspace.post(this::applyWorkspaceLayout);
    }

    private int workspaceChoiceLabel(){
        return WorkspaceLayoutPolicy.PHONE.equals(workspaceMode)?R.string.workspace_phone:
            WorkspaceLayoutPolicy.TABLET.equals(workspaceMode)?R.string.workspace_tablet:R.string.workspace_auto;
    }
    private void refreshWorkspaceModeLabel(){
        if(workspaceModeButton==null)return;
        workspaceModeButton.setText(getString(R.string.workspace_mode)+" · "+getString(workspaceChoiceLabel())+
            "\n"+getString(currentRailDp>0?R.string.workspace_wide:R.string.workspace_compact));
    }
    private void chooseWorkspaceMode(){
        final String[] modes={WorkspaceLayoutPolicy.AUTO,WorkspaceLayoutPolicy.PHONE,WorkspaceLayoutPolicy.TABLET};
        int selected=Math.max(0,java.util.Arrays.asList(modes).indexOf(workspaceMode));
        new AlertDialog.Builder(this).setTitle(R.string.workspace_mode)
            .setSingleChoiceItems(new String[]{getString(R.string.workspace_auto),getString(R.string.workspace_phone),getString(R.string.workspace_tablet)},selected,(dialog,index)->{
                dialog.dismiss();
                workspaceMode=modes[index];
                getPreferences(MODE_PRIVATE).edit().putString("workspace_mode",workspaceMode).apply();
                applyWorkspaceLayout();
            }).setNegativeButton(R.string.cancel,null).show();
    }
    private void applyWorkspaceLayout(){
        if(workspace==null||tabletRail==null||isFinishing()||isDestroyed())return;
        int widthDp=(int)(workspace.getWidth()/getResources().getDisplayMetrics().density);
        float scale=uiTextScale*Math.max(1f,getResources().getConfiguration().fontScale);
        int desired=WorkspaceLayoutPolicy.railWidthDp(workspaceMode,widthDp,scale);
        if(desired!=currentRailDp){
            currentRailDp=desired;
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)tabletRail.getLayoutParams();
            lp.width=dp(desired);tabletRail.setLayoutParams(lp);
            boolean wide=desired>0;
            tabletRail.setVisibility(wide?View.VISIBLE:View.GONE);
            tabletGap.setVisibility(wide?View.VISIBLE:View.GONE);
            if(menuButton!=null)menuButton.setVisibility(wide?View.GONE:View.VISIBLE);
            if(wide&&navigationDrawer!=null&&navigationDrawer.isShowing())navigationDrawer.dismiss();
            if(wide)refreshTabletRail();
        }
        refreshWorkspaceModeLabel();
    }
    private ScrollView createTabletRail(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(bg(CARD,14));
        LinearLayout rail=column();rail.setPadding(dp(10),dp(10),dp(10),dp(10));
        scroll.addView(rail,new ScrollView.LayoutParams(-1,-2));
        Button fresh=button(getString(R.string.workspace_new_local),true);
        fresh.setMinHeight(dp(48));margin(fresh,rail,0,8);
        fresh.setOnClickListener(v->{
            if(chatBusy||(cloudPanel!=null&&cloudPanel.isBusy())){toast(getString(R.string.busy));return;}
            if(cloudMode)returnToLocal();
            newChat();
        });
        int[] titles={R.string.cloud_return,R.string.cloud_short,R.string.models,R.string.settings,R.string.devices};
        for(int i=0;i<titles.length;i++){
            final int target=i;
            TextView item=drawerRow(getString(titles[i]),"",false);
            item.setMinHeight(dp(48));item.setFocusable(true);item.setClickable(true);
            tabletLinks[i]=item;margin(item,rail,0,3);
            item.setOnClickListener(v->{
                if(target==1){openCloud();return;}
                if(target==0){if(cloudMode){if(cloudPanel!=null&&cloudPanel.isBusy()){toast(getString(R.string.busy));return;}returnToLocal();}showTab(0);return;}
                if(cloudPanel!=null&&cloudPanel.isBusy()){toast(getString(R.string.busy));return;}
                showTab(target-1);
            });
        }
        margin(text(getString(R.string.workspace_local_history),12,MUTED,true),rail,14,5);
        tabletSearch=edit(getString(R.string.chat_search_content),"",false);
        tabletSearch.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(LocalChatLibrary.MAX_QUERY)});
        tabletSearch.setSingleLine(true);tabletSearch.setMinHeight(dp(48));margin(tabletSearch,rail,0,7);
        tabletChats=column();margin(tabletChats,rail,0,4);
        tabletSearch.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){refreshTabletRail();}
            @Override public void afterTextChanged(Editable e){}
        });
        return scroll;
    }
    private void refreshTabletRail(){
        if(currentRailDp<=0||tabletChats==null||tabletSearch==null)return;
        int selected=cloudMode?1:activeTab==0?0:activeTab+1;
        for(int i=0;i<tabletLinks.length;i++){
            TextView item=tabletLinks[i];if(item==null)continue;
            boolean active=i==selected;
            item.setSelected(active);item.setTypeface(Typeface.DEFAULT,active?Typeface.BOLD:Typeface.NORMAL);
            item.setBackground(bg(active?FIELD:BG,10));
            item.setContentDescription(item.getText()+(active?" · "+getString(R.string.workspace_selected):""));
        }
        renderDrawerChats(tabletChats,null,allSavedConversations(),tabletSearch.getText().toString());
    }
    private int chatBubbleMaxWidth(){
        int width=messageScroll==null?0:messageScroll.getWidth();
        if(width<=0)width=workspace==null?getResources().getDisplayMetrics().widthPixels:workspace.getWidth()-dp(currentRailDp+(currentRailDp>0?WorkspaceLayoutPolicy.GAP_DP:0));
        if(width<=0)width=getResources().getDisplayMetrics().widthPixels;
        int padding=messages==null?dp(8):messages.getPaddingLeft()+messages.getPaddingRight();
        return Math.max(dp(80),(int)(Math.max(0,width-padding)*0.82f));
    }
    private void refreshBubbleWidths(){
        if(messages==null)return;
        for(int i=0;i<messages.getChildCount();i++){
            View child=messages.getChildAt(i);
            if(child instanceof LinearLayout){
                LinearLayout wrapper=(LinearLayout)child;
                if((wrapper.getGravity()&Gravity.HORIZONTAL_GRAVITY_MASK)==Gravity.RIGHT&&wrapper.getChildCount()>0&&wrapper.getChildAt(0) instanceof TextView)
                    ((TextView)wrapper.getChildAt(0)).setMaxWidth(chatBubbleMaxWidth());
            }
        }
    }

    private void createChatPage() {
        chatPage = column();
        localChatContent=column();
        chatPage.addView(localChatContent,new LinearLayout.LayoutParams(-1,0,1));
        localChatContent.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(r-l==or-ol)return;
            // Keep long conversations readable even on large external displays.
            int side=Math.max(0,(r-l-dp(960))/2);
            if(v.getPaddingLeft()!=side||v.getPaddingRight()!=side)v.setPadding(side,0,side,0);
        });
        LinearLayout modelRow = row();
        TextView modelLabel = text(getString(R.string.model_label), 12, MUTED, false);
        rowItem(modelRow, modelLabel, 0, 12);
        modelChooser = new Spinner(this); modelChooser.setBackground(bg(FIELD, 12));
        modelChooser.setPadding(dp(12), dp(4), dp(8), dp(4));
        rowItem(modelRow, modelChooser, 1, 6);
        cloudShortcut=button(getString(R.string.cloud_short),false);
        cloudShortcut.setContentDescription(getString(R.string.cloud));
        rowItem(modelRow,cloudShortcut,0,0);
        cloudShortcut.setOnClickListener(v->openCloud());
        margin(modelRow, localChatContent, 2, 7);
        activeEngineLabel = text(getString(R.string.engine_idle), 11, MUTED, false);
        LinearLayout chatTools=row();
        rowItem(chatTools,activeEngineLabel,1,5);
        attachFileButton=button("+",false);
        attachFileButton.setContentDescription(getString(R.string.attachment_pick));
        attachFileButton.setMinWidth(dp(48));attachFileButton.setMinimumWidth(dp(48));attachFileButton.setMinimumHeight(dp(48));
        attachFileButton.setOnClickListener(v->pickChatAttachment());rowItem(chatTools,attachFileButton,0,3);
        exportConversation=button(getString(R.string.chat_export),false);
        exportConversation.setMinimumHeight(dp(48));
        exportConversation.setEnabled(false);
        exportConversation.setOnClickListener(v->chooseChatExport());
        rowItem(chatTools,exportConversation,0,0);
        margin(chatTools,localChatContent,0,7);
        modelChooser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (ignoreSelection || knownModels.isEmpty() || position >= knownModels.size()) return;
                String model = knownModels.get(position);
                if (model.equals(currentModel)) return;
                if (chatBusy||attachmentReading) { toast(getString(R.string.model_wait)); return; }
                saveLocalDraft();
                currentModel = model;
                getPreferences(MODE_PRIVATE).edit().putString("model", model).apply();
                loadHistory(); renderHistory();
            }
        });
        messageScroll = new ScrollView(this); messageScroll.setFillViewport(true);
        messageScroll.setBackgroundColor(BG);
        messages = column(); messages.setPadding(dp(4), dp(18), dp(4), dp(12));
        messageScroll.addView(messages);
        messageScroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(r-l!=or-ol)refreshBubbleWidths();
        });
        LinearLayout.LayoutParams scrollParam = new LinearLayout.LayoutParams(-1, 0, 1);
        localChatContent.addView(messageScroll, scrollParam);
        LinearLayout composer = row();
        composer.setPadding(dp(8), dp(7), dp(8), dp(7));
        composer.setBackground(bg(CARD, 22));
        chatInput = edit(getString(R.string.message_hint), "", true);
        chatInput.setMinLines(1); chatInput.setMaxLines(4);
        rowItem(composer, chatInput, 1, 5);
        voiceButton=button("🎙",false);
        voiceButton.setContentDescription(getString(R.string.voice));
        voiceButton.setMinWidth(dp(44));voiceButton.setMinimumWidth(dp(44));
        rowItem(composer,voiceButton,0,4);
        voiceButton.setOnClickListener(v->openVoice());
        voiceButton.setOnLongClickListener(v->{if(voice!=null)voice.cancel();return true;});
        sendButton = button("↑", true);
        sendButton.setContentDescription(getString(R.string.send));
        sendButton.setTextSize(19);
        rowItem(composer, sendButton, 0, 4);
        stopChatButton = button("■", false);
        stopChatButton.setContentDescription(getString(R.string.stop));
        rowItem(composer, stopChatButton, 0, 0);
        margin(composer, localChatContent, 8, 2);
        stopChatButton.setEnabled(false);stopChatButton.setVisibility(View.GONE);
        sendButton.setMinWidth(dp(48));sendButton.setMinimumWidth(dp(48));
        stopChatButton.setMinWidth(dp(48));stopChatButton.setMinimumWidth(dp(48));
        voiceButton.setMinWidth(dp(48));voiceButton.setMinimumWidth(dp(48));
        sendButton.setOnClickListener(v -> sendChat());
        stopChatButton.setOnClickListener(v -> stopChat());
        margin(text(getString(R.string.disclaimer),
            10, MUTED, false), localChatContent, 0, 0);
    }

    private void createModelPage() {
        modelPage=column();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout content=column();content.setPadding(dp(4),dp(8),dp(4),dp(18));
        scroll.addView(content);modelPage.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        margin(text(getString(R.string.recommended),23,WHITE,true),content,0,3);
        margin(text(getString(R.string.recommend_basis),13,MUTED,false),
            content,0,10);
        LinearLayout top=row();
        modelListHeader=text(getString(R.string.recommended_six),18,WHITE,true);
        rowItem(top,modelListHeader,1,5);
        Button refresh=button(getString(R.string.refresh),false);rowItem(top,refresh,0,0);
        refresh.setOnClickListener(v->{refreshModels();if(modelFilter!=null&&!modelFilter.getText().toString().trim().isEmpty())onModelSearchChanged(modelFilter.getText().toString(),true);else loadRecommendedVariants(true);});
        margin(top,content,0,7);
        LinearLayout viewModeRow=row();
        modelListViewButton=button(getString(R.string.list_view),modelListView);
        modelGridViewButton=button(getString(R.string.grid_view),!modelListView);
        rowItem(viewModeRow,modelListViewButton,1,6);
        rowItem(viewModeRow,modelGridViewButton,1,0);
        margin(viewModeRow,content,0,9);
        modelListViewButton.setOnClickListener(v->setModelViewMode(true));
        modelGridViewButton.setOnClickListener(v->setModelViewMode(false));
        updateModelViewButtons();
        LinearLayout searchRow=row();
        modelFilter=edit(getString(R.string.model_search),"",false);
        rowItem(searchRow,modelFilter,1,6);
        Button submitSearch=button(getString(R.string.search),true);
        rowItem(searchRow,submitSearch,0,0);margin(searchRow,content,0,5);
        submitSearch.setOnClickListener(v->onModelSearchChanged(modelFilter.getText().toString(),true));
        modelFilter.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        modelFilter.setOnEditorActionListener((v,action,event)->{
            if(action!=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH)return false;
            onModelSearchChanged(modelFilter.getText().toString(),true);return true;
        });
        LinearLayout searchOptions=row();
        sourceFilter=new Spinner(this);sourceFilter.setBackground(bg(FIELD,12));
        sourceFilter.setMinimumHeight(dp(46));
        sourceFilter.setAdapter(makeFilterAdapter(java.util.Arrays.asList("전체에서 찾기","Ollama에서 찾기","Hugging Face에서 찾기")));
        sortFilter=new Spinner(this);sortFilter.setBackground(bg(FIELD,12));sortFilter.setMinimumHeight(dp(46));
        sortFilter.setAdapter(makeFilterAdapter(java.util.Arrays.asList("추천 순","용량 작은 순","용량 큰 순","이름 순")));
        rowItem(searchOptions,sourceFilter,1,6);rowItem(searchOptions,sortFilter,1,0);
        margin(searchOptions,content,0,5);
        sourceFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(AdapterView<?> p){}
            @Override public void onItemSelected(AdapterView<?> p,View view,int pos,long id){
                String next=String.valueOf(p.getItemAtPosition(pos));
                if(!next.equals(selectedSource)){selectedSource=next;onModelSearchChanged(modelFilter.getText().toString());}
            }
        });
        sortFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(AdapterView<?> p){}
            @Override public void onItemSelected(AdapterView<?> p,View view,int pos,long id){
                String next=String.valueOf(p.getItemAtPosition(pos));
                if(!next.equals(selectedSort)){selectedSort=next;renderInstalledModels();}
            }
        });
        modelSearchStatus=text(getString(R.string.search_intro),11,MUTED,false);
        margin(modelSearchStatus,content,0,9);
        modelFilter.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                onModelSearchChanged(s.toString());
            }
            @Override public void afterTextChanged(Editable s){}
        });
        LinearLayout filterRow=row();
        familyFilter=new Spinner(this);
        familyFilter.setBackground(bg(FIELD,12));
        familyFilter.setMinimumHeight(dp(46));
        quantizationFilter=new Spinner(this);
        quantizationFilter.setBackground(bg(FIELD,12));
        quantizationFilter.setMinimumHeight(dp(46));
        rowItem(filterRow,familyFilter,1,6);
        rowItem(filterRow,quantizationFilter,1,0);
        margin(filterRow,content,0,11);
        margin(text(getString(R.string.filter_label),11,MUTED,false),content,0,9);
        populateModelFilters();
        modelRows=column();margin(modelRows,content,0,7);
        modelRows.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int width=r-l;
            if(width>0&&width!=lastModelGridWidth){lastModelGridWidth=width;ui.post(this::renderInstalledModels);}
        });
        installedPickerButton=button(getString(R.string.installed_all),false);
        margin(installedPickerButton,content,0,16);
        installedPickerButton.setOnClickListener(v->showAllInstalledModels());
        margin(text(getString(R.string.import_other),18,WHITE,true),content,0,6);
        Button importButton=button(getString(R.string.import_gguf),false);
        margin(importButton,content,0,8);
        importButton.setOnClickListener(v->{
            if(!OllamaForegroundService.isRunning()){toast(getString(R.string.start_ollama));return;}
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");startActivityForResult(intent,PICK_GGUF);
        });
        // Keep the legacy direct-tag fallback, but do not duplicate the main search UI.
        Button manualToggle=button(getString(R.string.advanced_closed),false);
        margin(manualToggle,content,0,7);
        LinearLayout manualDownloadPanel=column();
        manualDownloadPanel.setVisibility(View.GONE);
        margin(text(getString(R.string.manual_tag_help),
            12,MUTED,false),manualDownloadPanel,0,7);
        String recommendedDefault="qwen2.5:0.5b";
        for(DeviceProfiler.Recommendation rec:DeviceProfiler.recommend(deviceProfile)){
            if(rec.selectable){recommendedDefault=rec.model;break;}
        }
        modelInput=edit(getString(R.string.model_tag_example),recommendedDefault,false);
        margin(modelInput,manualDownloadPanel,0,7);
        pullButton=button(getString(R.string.download_tag),true);
        margin(pullButton,manualDownloadPanel,0,8);
        pullButton.setOnClickListener(v->pullModel());
        margin(manualDownloadPanel,content,0,6);
        manualToggle.setOnClickListener(v->{
            boolean expanded=manualDownloadPanel.getVisibility()==View.VISIBLE;
            manualDownloadPanel.setVisibility(expanded?View.GONE:View.VISIBLE);
            manualToggle.setText(expanded?getString(R.string.advanced_closed):
                getString(R.string.advanced_open));
        });

        LinearLayout downloadStatusRow=row();
        progressStatus=text(getString(R.string.download_status_hint),12,MUTED,false);
        rowItem(downloadStatusRow,progressStatus,1,6);
        downloadRetryButton=button(getString(R.string.retry),false);
        rowItem(downloadStatusRow,downloadRetryButton,0,6);
        downloadRetryButton.setVisibility(View.GONE);
        downloadRetryButton.setOnClickListener(v->{
            if(lastFailedPullModel.isEmpty()||pullBusy)return;
            modelInput.setText(lastFailedPullModel);
            pullModel();
        });
        cancelPullButton=button(getString(R.string.cancel_download),false);
        rowItem(downloadStatusRow,cancelPullButton,0,0);
        cancelPullButton.setVisibility(View.GONE);
        cancelPullButton.setEnabled(false);
        cancelPullButton.setOnClickListener(v->{
            pullCancelled=true;if(pullConnection!=null)pullConnection.disconnect();
        });
        margin(downloadStatusRow,content,0,5);
        downloadProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        downloadProgress.setMax(100);downloadProgress.setProgress(0);
        downloadProgress.setVisibility(View.GONE);
        margin(downloadProgress,content,0,8);
        margin(text(getString(BuildConfig.DEBUG?R.string.ad_test:R.string.ad_label),11,MUTED,false),content,0,3);
        downloadAdSlot=new FrameLayout(this);margin(downloadAdSlot,content,0,4);
        ads.register(downloadAdSlot,AdSupport.PLACEMENT_DOWNLOAD);
        margin(text(getString(R.string.network_data_notice),
            11,MUTED,false),content,0,15);
        // Lower duplicate "recommended models" section deliberately removed.
        // GGUF import stays visible; manual Ollama tags are a collapsed advanced option.
    }

    void registerHfDownloadAd(FrameLayout slot) {
        ads.register(slot,AdSupport.PLACEMENT_DOWNLOAD);
        ads.showBanner(slot);
        ads.prepareConsent();
    }
    void unregisterHfDownloadAd(FrameLayout slot) { ads.detach(slot); }

    private void createServerPage() {
        serverPage=column();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout content=column();content.setPadding(dp(4),dp(8),dp(4),dp(20));
        scroll.addView(content);
        serverPage.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        margin(text(getString(R.string.settings),23,WHITE,true),content,0,5);
        margin(text(getString(R.string.settings_intro),13,MUTED,false),content,0,15);
        Button language=button(getString(R.string.language),false);margin(language,content,0,8);
        language.setOnClickListener(v->{if(isUiBusy())toast(getString(R.string.busy));else OnHandsLocale.choose(this);});
        Button cloud=button(getString(R.string.cloud),false);margin(cloud,content,0,8);
        cloud.setOnClickListener(v->openCloud());
        Button guest=button(getString(R.string.guest),false);margin(guest,content,0,14);
        guest.setOnClickListener(v->enterGuestMode());

        margin(text(getString(R.string.display),17,WHITE,true),content,0,8);
        workspaceModeButton=button(getString(R.string.workspace_mode),false);
        workspaceModeButton.setMinHeight(dp(56));margin(workspaceModeButton,content,0,5);
        workspaceModeButton.setOnClickListener(v->chooseWorkspaceMode());
        refreshWorkspaceModeLabel();
        margin(text(getString(R.string.workspace_help),11,MUTED,false),content,0,12);
        String currentTheme=getString("auto".equals(themeMode)?R.string.theme_auto:activeThemeDark?R.string.theme_dark:R.string.theme_light);
        Button theme=button(getString(R.string.theme)+"  ·  "+currentTheme,false);
        margin(theme,content,0,6);
        theme.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle(getString(R.string.theme))
            .setSingleChoiceItems(new String[]{getString(R.string.theme_light_default),getString(R.string.theme_dark),getString(R.string.theme_auto_detail)},
                "auto".equals(themeMode)?2:activeThemeDark?1:0,
                (dialog,which)->{
                    dialog.dismiss();
                    applySetting("ui_theme",which==2?"auto":which==1?"dark":"light");
                }).setNegativeButton(getString(R.string.cancel),null).show());
        margin(text(getString(R.string.theme_location),
            11,MUTED,false),content,0,8);
        String chosenCity=getPreferences(MODE_PRIVATE).getString("sunset_city","device");
        Button cityChoice=button(getString(R.string.theme_city)+"  ·  "+
            UiText.cityLabels(this)[SolarTheme.cityIndex(chosenCity)],false);
        margin(cityChoice,content,0,12);
        cityChoice.setVisibility("auto".equals(themeMode)?View.VISIBLE:View.GONE);
        cityChoice.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle(getString(R.string.theme_city_title))
            .setSingleChoiceItems(UiText.cityLabels(this),SolarTheme.cityIndex(chosenCity),
                (dialog,index)->{
                    dialog.dismiss();
                    applySetting("sunset_city",SolarTheme.CITY_CODES[index]);
                }).setNegativeButton(getString(R.string.cancel),null).show());

        String f=getPreferences(MODE_PRIVATE).getString("font_size","normal");
        String sizeLabel=getString("large".equals(f)?R.string.font_large:"extra".equals(f)?R.string.font_extra:R.string.font_normal);
        Button typeSize=button(getString(R.string.font_size)+"  ·  "+sizeLabel,false);
        margin(typeSize,content,0,8);
        typeSize.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle(getString(R.string.font_size))
            .setSingleChoiceItems(new String[]{getString(R.string.font_normal)+" (100%)",getString(R.string.font_large)+" (113%)",getString(R.string.font_extra)+" (126%)"},
                "large".equals(f)?1:"extra".equals(f)?2:0,
                (dialog,which)->{
                    dialog.dismiss();
                    applySetting("font_size",which==1?"large":which==2?"extra":"normal");
                }).setNegativeButton(getString(R.string.cancel),null).show());

        Button hud=button(getString(R.string.hud_title)+"  ·  "+getString(showHud?R.string.hud_visible:R.string.hud_hidden),false);
        margin(hud,content,0,8);
        hud.setOnClickListener(v->{
            if(isUiBusy()){toast(getString(R.string.busy));return;}
            getPreferences(MODE_PRIVATE).edit().putBoolean("show_hud",!showHud).apply();
            recreate();
        });
        Button motion=button(getString(R.string.motion)+"  ·  "+getString(smoothMotion?R.string.on:R.string.off),false);
        margin(motion,content,0,14);
        motion.setOnClickListener(v->{
            if(isUiBusy()){toast(getString(R.string.busy));return;}
            getPreferences(MODE_PRIVATE).edit().putBoolean("smooth_motion",!smoothMotion).apply();
            recreate();
        });

        margin(text(getString(R.string.local_inference),17,WHITE,true),content,0,8);
        Button compute=button(getString(R.string.compute),false);
        margin(compute,content,0,8);
        compute.setOnClickListener(v->showComputeChoiceDialog());
        serverStatus=text(getString(R.string.server_check),13,MUTED,true);
        margin(serverStatus,content,0,8);
        LinearLayout controls=row();
        Button start=button(getString(R.string.server_start),true),stop=button(getString(R.string.server_stop),false);
        rowItem(controls,start,1,7);rowItem(controls,stop,1,0);
        margin(controls,content,0,14);
        start.setOnClickListener(v->startServer());
        stop.setOnClickListener(v->stopServer());
        margin(text(getString(R.string.inference_notice),
            12,MUTED,false),content,0,16);

        margin(text(getString(R.string.privacy),17,WHITE,true),content,0,8);
        Button privacy=button(getString(R.string.privacy_settings),false);
        margin(privacy,content,0,7);
        privacy.setOnClickListener(v->ads.showPrivacyChoices());
        margin(text(getString(R.string.ad_notice),
            12,MUTED,false),content,0,16);

        margin(text(getString(R.string.about),17,WHITE,true),content,0,8);
        Button license=button(getString(R.string.license),false);
        margin(license,content,0,5);
        license.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle(getString(R.string.license_title))
            .setMessage(R.string.license_notice)
            .setPositiveButton(getString(R.string.close),null).show());
        margin(text(getString(R.string.independent_client),12,MUTED,false),content,8,0);
    }

    private boolean isUiBusy(){
        return attachmentReading||chatBusy||pullBusy||diagnosticBusy||benchmarkBusy
               ||(cloudPanel!=null&&cloudPanel.isBusy())||HuggingFaceCatalog.isDownloading();
    }

    private void applySetting(String key,String value){
        if(isUiBusy()){toast(getString(R.string.busy));return;}
        getPreferences(MODE_PRIVATE).edit().putString(key,value).apply();
        recreate(); // preserves active tab and draft via onSaveInstanceState.
    }

    private void createDiagnosticsPage(){
        diagnosticPage=column();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout content=column();content.setPadding(dp(4),dp(8),dp(4),dp(20));
        scroll.addView(content);
        diagnosticPage.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        margin(text(getString(R.string.diag_title),23,WHITE,true),content,0,5);
        margin(text(getString(R.string.diag_intro),13,MUTED,false),
            content,0,12);

        LinearLayout ownTitle=row();
        rowItem(ownTitle,text(getString(R.string.own_device),18,WHITE,true),1,5);
        Button refresh=button(getString(R.string.diag_refresh),false);
        rowItem(ownTitle,refresh,0,0);
        margin(ownTitle,content,0,7);
        deviceSummary=text(deviceProfile.description(this),13,WHITE,false);
        deviceSummary.setPadding(dp(14),dp(13),dp(14),dp(13));
        deviceSummary.setBackground(bg(CARD,14));
        margin(deviceSummary,content,0,7);
        margin(text(getString(R.string.diag_suitability),18,WHITE,true),content,0,7);
        suitabilitySummary=text(getString(R.string.diag_models_check),13,WHITE,false);
        suitabilitySummary.setPadding(dp(14),dp(13),dp(14),dp(13));
        suitabilitySummary.setBackground(bg(CARD,14));
        margin(suitabilitySummary,content,0,10);
        computeSummary=text(computeReport.description(this),12,MUTED,false);
        computeSummary.setPadding(dp(14),dp(12),dp(14),dp(12));
        computeSummary.setBackground(bg(CARD,14));
        margin(computeSummary,content,0,8);
        localConnectionStatus=text(getString(R.string.connection_check),12,MUTED,false);
        margin(localConnectionStatus,content,0,10);
        refresh.setOnClickListener(v->{
            deviceProfile=DeviceProfiler.scan(this);
            computeReport=ComputeProfiler.scan(this);
            refreshDiagnosticViews();
            populateModelFilters();
            renderInstalledModels();
        });
        margin(text(getString(R.string.permissions),16,WHITE,true),content,0,6);
        margin(text(getString(R.string.diag_permission_detail),
            12,MUTED,false),content,0,7);
        Button notification=button(getString(R.string.notification_permission),false);
        margin(notification,content,0,14);
        notification.setOnClickListener(v->{
            if(Build.VERSION.SDK_INT<33){
                toast(getString(R.string.notification_legacy));return;
            }
            if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    ==PackageManager.PERMISSION_GRANTED){
                toast(getString(R.string.notification_allowed));return;
            }
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},3);
        });

        margin(text(getString(R.string.session_diagnostics),18,WHITE,true),content,0,6);
        connectionSessionStatus=text(getString(R.string.session_check),13,WHITE,false);
        connectionSessionStatus.setPadding(dp(14),dp(12),dp(14),dp(12));
        connectionSessionStatus.setBackground(bg(CARD,14));
        margin(connectionSessionStatus,content,0,8);
        Button coop=button(getString(R.string.session_join_host),true);
        margin(coop,content,0,9);
        coop.setOnClickListener(v->CoopUi.show(this));
        nodeRows=column();margin(nodeRows,content,0,10);

        margin(text(getString(R.string.performance_tests),18,WHITE,true),content,5,7);
        Button benchmark=button(getString(R.string.model_benchmark),true);
        margin(benchmark,content,0,7);
        benchmark.setOnClickListener(v->{
            if(isUiBusy()){toast(getString(R.string.busy));return;}
            ModelBenchmark.show(this,currentModel,
                ()->benchmarkBusy=true,()->{
                    benchmarkBusy=false;
                    renderInstalledModels();
                    if(activeTab==3)refreshDiagnosticViews();
                });
        });
        Button test=button(getString(R.string.hardware_test),false);
        margin(test,content,0,6);
        diagnosticStatus=text(getString(R.string.hardware_test_help),
            12,MUTED,false);
        margin(diagnosticStatus,content,0,10);
        test.setOnClickListener(v->beginHardwareSelfTest());

        margin(text(getString(R.string.engine_logs),16,WHITE,true),content,0,6);
        logText=text(getString(R.string.logs_loading),11,MUTED,false);
        logText.setTextIsSelectable(true);
        logText.setPadding(dp(12),dp(10),dp(12),dp(10));
        logText.setBackground(bg(CARD,12));
        logText.setMaxLines(24);
        margin(logText,content,0,6);
        refreshDiagnosticViews();
    }

    private String networkDiagnostics(){
        try {
            android.net.ConnectivityManager manager=(android.net.ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.Network net=manager==null?null:manager.getActiveNetwork();
            android.net.NetworkCapabilities cap=net==null?null:manager.getNetworkCapabilities(net);
            if(cap==null)return getString(R.string.network_unavailable);
            String kind=cap.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    ?"Wi-Fi":cap.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                    ?getString(R.string.network_mobile):getString(R.string.network_other);
            boolean internet=cap.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            String ip=CoopSession.localIPv4();
            return getString(R.string.network_summary,kind,getString(internet?R.string.network_validated:R.string.network_unvalidated))
                    +(ip.isEmpty()?"":"\n"+getString(R.string.network_ip_address,ip));
        }catch(Exception ex){return getString(R.string.network_restricted);}
    }

    private void refreshDiagnosticViews(){
        if(deviceSummary==null||computeSummary==null||connectionSessionStatus==null)return;
        deviceSummary.setText(deviceProfile.description(this)+
            (lastHud==null?"":"\n\n"+getString(R.string.hud_live_detail,lastHud.detail(this))));
        computeSummary.setText(computeReport.description(this));
        if(suitabilitySummary!=null)suitabilitySummary.setText(suitabilityOverview());
        if(localConnectionStatus!=null)localConnectionStatus.setText(networkDiagnostics());
        CoopSession session=CoopSession.get(this);
        connectionSessionStatus.setText(session.status(this));
        if(nodeRows==null)return;
        nodeRows.removeAllViews();
        if("host".equals(session.role())&&!session.nodes().isEmpty()){
            for(CoopSession.Node node:session.nodes()){
                String info=CoopDiagnosticPresentation.host(this,node,false);
                TextView card=text(info,12,WHITE,false);
                card.setPadding(dp(14),dp(12),dp(14),dp(12));
                card.setBackground(bg(CARD,14));margin(card,nodeRows,0,8);
            }
        }else if("guest".equals(session.role())){
            String summary=session.peerDiagnostics(this);
            TextView text=text(summary,12,WHITE,false);
            text.setPadding(dp(14),dp(12),dp(14),dp(12));
            text.setBackground(bg(CARD,14));margin(text,nodeRows,0,7);
        }else{
            TextView none=text(getString(R.string.session_no_nodes),
                    13,MUTED,false);
            none.setPadding(dp(14),dp(14),dp(14),dp(14));
            none.setBackground(bg(CARD,14));margin(none,nodeRows,0,5);
        }
    }

    private void beginHardwareSelfTest() {
        if (diagnosticBusy || chatBusy || pullBusy || benchmarkBusy) {
            toast(getString(R.string.busy)); return;
        }
        if (currentModel == null || currentModel.isEmpty() || knownModels.isEmpty()) {
            toast(getString(R.string.test_need_model)); showTab(1); return;
        }
        if (!OllamaForegroundService.isRunning()) {
            toast(getString(R.string.test_need_server)); return;
        }
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.verify_inference))
            .setMessage(getString(R.string.hardware_confirm,currentModel))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.test_start), (d,which) -> {
                diagnosticBusy = true;
                diagnosticStatus.setText(getString(R.string.test_running));
                final String model = currentModel;
                io.execute(() -> {
                    String computed;
                    try {
                        computed = HardwareSelfTest.run(getApplicationContext(), model,
                            message -> ui.post(() -> {
                                if (!isFinishing() && diagnosticStatus != null)
                                    diagnosticStatus.setText(message);
                            }));
                    } catch (Throwable e) {
                        computed = getString(R.string.bench_failed,e.getClass().getSimpleName()+" · "+e.getMessage());
                    }
                    final String result = computed;
                    ui.post(() -> {
                        diagnosticBusy = false;
                        if (isFinishing()) return;
                        diagnosticStatus.setText(getString(R.string.test_finished));
                        new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.test_report))
                            .setMessage(result)
                            .setNegativeButton(getString(R.string.close), null)
                            .setPositiveButton(getString(R.string.copy_result), (dialog, action) -> {
                                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.test_report), result));
                                toast(getString(R.string.diag_copied));
                            }).show();
                    });
                });
            }).show();
    }

    private String computePreference() {
        return getPreferences(MODE_PRIVATE).getString("compute_preference", "auto");
    }

    private void showComputeChoiceDialog() {
        computeReport = ComputeProfiler.scan(this);
        String gpuInfo = !GpuRunner.packaged(this) ? getString(R.string.compute_gpu_missing) :
            (computeReport.gpuCompute ? getString(R.string.compute_gpu_try) : getString(R.string.compute_gpu_unavailable));
        String npuInfo = computeReport.nnapiAccelerators > 0 ?
            getString(R.string.compute_npu_candidate) :
            getString(R.string.compute_npu_unsupported);
        String[] options = {getString(R.string.compute_auto), getString(R.string.compute_cpu), gpuInfo, npuInfo};
        new AlertDialog.Builder(this).setTitle(getString(R.string.compute_select))
            .setMessage(R.string.compute_notice)
            .setSingleChoiceItems(options,
                "cpu".equals(computePreference()) ? 1 : "gpu".equals(computePreference()) ? 2 : 0,
                (dialog, which) -> {
                    if (chatBusy) { toast(getString(R.string.model_wait)); return; }
                    if (which == 3) {
                        toast(getString(R.string.compute_npu_error)); return;
                    }
                    if (which == 2 && (!computeReport.gpuCompute || !GpuRunner.packaged(this))) {
                        toast(getString(R.string.compute_gpu_error)); return;
                    }
                    String pref = which == 1 ? "cpu" : which == 2 ? "gpu" : "auto";
                    getPreferences(MODE_PRIVATE).edit().putString("compute_preference", pref).apply();
                    if (which == 1) GpuRunner.stop();
                    if (activeEngineLabel != null)
                        activeEngineLabel.setText(getString(R.string.compute_selected,pref));
                    toast(getString(R.string.compute_saved));
                    dialog.dismiss();
                }).setNegativeButton(getString(R.string.cancel), null).show();
    }

    private boolean animationsEnabled() {
        return smoothMotion && ValueAnimator.areAnimatorsEnabled();
    }

    private void showTab(int index) {
        int target=Math.max(0,Math.min(3,index));
        if(target!=0&&cloudMode)returnToLocal();
        LinearLayout[] screens={chatPage,modelPage,serverPage,diagnosticPage};
        boolean alreadyShowing=screens[target].getVisibility()==View.VISIBLE;
        activeTab=target;
        for(int i=0;i<screens.length;i++){
            View page=screens[i];
            page.animate().cancel();
            if(i!=target){
                page.setVisibility(View.GONE);
                page.setAlpha(1f);
                page.setTranslationX(0f);
            }
        }
        View incoming=screens[target];
        incoming.setVisibility(View.VISIBLE);
        if(!alreadyShowing && animationsEnabled()){
            incoming.setAlpha(0f);
            incoming.setTranslationX(dp(14));
            incoming.animate().alpha(1f).translationX(0f)
                .setDuration(190)
                .setInterpolator(new DecelerateInterpolator())
                .withLayer().start();
        }else{
            incoming.setAlpha(1f);
            incoming.setTranslationX(0f);
        }
        if(activeTab!=0&&voice!=null)voice.cancel();
        if(topPageTitle!=null)topPageTitle.setText("OnHand AI");
        if(topPageSubtitle!=null){
            int[] subtitles={R.string.chat,R.string.models,R.string.settings,R.string.devices};
            topPageSubtitle.setText(getString(subtitles[activeTab])+"  ·  "+getString(cloudMode?R.string.cloud_short:R.string.local));
        }
        if(activeTab==1)loadRecommendedVariants(false);
        if(activeTab==3)refreshDiagnosticViews();
        refreshTabletRail();
    }

    private void showFirstRunGuide() {
        LinearLayout guide = column(); guide.setPadding(dp(20), dp(12), dp(20), dp(5));
        margin(text(getString(R.string.onboarding_title), 20, WHITE, true), guide, 0, 8);
        margin(text(getString(R.string.onboarding_body),13,WHITE,false),guide,0,11);
        // Preserve the original first-run banner placement and ad unit; keep it separate from dialog controls.
        margin(text(getString(BuildConfig.DEBUG?R.string.ad_test:R.string.ad_label),11,MUTED,false),guide,0,4);
        FrameLayout guideAd = new FrameLayout(this);
        guideAd.setMinimumHeight(dp(56));
        margin(guideAd,guide,0,12);
        ads.register(guideAd,AdSupport.PLACEMENT_ONBOARDING);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(guide)
            .setNegativeButton(R.string.chat, (d,w) -> showTab(0))
            .setNeutralButton(R.string.guest, (d,w) -> enterGuestMode())
            .setPositiveButton(R.string.install_model, (d,w) -> showTab(1)).create();
        dialog.setOnDismissListener(d -> {
            getPreferences(MODE_PRIVATE).edit().putBoolean("welcome_v2_seen", true).apply();
            ads.detach(guideAd);
        });
        dialog.show();
        ads.showBanner(guideAd);
        ads.prepareConsent();
    }

    private void startServer() {
        Intent intent = new Intent(this, OllamaForegroundService.class).setAction(OllamaForegroundService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            toast(getString(R.string.local_start_requested));
        } catch (Exception e) { toast(getString(R.string.server_start_failed,e.getMessage())); }
    }
    private void stopServer() {
        if (chatBusy) stopChat();
        GpuRunner.stop();
        if (pullBusy) { pullCancelled = true; if (pullConnection != null) pullConnection.disconnect(); }
        Intent intent = new Intent(this, OllamaForegroundService.class).setAction(OllamaForegroundService.ACTION_STOP);
        startService(intent);
    }
    private String installedSize(long bytes) {
        if(bytes<=0)return getString(R.string.storage_unknown);
        return String.format(getResources().getConfiguration().getLocales().get(0),"%.2f GB",bytes/1_000_000_000.0);
    }
    // UI mapping only; original suitability values remain stable for admission rules.
    private String suitabilityLabel(String value){
        if(value==null)return getString(R.string.unknown);
        switch(value){
            case "미지원":return getString(R.string.suitability_unsupported);
            case "원활":return getString(R.string.suitability_smooth);
            case "보통":return getString(R.string.suitability_moderate);
            case "느림":return getString(R.string.suitability_slow);
            case "메모리 주의":return getString(R.string.suitability_memory);
            case "권장하지 않음":return getString(R.string.suitability_not_recommended);
            case "주의":return getString(R.string.suitability_caution);
            case "실행 여유 예상":return getString(R.string.suitability_expected);
            default:return value;
        }
    }
    private String localizedLayerLabel(String status,long completed,long total){
        String raw=status==null?"":status;
        String label=raw;
        if(raw.isEmpty()||"진행 중".equals(raw))label=getString(R.string.pull_progress);
        else if("pulling manifest".equals(raw))label=getString(R.string.pull_manifest);
        else if(raw.startsWith("pulling "))label=getString(R.string.pull_data)+" · "+raw.substring(8);
        else if("verifying sha256 digest".equals(raw))label=getString(R.string.pull_verify);
        else if("writing manifest".equals(raw))label=getString(R.string.pull_write);
        else if("success".equals(raw))label=getString(R.string.pull_success);
        int percent=ModelDownloadBudget.layerPercent(completed,total);
        if(percent<0)return label;
        if("ko".equals(getResources().getConfiguration().getLocales().get(0).getLanguage()))
            return ModelDownloadBudget.layerLabel(label,completed,total);
        return getString(R.string.download_layer_percent,label,percent);
    }

    private void refreshModels() {
        io.execute(()->{
            try {
                JSONObject result=OllamaHttp.get("/api/tags");
                JSONArray list=result.optJSONArray("models");
                List<String> names=new ArrayList<>();
                Map<String,String> descriptions=new HashMap<>();
                Map<String,String> quantizations=new HashMap<>();
                Map<String,Long> actualSizes=new HashMap<>();
                if(list!=null)for(int i=0;i<list.length();i++){
                    JSONObject item=list.optJSONObject(i);
                    if(item==null)continue;
                    String name=item.optString("name","");
                    if(name.isEmpty())continue;
                    names.add(name);
                    JSONObject detail=item.optJSONObject("details");
                    String p=detail==null?"":detail.optString("parameter_size","");
                    String q=detail==null?"":detail.optString("quantization_level","");
                    String family=detail==null?"":detail.optString("family","");
                    quantizations.put(name, normalizeQuantization(q));
                    StringBuilder meta=new StringBuilder(installedSize(item.optLong("size",0)));
                    if(!p.isEmpty())meta.append("  ·  ").append(p);
                    if(!q.isEmpty())meta.append("  ·  ").append(q);
                    if(!family.isEmpty())meta.append("\n계열: ").append(family);
                    descriptions.put(name,meta.toString());
                    actualSizes.put(name,item.optLong("size",0));
                }
                java.util.Collections.sort(names,String.CASE_INSENSITIVE_ORDER);
                ui.post(()->updateModels(names,descriptions,quantizations,actualSizes));
            }catch(Exception error){
                ui.post(()->{
                    if(modelRows==null)return;
                    renderInstalledModels(); // Online catalog remains usable while the local server starts.
                });
            }
        });
    }

    private void updateModels(List<String> names,Map<String,String> descriptions,
                              Map<String,String> quantizations,Map<String,Long> sizes) {
        ignoreSelection=true;
        knownModels.clear();knownModels.addAll(names);
        modelMetadata.clear();modelMetadata.putAll(descriptions);
        modelQuantization.clear();modelQuantization.putAll(quantizations);
        modelBytes.clear();modelBytes.putAll(sizes);
        List<String> shown=names.isEmpty()
            ?java.util.Collections.singletonList(getString(R.string.model_none_installed)):names;
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item,shown){
            @Override public View getView(int position,View convertView,ViewGroup parent){
                TextView item=text(getItem(position),14,WHITE,true);
                item.setPadding(dp(12),dp(7),dp(8),dp(7));
                return item;
            }
            @Override public View getDropDownView(int position,View convertView,ViewGroup parent){
                TextView item=text(getItem(position),14,WHITE,false);
                item.setPadding(dp(14),dp(12),dp(10),dp(12));
                item.setBackground(bg(CARD,0));return item;
            }
        };
        modelChooser.setAdapter(adapter);
        int idx=names.indexOf(currentModel);
        if(!names.isEmpty()&&idx<0&&!chatBusy&&!attachmentReading){
            saveLocalDraft();
            currentModel=names.get(0);
            getPreferences(MODE_PRIVATE).edit().putString("model",currentModel).apply();
            loadHistory();renderHistory();idx=0;
        }
        if(idx>=0)modelChooser.setSelection(idx);
        modelChooser.setEnabled(!names.isEmpty()&&!chatBusy);
        if(installedPickerButton!=null){
            installedPickerButton.setText(getString(R.string.installed_count,names.size()));
            installedPickerButton.setEnabled(!names.isEmpty());
            installedPickerButton.setAlpha(names.isEmpty()?0.55f:1f);
        }
        ignoreSelection=false;
        populateModelFilters();
        animateNextTileUpdate=true;
        renderInstalledModels();
        if(activeTab==3)refreshDiagnosticViews();
    }

    private static String normalizeQuantization(String raw) {
        return ModelListFilter.normalizeQuantization(raw);
    }
    private static String modelFamily(String name,String meta) {
        return ModelListFilter.family(name,meta);
    }
    private static String quantizationBand(String quant) {
        return ModelListFilter.band(quant);
    }

    // Only the display label is translated; model search/filter identifiers remain stable.
    private String filterDisplay(String value){
        if(value==null)return "";
        switch(value){
            case "전체에서 찾기":return getString(R.string.source_all);
            case "Ollama에서 찾기":return getString(R.string.source_ollama);
            case "Hugging Face에서 찾기":return getString(R.string.source_hf);
            case "추천 순":return getString(R.string.sort_recommend);
            case "용량 작은 순":return getString(R.string.sort_small);
            case "용량 큰 순":return getString(R.string.sort_large);
            case "이름 순":return getString(R.string.sort_name);
            case "전체 모델":return getString(R.string.family_all);
            case "전체 양자화":return getString(R.string.quant_all);
            case "기타":return getString(R.string.other);
            case "미확인":return getString(R.string.unknown);
            default:return value.endsWith(" 전체")?getString(R.string.quant_band,value.substring(0,value.length()-3)):value;
        }
    }
    private ArrayAdapter<String> makeFilterAdapter(List<String> options) {
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item,options){
            @Override public View getView(int pos,View recycled,ViewGroup parent){
                TextView view=text(filterDisplay(getItem(pos)),13,WHITE,true);
                view.setPadding(dp(11),dp(8),dp(3),dp(8));
                return view;
            }
            @Override public View getDropDownView(int pos,View recycled,ViewGroup parent){
                TextView view=text(filterDisplay(getItem(pos)),13,WHITE,false);
                view.setPadding(dp(14),dp(12),dp(10),dp(12));
                view.setBackground(bg(CARD,0));
                return view;
            }
        };
        return adapter;
    }

    /** Max six locally admitted candidate tags, no unrelated installed or featured cards. */
    private List<ModelTileCatalog.Tile> baseRecommendedTiles() {
        List<String> ids=new ArrayList<>();
        Map<String,String> descriptions=new HashMap<>();
        Map<String,Long> expectedSizes=new HashMap<>();
        for(DeviceProfiler.Recommendation rec:DeviceProfiler.recommend(deviceProfile)) {
            if(!rec.selectable)continue;
            double gib=rec.downloadGb*1_000_000_000.0/(1024.0*1024.0*1024.0);
            ModelPerformanceAdvisor.Result estimate=advise(rec.model,gib);
            if("미지원".equals(estimate.suitability)
                    ||"권장하지 않음".equals(estimate.suitability))continue;
            if(ids.contains(rec.model))continue;
            ids.add(rec.model);
            descriptions.put(rec.model,rec.description+" · "+estimate.suitability);
            expectedSizes.put(rec.model,Math.max(0,(long)(rec.downloadGb*1_000_000_000L)));
        }
        return ModelTileCatalog.forDevice(ids,descriptions,expectedSizes,
            knownModels,modelBytes,modelQuantization,6);
    }

    private double variantScore(ModelVariantCatalog.Variant v){
        return modelScore(v.id(),v.bytes,v.quant,v.hf(),knownModels.contains(v.model));
    }
    private double modelScore(String id,long bytes,String quant,boolean hf,boolean installed){
        double score=ModelVariantCatalog.score(bytes,quant,hf,installed,deviceProfile.arm64,
            deviceProfile.lowMemory,deviceProfile.ramGb,deviceProfile.availableRamGb,
            deviceProfile.freeStorageGb,deviceProfile.cores);
        if(!hf){
            String report=getSharedPreferences("ai_pocket_bench",MODE_PRIVATE)
                .getString("model_"+id,"");
            score+=BenchmarkRecommendation.adjustment(
                ModelPerformanceAdvisor.measuredCpuTps(report),
                ModelPerformanceAdvisor.measuredGpuTps(report));
        }
        return score;
    }
    private boolean recommendedFit(ModelVariantCatalog.Variant v){
        return v.bytes>0&&!"미확인".equals(v.quant)&&
            ModelVariantCatalog.tier(v.bytes,v.hf(),knownModels.contains(v.model),
                deviceProfile.arm64,deviceProfile.lowMemory,
                deviceProfile.ramGb,deviceProfile.availableRamGb,deviceProfile.freeStorageGb)==0;
    }
    private Map<String,ModelVariantCatalog.Variant> bestQuantizations(List<ModelVariantCatalog.Variant> list){
        Map<String,ModelVariantCatalog.Variant> best=new HashMap<>();
        for(ModelVariantCatalog.Variant v:list){
            if(!recommendedFit(v))continue;
            ModelVariantCatalog.Variant old=best.get(v.group());
            if(old==null||variantScore(v)<variantScore(old))best.put(v.group(),v);
        }
        return best;
    }
    private void loadRecommendedVariants(boolean force){
        if(recommendationLoading||(!force&&recommendationLoaded))return;
        recommendationLoading=true;recommendationError="";
        java.util.Set<String> families=new java.util.LinkedHashSet<>();
        for(ModelTileCatalog.Tile t:baseRecommendedTiles())families.add(t.id.split(":",2)[0]);
        updateModelSearchStatus();
        io.execute(()->{
            List<ModelVariantCatalog.Variant> results=Collections.emptyList();String error="";
            try{results=ModelVariantCatalog.fetchFamilies(families);}
            catch(Exception ex){error=UiText.error(this,ex.getMessage()==null?"온라인 확인 실패":ex.getMessage());}
            final List<ModelVariantCatalog.Variant> ready=results;final String problem=error;
            ui.post(()->{
                if(isDestroyed()||isFinishing())return;
                recommendationLoading=false;recommendationLoaded=true;recommendationError=problem;
                if(!ready.isEmpty()||problem.isEmpty()){recommendedVariants.clear();recommendedVariants.addAll(ready);}
                if(modelFilter.getText().toString().trim().isEmpty()){populateModelFilters();updateModelSearchStatus();renderInstalledModels();}
            });
        });
    }
    private List<ModelTileCatalog.Tile> deviceRecommendedTiles(){
        List<ModelTileCatalog.Tile> result=new ArrayList<>();
        visibleVariants.clear();
        for(ModelTileCatalog.Tile base:baseRecommendedTiles()){
            ModelVariantCatalog.Variant best=null;
            for(ModelVariantCatalog.Variant v:recommendedVariants){
                if(!OllamaQuantCatalog.belongsToModel(base.id,v.model)||!recommendedFit(v))continue;
                if(best==null||variantScore(v)<variantScore(best))best=v;
            }
            if(best==null){
                // Local installed quantization is verified by /api/tags, not by a fabricated tag.
                for(String installed:knownModels){
                    if(!OllamaQuantCatalog.belongsToModel(base.id,installed))continue;
                    String quant=modelQuantization.getOrDefault(installed,"미확인");
                    long bytes=modelBytes.getOrDefault(installed,0L);
                    if(!"미확인".equals(quant)&&bytes>0&&modelScore(installed,bytes,quant,false,true)<1000){
                        result.add(new ModelTileCatalog.Tile(installed,base.title,base.description,quant,bytes,true,true));
                        best=null;break;
                    }
                }
                if(!result.isEmpty()&&OllamaQuantCatalog.belongsToModel(base.id,result.get(result.size()-1).id))continue;
                // A candidate is not falsely labelled an exact recommended quantization offline.
                result.add(new ModelTileCatalog.Tile(base.id,base.title,"양자화 확인 필요","미확인",base.bytes,knownModels.contains(base.id),false));
                continue;
            }
            visibleVariants.put(best.id(),best);
            result.add(new ModelTileCatalog.Tile(best.id(),base.title,base.description,best.quant,best.bytes,knownModels.contains(best.model),true));
        }
        return result;
    }

    private void populateModelFilters(){
        if(familyFilter==null||quantizationFilter==null)return;
        List<String> families=new ArrayList<>();
        families.add("전체 모델");
        List<String> foundFamilies=new ArrayList<>();
        List<String> quantized=new ArrayList<>();
        String query=modelFilter==null?"":modelFilter.getText().toString().trim();
        List<ModelTileCatalog.Tile> filterTiles=query.isEmpty()?deviceRecommendedTiles():allSearchTiles(query);
        for(ModelTileCatalog.Tile entry:filterTiles){
            String name=entry.id;
            String family=modelFamily(name,modelMetadata.getOrDefault(name,entry.description));
            if(!foundFamilies.contains(family))foundFamilies.add(family);
            String q=modelQuantization.getOrDefault(name,entry.quantization);
            if(!quantized.contains(q))quantized.add(q);
        }
        String[] priorities={"Qwen","Llama","Gemma","DeepSeek","Mistral","Phi","GLM","GPT-OSS","기타"};
        for(String family:priorities)if(foundFamilies.contains(family))families.add(family);
        List<String> quantOptions=new ArrayList<>();
        quantOptions.add("전체 양자화");
        for(String band:new String[]{"Q2","Q3","Q4","Q5","Q6","Q8","F16/BF16","기타","미확인"}){
            boolean exists=false;
            for(String q:quantized)if(band.equals(quantizationBand(q)))exists=true;
            if(exists)quantOptions.add(band+" 전체");
        }
        java.util.Collections.sort(quantized);
        for(String q:quantized)if(!"미확인".equals(q))quantOptions.add(q);
        int familyIndex=families.indexOf(selectedModelFamily);
        int quantIndex=quantOptions.indexOf(selectedQuantization);
        if(familyIndex<0){selectedModelFamily="전체 모델";familyIndex=0;}
        if(quantIndex<0){selectedQuantization="전체 양자화";quantIndex=0;}
        ignoreSelection=true;
        familyFilter.setAdapter(makeFilterAdapter(families));
        quantizationFilter.setAdapter(makeFilterAdapter(quantOptions));
        familyFilter.setSelection(familyIndex);
        quantizationFilter.setSelection(quantIndex);
        ignoreSelection=false;
        familyFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(AdapterView<?> parent){}
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                if(ignoreSelection)return;
                String next=String.valueOf(parent.getItemAtPosition(position));
                if(!next.equals(selectedModelFamily)){selectedModelFamily=next;renderInstalledModels();}
            }
        });
        quantizationFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(AdapterView<?> parent){}
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                if(ignoreSelection)return;
                String next=String.valueOf(parent.getItemAtPosition(position));
                if(!next.equals(selectedQuantization)){selectedQuantization=next;renderInstalledModels();}
            }
        });
    }

    private boolean matchesQuantization(String quant) {
        return ModelListFilter.matchesQuantization(selectedQuantization,quant);
    }

    private ModelPerformanceAdvisor.Result advise(String model,double sizeGiB) {
        String ownReport=getSharedPreferences("ai_pocket_bench",MODE_PRIVATE)
            .getString("model_"+model,"");
        Double ownTps=ModelPerformanceAdvisor.measuredCpuTps(ownReport);
        Double baselineTps=null;
        double baselineSize=0;
        if(ownTps==null){
            for(String reference:knownModels){
                if(reference.equals(model))continue;
                long bytes=modelBytes.getOrDefault(reference,0L);
                if(bytes<=0)continue;
                String report=getSharedPreferences("ai_pocket_bench",MODE_PRIVATE)
                    .getString("model_"+reference,"");
                Double tps=ModelPerformanceAdvisor.measuredCpuTps(report);
                if(tps!=null){
                    baselineTps=tps;
                    baselineSize=bytes/(1024.0*1024.0*1024.0);
                    break;
                }
            }
        }
        return ModelPerformanceAdvisor.evaluate(
            deviceProfile.arm64,deviceProfile.lowMemory,
            deviceProfile.ramGb,deviceProfile.availableRamGb,
            sizeGiB,ownTps,baselineTps,baselineSize);
    }

    private String localeDecimal(double value){
        return Double.isFinite(value)?String.format(getResources().getConfiguration().getLocales().get(0),"%.1f",value):getString(R.string.metric_unavailable);
    }
    private String advisorSpeed(ModelPerformanceAdvisor.Result r){
        switch(r.reason){
            case UNSUPPORTED:return getString(R.string.advisor_no_speed);
            case MEASURED:return getString(R.string.advisor_measured,localeDecimal(r.measuredTps));
            case RANGE:return getString(R.string.advisor_range,localeDecimal(r.lowTps),localeDecimal(r.highTps));
            default:return getString(R.string.advisor_unmeasured);
        }
    }
    private String advisorSummary(ModelPerformanceAdvisor.Result r){
        String header=suitabilityLabel(r.suitability)+" · "+advisorSpeed(r);
        if(r.reason==ModelPerformanceAdvisor.Result.Reason.UNSUPPORTED)return header+"\n"+getString(R.string.advisor_arm);
        String memory=r.weightGiB>0?getString(R.string.advisor_memory_estimate,localeDecimal(r.weightGiB),localeDecimal(r.requiredGiB)):getString(R.string.advisor_memory_unknown);
        int reason=r.reason==ModelPerformanceAdvisor.Result.Reason.MEASURED?R.string.advisor_reason_measured:
            r.reason==ModelPerformanceAdvisor.Result.Reason.MEMORY?R.string.advisor_reason_memory:
            r.reason==ModelPerformanceAdvisor.Result.Reason.RANGE?R.string.advisor_reason_range:R.string.advisor_reason_unmeasured;
        return header+"\n"+memory+"\n"+getString(reason);
    }
    private String suitabilityOverview(){
        StringBuilder display=new StringBuilder(getString(R.string.advisor_device,deviceProfile.device,localeDecimal(deviceProfile.ramGb),deviceProfile.cores)).append('\n');
        if(!currentModel.isEmpty()&&modelBytes.containsKey(currentModel)){
            long weight=modelBytes.getOrDefault(currentModel,0L);
            ModelPerformanceAdvisor.Result current=advise(currentModel,weight/(1024.0*1024.0*1024.0));
            display.append('\n').append(getString(R.string.advisor_current,currentModel)).append('\n').append(advisorSummary(current)).append('\n');
        }else display.append('\n').append(getString(R.string.advisor_choose)).append('\n');
        display.append('\n').append(getString(R.string.advisor_recommendations));
        int shown=0;
        for(DeviceProfiler.Recommendation recommended:DeviceProfiler.recommend(deviceProfile)){
            if(!recommended.selectable)continue;
            ModelPerformanceAdvisor.Result score=advise(recommended.model,recommended.downloadGb*1_000_000_000/(1024.0*1024.0*1024.0));
            display.append("\n • ").append(recommended.model).append(" · ").append(suitabilityLabel(score.suitability)).append(" · ").append(advisorSpeed(score));
            if(++shown>=3)break;
        }
        if(shown==0)display.append('\n').append(getString(R.string.advisor_no_recommendations));
        return display.append("\n\n").append(getString(R.string.advisor_limit)).toString();
    }

    private void setModelViewMode(boolean listMode){
        if(modelListView==listMode)return;
        modelListView=listMode;
        getPreferences(MODE_PRIVATE).edit().putBoolean("model_list_view",listMode).apply();
        updateModelViewButtons();
        renderInstalledModels();
    }

    private void updateModelViewButtons(){
        if(modelListViewButton==null||modelGridViewButton==null)return;
        Button[] buttons={modelListViewButton,modelGridViewButton};
        boolean[] active={modelListView,!modelListView};
        for(int i=0;i<buttons.length;i++){
            buttons[i].setBackground(bg(active[i]?ACCENT:FIELD,10));
            buttons[i].setTextColor(active[i]?BG:WHITE);
            buttons[i].setSelected(active[i]);
        }
        modelListViewButton.setContentDescription(getString(R.string.list_view)+(modelListView?" · "+getString(R.string.workspace_selected):""));
        modelGridViewButton.setContentDescription(getString(R.string.grid_view)+(!modelListView?" · "+getString(R.string.workspace_selected):""));
    }

    private int tileBackgroundColor(boolean selected){
        return selected?FIELD:CARD;
    }
    private int tileTextColor(){return WHITE;}
    private int tileMutedColor(){return MUTED;}
    private GradientDrawable tileBackground(boolean selected){
        GradientDrawable shape=bg(tileBackgroundColor(selected),13);
        // Selected rows are distinguished by surface tone, not thick borders.
        shape.setStroke(dp(1),selected?
            (lightTheme()?Color.rgb(208,210,215):Color.rgb(79,82,89)):borderTone());
        return shape;
    }

    private void tilePressEffect(View view){
        if(!animationsEnabled())return;
        view.setOnTouchListener((v,event)->{
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                v.animate().cancel();
                v.animate().scaleX(0.985f).scaleY(0.985f)
                    .setDuration(95).setInterpolator(new DecelerateInterpolator()).start();
            }else if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_UP){
                v.animate().cancel();
                v.animate().scaleX(1f).scaleY(1f)
                    .setDuration(155).setInterpolator(new DecelerateInterpolator()).start();
            }
            return false;
        });
    }

    private void activateTile(ModelTileCatalog.Tile tile){
        if(tile.id.equals(downloadingCardId)&&pullBusy){toast(getString(R.string.model_downloading));return;}
        if(isUiBusy()){toast(getString(R.string.busy));return;}
        ModelVariantCatalog.Variant selected=visibleVariants.get(tile.id);
        if(selected!=null){
            List<ModelVariantCatalog.Variant> options=new ArrayList<>();
            List<ModelVariantCatalog.Variant> source=modelFilter.getText().toString().trim().isEmpty()?recommendedVariants:onlineVariants;
            for(ModelVariantCatalog.Variant v:source)
                if(v.source.equals(selected.source)&&v.family().equalsIgnoreCase(selected.family()))options.add(v);
            if(options.isEmpty())options.add(selected);
            Map<String,ModelVariantCatalog.Variant> best=bestQuantizations(options);
            options.sort(Comparator.comparingDouble(this::variantScore).thenComparing(v->v.label(),String.CASE_INSENSITIVE_ORDER));
            String[] labels=new String[options.size()];
            for(int i=0;i<labels.length;i++){
                ModelVariantCatalog.Variant v=options.get(i);
                labels[i]=v.label()+"\n"+filterDisplay(v.quant)+" · "+(v.bytes>0?installedSize(v.bytes):getString(R.string.storage_unknown))+
                    (best.get(v.group())==v?" "+getString(R.string.model_recommend_mark):"")+(knownModels.contains(v.model)?" · "+getString(R.string.model_installed):"");
            }
            new AlertDialog.Builder(this).setTitle(getString(R.string.model_quant_title))
                .setItems(labels,(d,i)->{
                    if(isUiBusy())return;
                    ModelVariantCatalog.Variant choice=options.get(i);
                    if(choice.hf())HuggingFaceCatalog.openVariant(this,choice,this::refreshModels);
                    else if(knownModels.contains(choice.model)){selectModel(choice.model);showTab(0);}
                    else{modelInput.setText(choice.model);pullModel();}
                }).setNegativeButton(getString(R.string.close),null).show();
            return;
        }
        if(tile.installed){selectModel(tile.id);showTab(0);return;}
        OllamaQuantPicker.show(this,tile.id,new ArrayList<>(knownModels),(tag,installed)->{
            if(isUiBusy())return;
            if(installed&&knownModels.contains(tag)){selectModel(tag);showTab(0);}
            else{modelInput.setText(tag);pullModel();}
        });
    }

    private String benchmarkLabel(String tag){
        String report=getSharedPreferences("ai_pocket_bench",MODE_PRIVATE).getString("model_"+tag,"");
        Double cpu=ModelPerformanceAdvisor.measuredCpuTps(report);
        Double gpu=ModelPerformanceAdvisor.measuredGpuTps(report);
        double speed=BenchmarkRecommendation.bestMeasured(cpu,gpu);
        if(speed<=0)return "";
        return " · "+getString(R.string.model_measured_badge,localeDecimal(speed),gpu!=null&&(cpu==null||gpu>cpu)?"GPU":"CPU");
    }

    /** Compact contents fit inside an actual square at narrow phone widths. */
    private LinearLayout buildModelTile(ModelTileCatalog.Tile item,int sideDp) {
        boolean selected=item.installed&&OllamaQuantCatalog.belongsToModel(item.id,currentModel);
        int ink=tileTextColor(), secondary=tileMutedColor();
        LinearLayout card=column();
        card.setPadding(dp(10),dp(9),dp(10),dp(9));
        card.setBackground(tileBackground(selected));
        card.setClickable(true);
        card.setFocusable(true);
        card.setElevation(0);
        card.setContentDescription(getString(R.string.model_card_access,item.title,item.id,
            getString(item.id.equals(downloadingCardId)?R.string.model_downloading:item.installed?R.string.model_installed:R.string.model_not_installed),
            filterDisplay(item.quantization))+" · "+UiText.modelDescription(this,item.description));
        TypedValue ripple=new TypedValue();
        if(getTheme().resolveAttribute(android.R.attr.selectableItemBackground,ripple,true)
                &&ripple.resourceId!=0)
            card.setForeground(getDrawable(ripple.resourceId));
        tilePressEffect(card);

        TextView statusText=text(
            (item.id.equals(downloadingCardId)&&pullBusy)?getString(R.string.model_downloading):
            item.installed?(selected?getString(R.string.model_active):getString(R.string.model_installed)):getString(R.string.model_not_installed),
            10,ink,true);
        statusText.setSingleLine(true);
        statusText.setEllipsize(TextUtils.TruncateAt.END);
        statusText.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        LinearLayout statusRow=row();
        rowItem(statusRow,statusText,1,3);
        if(item.featured){
            TextView recommendation=text(getString(R.string.model_recommend_mark),10,secondary,true);
            recommendation.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
            recommendation.setSingleLine(true);
            rowItem(statusRow,recommendation,0,0);
        }
        margin(statusRow,card,0,2);
        tileStatusLabels.put(item.id,statusText);

        TextView name=text(item.title,sideDp<168?16:18,ink,true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(name,new LinearLayout.LayoutParams(-1,0,1));

        ModelVariantCatalog.Variant remote=visibleVariants.get(item.id);
        String exact=remote==null?item.id:remote.label();
        TextView variant=text(exact,10,secondary,false);
        variant.setGravity(Gravity.CENTER);
        variant.setSingleLine(true);
        variant.setEllipsize(TextUtils.TruncateAt.END);
        margin(variant,card,0,1);

        String suffix=item.bytes>0?installedSize(item.bytes):getString(R.string.storage_unknown);
        String provider=remote!=null?remote.source:item.installed?getString(R.string.own_device):"Ollama";
        TextView details=text(filterDisplay(item.quantization)+" · "+suffix+"\n"+
            provider+benchmarkLabel(item.id),10,secondary,false);
        details.setGravity(Gravity.CENTER);
        details.setMaxLines(2);
        details.setEllipsize(TextUtils.TruncateAt.END);
        margin(details,card,1,2);

        ProgressBar progress=new ProgressBar(this,null,
            android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(item.id.equals(downloadingCardId)&&pullBusy?
            View.VISIBLE:View.GONE);
        if(item.id.equals(downloadingCardId)&&pullBusy){
            progress.setIndeterminate(downloadingPercent<0);
            if(downloadingPercent>=0)progress.setProgress(downloadingPercent);
        }
        margin(progress,card,0,2);
        tileDownloadBars.put(item.id,progress);

        TextView hint=text(getString(R.string.model_quant_select),11,secondary,false);
        hint.setGravity(Gravity.CENTER);
        hint.setSingleLine(true);
        margin(hint,card,1,0);
        card.setOnClickListener(v->activateTile(item));
        return card;
    }

    /** Full-width accessible model row. Uses the same selection and progress state as square cards. */
    private LinearLayout buildModelListRow(ModelTileCatalog.Tile item){
        boolean selected=item.installed&&OllamaQuantCatalog.belongsToModel(item.id,currentModel);
        int ink=tileTextColor(),secondary=tileMutedColor();
        LinearLayout card=column();
        card.setPadding(dp(13),dp(11),dp(13),dp(11));
        card.setMinimumHeight(dp(90));
        card.setBackground(tileBackground(selected));
        card.setClickable(true);
        card.setFocusable(true);
        card.setElevation(0);
        card.setContentDescription(getString(R.string.model_card_access,item.title,item.id,
            getString(item.id.equals(downloadingCardId)?R.string.model_downloading:item.installed?R.string.model_installed:R.string.model_not_installed),
            filterDisplay(item.quantization)+(item.featured?" · "+getString(R.string.model_recommend_mark):""))+" · "+UiText.modelDescription(this,item.description));
        TypedValue ripple=new TypedValue();
        if(getTheme().resolveAttribute(android.R.attr.selectableItemBackground,ripple,true)
                &&ripple.resourceId!=0)card.setForeground(getDrawable(ripple.resourceId));
        tilePressEffect(card);

        LinearLayout line=row();
        LinearLayout body=column();
        TextView title=text(item.title,16,ink,true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        margin(title,body,0,3);
        ModelVariantCatalog.Variant remote=visibleVariants.get(item.id);
        String exact=remote==null?item.id:remote.label();
        TextView identifier=text(exact,12,secondary,false);
        identifier.setSingleLine(true);
        identifier.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        margin(identifier,body,0,4);
        String provider=remote!=null?remote.source:item.installed?getString(R.string.own_device):"Ollama";
        TextView details=text(filterDisplay(item.quantization)+"  ·  "+
            (item.bytes>0?installedSize(item.bytes):getString(R.string.storage_unknown))+"  ·  "+provider+
            benchmarkLabel(item.id),11,secondary,false);
        details.setSingleLine(true);
        details.setEllipsize(TextUtils.TruncateAt.END);
        margin(details,body,0,0);
        rowItem(line,body,1,8);

        LinearLayout trailing=column();
        trailing.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        TextView status=text(item.id.equals(downloadingCardId)&&pullBusy?getString(R.string.model_downloading):
            item.installed?(selected?getString(R.string.model_active):getString(R.string.model_installed)):getString(R.string.model_not_installed),
            10,ink,true);
        status.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        status.setSingleLine(true);
        margin(status,trailing,0,5);
        if(item.featured){
            TextView recommended=text(getString(R.string.model_recommend_mark),11,secondary,true);
            recommended.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            recommended.setSingleLine(true);
            margin(recommended,trailing,0,5);
        }
        TextView more=text(getString(R.string.model_select_more),12,secondary,false);
        more.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        more.setSingleLine(true);
        margin(more,trailing,0,0);
        rowItem(line,trailing,0,0);
        margin(line,card,0,0);
        tileStatusLabels.put(item.id,status);

        ProgressBar progress=new ProgressBar(this,null,
            android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        boolean ongoing=item.id.equals(downloadingCardId)&&pullBusy;
        progress.setVisibility(ongoing?View.VISIBLE:View.GONE);
        if(ongoing){
            progress.setIndeterminate(downloadingPercent<0);
            if(downloadingPercent>=0)progress.setProgress(downloadingPercent);
        }
        margin(progress,card,7,0);
        tileDownloadBars.put(item.id,progress);
        card.setOnClickListener(v->activateTile(item));
        return card;
    }

    private void updateTileDownloadProgress(){
        if(!pullBusy||downloadingModel.isEmpty())return;
        TextView status=tileStatusLabels.get(downloadingCardId);
        if(status!=null){
            status.setText(downloadingPercent<0?getString(R.string.model_download_preparing):
                getString(R.string.tile_layer_percent,downloadingPercent));
        }
        ProgressBar bar=tileDownloadBars.get(downloadingCardId);
        if(bar!=null){
            bar.setVisibility(View.VISIBLE);
            bar.setIndeterminate(downloadingPercent<0);
            if(downloadingPercent>=0)bar.setProgress(downloadingPercent);
        }
    }

    /** Search both providers concurrently; render each provider as soon as it resolves. */
    private void onModelSearchChanged(String nextValue){
        onModelSearchChanged(nextValue,false);
    }
    private void onModelSearchChanged(String nextValue,boolean forceRefresh){
        long sequence=++onlineSearchSerial;
        if(onlineSearchRunnable!=null)ui.removeCallbacks(onlineSearchRunnable);
        if(onlineSearchTask!=null)onlineSearchTask.cancel(true);
        if(onlineHfTask!=null)onlineHfTask.cancel(true);
        onlineSearchRunnable=null;
        onlineSearchError="";onlineSearchPending=false;
        String query=nextValue==null?"":nextValue.trim();
        String provider=selectedSource;
        searchParts.clear();searchFailures.clear();expandedGroups.clear();
        searchRequestsRemaining=0;
        selectedModelFamily="전체 모델";selectedQuantization="전체 양자화";
        if(query.isEmpty())loadRecommendedVariants(false);
        if(query.length()>=2&&query.length()<=120){
            long now=android.os.SystemClock.elapsedRealtime();
            if(!"Hugging Face에서 찾기".equals(provider)){
                List<ModelVariantCatalog.Variant> cached=searchCache.get(ModelSearchCache.key("Ollama",query),now);
                if(cached!=null)searchParts.put("Ollama",cached);
                if(forceRefresh||cached==null)searchRequestsRemaining++;
            }
            if(!"Ollama에서 찾기".equals(provider)){
                List<ModelVariantCatalog.Variant> cached=searchCache.get(ModelSearchCache.key("Hugging Face",query),now);
                if(cached!=null)searchParts.put("Hugging Face",cached);
                if(forceRefresh||cached==null)searchRequestsRemaining++;
            }
        }
        onlineSearchPending=searchRequestsRemaining>0;
        rebuildSearchParts();
        populateModelFilters();updateModelSearchStatus();renderInstalledModels();
        if(!onlineSearchPending)return;
        boolean needOllama=!"Hugging Face에서 찾기".equals(provider)&&
            (forceRefresh||!searchParts.containsKey("Ollama"));
        boolean needHf=!"Ollama에서 찾기".equals(provider)&&
            (forceRefresh||!searchParts.containsKey("Hugging Face"));
        onlineSearchRunnable=()->{
            if(isDestroyed()||sequence!=onlineSearchSerial)return;
            if(needOllama)onlineSearchTask=io.submit(()->fetchSearchSource(
                sequence,query,"Ollama"));
            if(needHf)onlineHfTask=io.submit(()->fetchSearchSource(
                sequence,query,"Hugging Face"));
        };
        ui.postDelayed(onlineSearchRunnable,450);
    }
    private void fetchSearchSource(long sequence,String query,String provider){
        List<ModelVariantCatalog.Variant> ready=Collections.emptyList();
        String error="";
        try{
            ready="Ollama".equals(provider)?
                ModelVariantCatalog.searchOllama(query):HuggingFaceCatalog.searchVariants(query);
            if(!Thread.currentThread().isInterrupted())
                searchCache.put(ModelSearchCache.key(provider,query),
                    ready,android.os.SystemClock.elapsedRealtime());
        }catch(Exception ex){
            error=provider+": "+UiText.error(this,ex.getMessage()==null?"연결 오류":ex.getMessage());
        }
        final List<ModelVariantCatalog.Variant> result=ready;final String failure=error;
        ui.post(()->{
            if(isFinishing()||isDestroyed()||sequence!=onlineSearchSerial)return;
            if(failure.isEmpty())searchParts.put(provider,result);
            else searchFailures.add(failure);
            searchRequestsRemaining=Math.max(0,searchRequestsRemaining-1);
            onlineSearchPending=searchRequestsRemaining>0;
            onlineSearchError=android.text.TextUtils.join(" / ",searchFailures);
            rebuildSearchParts();
            populateModelFilters();updateModelSearchStatus();
            animateNextTileUpdate=true;renderInstalledModels();
        });
    }
    private void rebuildSearchParts(){
        onlineVariants.clear();
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(List<ModelVariantCatalog.Variant> source:searchParts.values())
            for(ModelVariantCatalog.Variant v:source)
                if(ids.add(v.id().toLowerCase(Locale.ROOT)))onlineVariants.add(v);
    }
    private void updateModelSearchStatus(){
        if(modelSearchStatus==null)return;
        String query=modelFilter==null?"":modelFilter.getText().toString().trim();
        if(query.isEmpty()){
            modelSearchStatus.setText(recommendationLoading?getString(R.string.search_check_sizes):
                !recommendationError.isEmpty()?getString(R.string.search_recommend_fail):getString(R.string.search_estimate_notice));
        }else if(query.length()<2)modelSearchStatus.setText(getString(R.string.search_min_chars));
        else if(query.length()>120)modelSearchStatus.setText(getString(R.string.search_max_chars));
        else if(onlineSearchPending)modelSearchStatus.setText(getString(R.string.search_pending_count,filterDisplay(selectedSource),onlineVariants.size()));
        else modelSearchStatus.setText(getString(R.string.search_result_count,onlineVariants.size(),
            onlineSearchError.isEmpty()?getString(R.string.search_unknown_last):getString(R.string.search_error_retry,onlineSearchError)));
    }
    private List<ModelTileCatalog.Tile> allSearchTiles(String query){
        List<ModelTileCatalog.Tile> tiles=new ArrayList<>();
        visibleVariants.clear();
        java.util.Set<String> seen=new java.util.HashSet<>();
        Map<String,ModelVariantCatalog.Variant> best=bestQuantizations(onlineVariants);
        for(ModelVariantCatalog.Variant v:onlineVariants){
            if(!seen.add(v.id().toLowerCase(Locale.ROOT)))continue;
            visibleVariants.put(v.id(),v);
            tiles.add(new ModelTileCatalog.Tile(v.id(),v.hf()?v.model:ModelTileCatalog.familyName(v.model),
                v.source+" · "+v.label(),v.quant,v.bytes,!v.hf()&&knownModels.contains(v.model),best.get(v.group())==v));
        }
        for(String name:knownModels){
            String meta=modelMetadata.getOrDefault(name,"로컬 모델");
            String quant=modelQuantization.getOrDefault(name,"미확인");
            if(!ModelListFilter.matches(name,meta,quant,query,"전체 모델","전체 양자화"))continue;
            if(!seen.add(name.toLowerCase(Locale.ROOT)))continue;
            tiles.add(new ModelTileCatalog.Tile(name,ModelTileCatalog.familyName(name),"이 기기에 설치됨",
                quant,modelBytes.getOrDefault(name,0L),true,false));
        }
        return tiles;
    }
    private double tileScore(ModelTileCatalog.Tile item){
        ModelVariantCatalog.Variant v=visibleVariants.get(item.id);
        return modelScore(item.id,item.bytes,item.quantization,v!=null&&v.hf(),item.installed);
    }
    private void sortTiles(List<ModelTileCatalog.Tile> shown){
        Map<String,Double> scores=new HashMap<>();
        if("추천 순".equals(selectedSort))for(ModelTileCatalog.Tile item:shown)scores.put(item.id,tileScore(item));
        shown.sort((a,b)->{
            int c;
            if("용량 작은 순".equals(selectedSort))c=ModelVariantCatalog.compareSize(a.bytes,b.bytes,false);
            else if("용량 큰 순".equals(selectedSort))c=ModelVariantCatalog.compareSize(a.bytes,b.bytes,true);
            else if("이름 순".equals(selectedSort))c=a.title.compareToIgnoreCase(b.title);
            else c=Double.compare(scores.get(a.id),scores.get(b.id));
            return c!=0?c:a.id.compareToIgnoreCase(b.id);
        });
    }

    private String groupKey(ModelTileCatalog.Tile item){
        ModelVariantCatalog.Variant v=visibleVariants.get(item.id);
        return v!=null?ModelGroupings.key(v):ModelGroupings.localKey(item.id);
    }
    private void renderGroupedSearch(List<ModelTileCatalog.Tile> sorted){
        Map<String,List<ModelTileCatalog.Tile>> groups=new java.util.LinkedHashMap<>();
        for(ModelTileCatalog.Tile item:sorted)
            groups.computeIfAbsent(groupKey(item),key->new ArrayList<>()).add(item);
        for(Map.Entry<String,List<ModelTileCatalog.Tile>> section:groups.entrySet()){
            List<ModelTileCatalog.Tile> items=section.getValue();
            if(items.size()==1){
                LinearLayout single=buildModelListRow(items.get(0));
                margin(single,modelRows,0,8);
                continue;
            }
            String id=section.getKey();
            boolean opened=expandedGroups.contains(id);
            ModelTileCatalog.Tile best=items.get(0);
            for(ModelTileCatalog.Tile item:items)
                if(tileScore(item)<tileScore(best))best=item;
            LinearLayout heading=row();
            heading.setPadding(dp(12),dp(11),dp(12),dp(11));
            heading.setBackground(bg(CARD,14));
            heading.setElevation(0);
            heading.setClickable(true);heading.setFocusable(true);
            LinearLayout details=column();
            TextView label=text(best.title,15,WHITE,true);
            label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);
            margin(label,details,0,4);
            String source=section.getKey().startsWith("hf:")?"Hugging Face":"Ollama";
            String metadata=getString(R.string.model_group_count,items.size())+" · "+filterDisplay(best.quantization)+" · "+
                (best.bytes>0?installedSize(best.bytes):getString(R.string.storage_unknown))+" · "+source;
            if(items.stream().anyMatch(t->t.id.equals(downloadingCardId)&&pullBusy))
                metadata+=" · "+getString(R.string.model_downloading);
            TextView info=text(metadata,11,MUTED,false);
            info.setSingleLine(true);info.setEllipsize(TextUtils.TruncateAt.END);
            margin(info,details,0,0);
            rowItem(heading,details,1,6);
            TextView arrow=text(opened?"⌄":"›",22,MUTED,false);
            arrow.setGravity(Gravity.CENTER);
            rowItem(heading,arrow,0,0);
            heading.setContentDescription(getString(R.string.model_group_access,best.title,items.size(),getString(opened?R.string.collapse:R.string.expand)));
            heading.setOnClickListener(v->{
                if(!expandedGroups.add(id))expandedGroups.remove(id);
                renderInstalledModels();
            });
            tilePressEffect(heading);
            margin(heading,modelRows,0,opened?5:9);
            if(!opened)continue;
            if(modelListView){
                for(ModelTileCatalog.Tile item:items){
                    LinearLayout row=buildModelListRow(item);
                    LinearLayout.LayoutParams p=lp(-1,-2);
                    p.leftMargin=dp(10);p.bottomMargin=dp(6);
                    modelRows.addView(row,p);
                }
            }else{
                int space=modelRows.getWidth()-dp(10);
                if(space<=0)space=getResources().getDisplayMetrics().widthPixels-dp(46);
                int gap=dp(8),columns=ModelVariantCatalog.columns(space,dp(150),gap);
                int sidePx=Math.max(dp(110),(space-gap*(columns-1))/columns);
                int sideDp=Math.round(sidePx/getResources().getDisplayMetrics().density);
                for(int pos=0;pos<items.size();pos+=columns){
                    LinearLayout groupRow=row();
                    groupRow.setGravity(Gravity.START|Gravity.TOP);
                    for(int c=0;c<columns&&pos+c<items.size();c++){
                        LinearLayout card=buildModelTile(items.get(pos+c),sideDp);
                        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(sidePx,sidePx);
                        if(c>0)p.leftMargin=gap;
                        groupRow.addView(card,p);
                    }
                    LinearLayout.LayoutParams p=lp(-1,-2);
                    p.leftMargin=dp(10);p.bottomMargin=dp(6);
                    modelRows.addView(groupRow,p);
                }
            }
        }
        updateTileDownloadProgress();
    }

    private void renderInstalledModels() {
        if(modelRows==null)return;
        modelRows.removeAllViews();
        tileStatusLabels.clear();
        tileDownloadBars.clear();
        String filter=modelFilter==null?"":modelFilter.getText().toString().trim();
        boolean searching=!filter.isEmpty();
        List<ModelTileCatalog.Tile> all=searching?
            allSearchTiles(filter):deviceRecommendedTiles();
        List<ModelTileCatalog.Tile> shown=new ArrayList<>();
        for(ModelTileCatalog.Tile item:all){
            if(searching){
                if(ModelListFilter.matches(item.id,item.description+" "+item.title,item.quantization,"",
                    selectedModelFamily,selectedQuantization))shown.add(item);
            }else{
                String description=modelMetadata.getOrDefault(item.id,
                    item.description+" · "+installedSize(item.bytes));
                if(ModelListFilter.matches(item.id,description+" "+item.title,
                        modelQuantization.getOrDefault(item.id,item.quantization),
                        filter,selectedModelFamily,selectedQuantization))
                    shown.add(item);
            }
        }
        sortTiles(shown);
        if(modelListHeader!=null)
            modelListHeader.setText(searching?
                getString(R.string.model_result_header,shown.size(),filterDisplay(selectedSort)):
                getString(R.string.model_recommended_header,shown.size()));
        if(shown.isEmpty()){
            String emptyMessage=searching?
                (onlineSearchPending?getString(R.string.model_online_searching):
                getString(filter.length()<2?R.string.model_no_local_match:R.string.model_no_match)):
                getString(all.isEmpty()?R.string.model_no_safe:R.string.model_no_filtered);
            TextView empty=text(emptyMessage,
                14,MUTED,false);
            empty.setPadding(dp(16),dp(22),dp(16),dp(22));
            empty.setBackground(bg(CARD,14));
            margin(empty,modelRows,0,8);
            return;
        }

        boolean reveal=animateNextTileUpdate&&animationsEnabled();
        animateNextTileUpdate=false;
        if(searching){
            renderGroupedSearch(shown);
            return;
        }
        if(modelListView){
            for(int pos=0;pos<shown.size();pos++){
                LinearLayout item=buildModelListRow(shown.get(pos));
                margin(item,modelRows,0,8);
                if(reveal&&pos<6){
                    item.setAlpha(0f);
                    item.setTranslationY(dp(6));
                    item.animate().alpha(1f).translationY(0f)
                        .setDuration(170).setStartDelay(Math.min(120,pos*20))
                        .setInterpolator(new DecelerateInterpolator()).start();
                }
            }
            updateTileDownloadProgress();
            return;
        }
        int available=modelRows.getWidth();
        if(available<=0)available=Math.max(dp(120),getResources().getDisplayMetrics().widthPixels-dp(32));
        int gap=dp(10),minCard=dp(uiTextScale>1.15f?178:148);
        int columns=ModelVariantCatalog.columns(available,minCard,gap);
        int sidePx=Math.max(1,(available-gap*(columns-1))/columns);
        int sideDp=Math.max(1,(int)(sidePx/getResources().getDisplayMetrics().density));
        for(int pos=0;pos<shown.size();pos+=columns){
            LinearLayout group=row();
            group.setGravity(Gravity.TOP|Gravity.START);
            for(int col=0;col<columns;col++){
                if(pos+col>=shown.size())break;
                ModelTileCatalog.Tile item=shown.get(pos+col);
                LinearLayout tile=buildModelTile(item,sideDp);
                LinearLayout.LayoutParams tileParams=new LinearLayout.LayoutParams(sidePx,sidePx);
                if(col>0)tileParams.leftMargin=gap;
                group.addView(tile,tileParams);
                if(reveal&&pos+col<6){
                    tile.setAlpha(0f);
                    tile.setTranslationY(dp(8));
                    tile.animate().alpha(1f).translationY(0f)
                        .setDuration(185).setStartDelay(Math.min(100,(pos+col)*18))
                        .setInterpolator(new DecelerateInterpolator()).start();
                }
            }
            margin(group,modelRows,0,10);
        }
        updateTileDownloadProgress();
    }

    private void showAllInstalledModels() {
        if(knownModels.isEmpty()){
            toast(getString(R.string.model_none_installed));
            return;
        }
        String[] entries=knownModels.toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.model_picker))
            .setItems(entries,(dialog,index)->{
                if(chatBusy||benchmarkBusy||diagnosticBusy){
                    toast(getString(R.string.busy));return;
                }
                selectModel(entries[index]);
                showTab(0);
            })
            .setNegativeButton(getString(R.string.close),null).show();
    }

    private void selectModel(String name) {
        if(chatBusy||attachmentReading||!knownModels.contains(name))return;
        saveLocalDraft();
        currentModel=name;
        getPreferences(MODE_PRIVATE).edit().putString("model",name).apply();
        ignoreSelection=true;
        if(modelChooser!=null)modelChooser.setSelection(knownModels.indexOf(name));
        ignoreSelection=false;
        loadHistory();renderHistory();renderInstalledModels();
        if(activeTab==3)refreshDiagnosticViews();
    }

    private void pullModel() {
        String model = modelInput.getText().toString().trim();
        if (model.isEmpty() || !model.matches("[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,119}")) {
            toast(getString(R.string.model_tag_invalid)); return;
        }
        if (pullBusy || benchmarkBusy || HuggingFaceCatalog.isDownloading()) {
            toast(getString(R.string.busy));return;
        }
        long featuredBytes=modelBytes.getOrDefault(model,0L);
        for(ModelVariantCatalog.Variant v:onlineVariants)if(v.model.equals(model)){featuredBytes=v.bytes;break;}
        if(featuredBytes==0)for(ModelVariantCatalog.Variant v:recommendedVariants)
            if(v.model.equals(model)){featuredBytes=v.bytes;break;}
        long availableBytes;
        try{availableBytes=new StatFs(getFilesDir().getAbsolutePath()).getAvailableBytes();}
        catch(Exception e){availableBytes=-1;}
        long reserved=ModelDownloadBudget.requiredBytes(featuredBytes);
        if(availableBytes>=0&&featuredBytes>0&&!ModelDownloadBudget.enough(availableBytes,featuredBytes)){
            new AlertDialog.Builder(this).setTitle(getString(R.string.storage_low))
                .setMessage(getString(R.string.storage_required,installedSize(reserved),installedSize(availableBytes)))
                .setPositiveButton(R.string.ok,null).show();
            return;
        }
        String storageHint=featuredBytes>0?getString(R.string.download_storage_hint,installedSize(featuredBytes),installedSize(reserved)):getString(R.string.download_size_unknown);
        String memoryHint=featuredBytes>0?getString(R.string.download_ram_hint,suitabilityLabel(advise(model,featuredBytes/(1024.0*1024.0*1024.0)).suitability)):"\n";
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.download_data_title))
            .setMessage(getString(R.string.download_confirm_body,model,storageHint,memoryHint))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.download_confirm), (d,w) -> beginDownload(model))
            .show();
    }

    private void beginDownload(String model) {
        if (pullBusy) return;
        if (!OllamaForegroundService.isRunning()) {
            toast(getString(R.string.download_server_wait));
            startServer(); return;
        }
        pullBusy = true; pullCancelled = false;
        lastFailedPullModel="";
        if(downloadRetryButton!=null)downloadRetryButton.setVisibility(View.GONE);
        downloadingModel=model;
        downloadingCardId=model;
        if(modelFilter.getText().toString().trim().isEmpty()){
            for(ModelTileCatalog.Tile card:deviceRecommendedTiles()){
                if(card.id.equalsIgnoreCase(model)||ModelVariantCatalog.stripQuant(card.id).equalsIgnoreCase(ModelVariantCatalog.stripQuant(model))){
                    downloadingCardId=card.id;break;
                }
            }
        }
        downloadingPercent=-1;
        animateNextTileUpdate=false;
        renderInstalledModels();  // Immediately show the accurate downloading tile.
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgress.setIndeterminate(true);
        ads.showBanner(downloadAdSlot);
        pullButton.setEnabled(false); pullButton.setAlpha(0.5f);
        cancelPullButton.setEnabled(true); cancelPullButton.setVisibility(View.VISIBLE);
        progressStatus.setText(getString(R.string.download_prepare_model,model));
        io.execute(() -> {
            String error = null;
            try {
                HttpURLConnection connection = OllamaHttp.newPullConnection(); pullConnection = connection;
                OllamaHttp.stream(connection, OllamaHttp.pullBody(model), item -> {
                    String status = item.optString("status", "진행 중");
                    long total = item.optLong("total", 0), completed = item.optLong("completed", 0);
                    String info=model+" · "+localizedLayerLabel(status,completed,total);
                    ui.post(() -> {
                        progressStatus.setText(info);
                        downloadingPercent=ModelDownloadBudget.layerPercent(completed,total);
                        updateTileDownloadProgress();
                        if (total > 0) {
                            downloadProgress.setIndeterminate(false);
                            downloadProgress.setProgress(downloadingPercent);
                        } else {
                            downloadProgress.setIndeterminate(true);
                        }
                    });
                });
            } catch (Exception ex) { error = ex.getMessage(); }
            finally { pullConnection = null; }
            final String failure = error;
            ui.post(() -> {
                pullBusy = false;
                downloadingModel="";
                downloadingCardId="";
                downloadingPercent=-1;
                renderInstalledModels();
                pullButton.setEnabled(true); pullButton.setAlpha(1f);
                cancelPullButton.setEnabled(false); cancelPullButton.setVisibility(View.GONE);
                progressStatus.setText(pullCancelled?getString(R.string.download_stopped):failure!=null?getString(R.string.download_failed,UiText.error(this,failure)):getString(R.string.download_completed,model));
                lastFailedPullModel=!pullCancelled&&failure!=null?model:"";
                if(downloadRetryButton!=null)
                    downloadRetryButton.setVisibility(lastFailedPullModel.isEmpty()?View.GONE:View.VISIBLE);
                downloadProgress.setIndeterminate(false);
                if (failure == null && !pullCancelled) downloadProgress.setProgress(100);
                else downloadProgress.setVisibility(View.GONE);
                if (failure == null && !pullCancelled) {
                    refreshModels();
                    new AlertDialog.Builder(this).setTitle(getString(R.string.model_ready))
                        .setMessage(getString(R.string.download_ready_message,model))
                        .setNegativeButton(getString(R.string.stay_models), null)
                        .setPositiveButton(getString(R.string.start_chat), (d,w) -> showTab(0)).show();
                }
            });
        });
    }

    private String sessionKey() { return "active_chat_" + currentModel; }
    private String currentSessionId() {
        String id = getPreferences(MODE_PRIVATE).getString(sessionKey(), "");
        if (id.isEmpty()) {
            id = freshSessionId();
            getPreferences(MODE_PRIVATE).edit().putString(sessionKey(), id).apply();
        }
        return id;
    }
    private String listKey() { return "chat_list_" + currentModel; }
    private String historyKey() { return "chat_v4_" + currentModel + "_" + currentSessionId(); }
    private String freshSessionId(){
        long id=System.currentTimeMillis();
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        while(prefs.contains("chat_v4_"+currentModel+"_"+id)||Long.toString(id).equals(prefs.getString(sessionKey(),"")))id++;
        return Long.toString(id);
    }
    private String draftKey(String scope){return "chat_draft_v1_"+scope;}
    private boolean ownsLoadedScope(){
        return !loadedHistoryScope.isEmpty()&&loadedHistoryScope.equals(historyKey());
    }
    private boolean originalStillStored(String scope,String original)throws Exception{
        return new JSONArray(getPreferences(MODE_PRIVATE).getString(scope,"[]")).toString().equals(original);
    }
    private void persistLocalDraft(String scope,String session,String prompt)throws Exception{
        if(!ownsLoadedScope()||!scope.equals(loadedHistoryScope)||!historyReadable||
            !originalStillStored(scope,history.toString()))throw new IllegalStateException("Chat scope changed");
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        JSONArray index=indexWith(session,shortTitle(prompt));
        android.content.SharedPreferences.Editor change=prefs.edit().putString(draftKey(scope),prompt)
            .putString(listKey(),index.toString());
        if(!prefs.contains(scope))change.putString(scope,history.toString());
        change.apply();
        refreshTabletRail();
    }
    private void saveLocalDraft(){
        if(guestOnly||chatInput==null||currentModel.isEmpty()||!historyReadable||!ownsLoadedScope())return;
        String text=chatBusy?pendingLocalPrompt:chatInput.getText().toString();
        if(text.length()>LocalChatTurns.MAX_PROMPT)return;
        try{
            if(!originalStillStored(loadedHistoryScope,history.toString()))return;
            if(text.isEmpty())getPreferences(MODE_PRIVATE).edit().remove(draftKey(loadedHistoryScope)).apply();
            else persistLocalDraft(loadedHistoryScope,currentSessionId(),text);
        }catch(Exception ignored){
            // Preserve existing records; do not replace a damaged index or a newer conversation.
        }
    }
    private void newChat() {
        if(cloudMode){if(cloudPanel!=null)cloudPanel.newChat();return;}
        if (chatBusy||attachmentReading) { toast(getString(R.string.new_chat_busy)); return; }
        if(voice!=null)voice.cancel();
        saveLocalDraft();
        String id=freshSessionId();
        getPreferences(MODE_PRIVATE).edit().putString(sessionKey(),id)
            .putString("chat_v4_"+currentModel+"_"+id,"[]").apply();
        history=new JSONArray();historyReadable=true;
        loadedHistoryScope="chat_v4_"+currentModel+"_"+id;
        if(chatInput!=null)chatInput.setText("");
        renderHistory();
        showTab(0);
    }
    private static final class DrawerChat {
        final String model,id,title,excerpt;
        final boolean pinned;
        final int matchIndex;
        final long created;
        DrawerChat(String model,String id,String title,boolean pinned){this(model,id,title,pinned,"",-1);}
        DrawerChat(String model,String id,String title,boolean pinned,String excerpt,int matchIndex) {
            this.model=model;this.id=id;this.title=title;this.pinned=pinned;this.excerpt=excerpt;this.matchIndex=matchIndex;
            long timestamp=0;
            try {timestamp=Long.parseLong(id);}catch(Exception ignored){}
            created=timestamp;
        }
    }

    /** Preserve all preexisting model-specific histories; show them in one list. */
    private List<DrawerChat> allSavedConversations(){
        List<DrawerChat> result=new ArrayList<>();
        Map<String,?> prefs=getPreferences(MODE_PRIVATE).getAll();
        for(Map.Entry<String,?> entry:prefs.entrySet()){
            String key=entry.getKey();
            if(!key.startsWith("chat_list_")||!(entry.getValue() instanceof String))continue;
            String model=key.substring("chat_list_".length());
            try {
                JSONArray records=new JSONArray((String)entry.getValue());
                for(int i=0;i<records.length();i++){
                    JSONObject obj=records.optJSONObject(i);
                    if(obj==null)continue;
                    String id=obj.optString("id","");
                    if(id.isEmpty()||id.length()>70)continue;
                    String label=obj.optString("title",getString(R.string.chat));
                    result.add(new DrawerChat(model,id,label,obj.optBoolean("pinned",false)));
                }
            }catch(Exception ignored){}
        }
        Collections.sort(result,(a,b)->ChatOrganization.compare(a.pinned,a.created,a.model+"\0"+a.id,
            b.pinned,b.created,b.model+"\0"+b.id));
        return result;
    }

    private TextView drawerRow(String title,String subtitle,boolean active){
        // Conversation labels are single-line; metadata stays in accessibility descriptions.
        TextView t=text(title,14,WHITE,active);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(12),dp(8),dp(10),dp(8));
        t.setMinHeight(dp(44));
        t.setBackground(bg(active?FIELD:BG,10));
        return t;
    }

    private void openSavedConversation(DrawerChat entry,Dialog drawer){
        if(attachmentReading||chatBusy||benchmarkBusy||diagnosticBusy||(cloudPanel!=null&&cloudPanel.isBusy())){
            toast(getString(R.string.busy));
            return;
        }
        if(!knownModels.contains(entry.model)){
            if(drawer!=null)drawer.dismiss();
            toast(getString(R.string.workspace_missing_model,entry.model));
            showTab(1);
            return;
        }
        if(cloudMode)returnToLocal();
        saveLocalDraft();
        if(!entry.model.equals(currentModel))selectModel(entry.model);
        getPreferences(MODE_PRIVATE).edit()
            .putString("active_chat_"+entry.model,entry.id).apply();
        loadHistory();renderHistory();
        if(drawer!=null)drawer.dismiss();
        showTab(0);
        if(entry.matchIndex>=0&&messages!=null&&messageScroll!=null){
            View hit=messages.findViewWithTag("chat-message-"+entry.matchIndex);
            if(hit!=null)hit.post(()->{
                if(hit.getParent()==messages)messageScroll.scrollTo(0,hit.getTop());
            });
        }
    }

    private void renderDrawerChats(LinearLayout list,Dialog drawer,List<DrawerChat> conversations,String query){
        final long serial=++chatSearchSerial;
        if(chatSearchTask!=null){chatSearchTask.cancel(true);chatSearchTask=null;}
        String search=LocalChatLibrary.query(query);
        if(search.isEmpty()){
            renderDrawerChatItems(list,drawer,conversations,true,conversations.size()>LocalChatLibrary.MAX_RESULTS,false);
            return;
        }
        list.removeAllViews();
        margin(text(getString(R.string.chat_search_working),13,MUTED,false),list,5,5);
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        ui.postDelayed(()->{
            if(isFinishing()||isDestroyed()||serial!=chatSearchSerial||(drawer!=null&&!drawer.isShowing()))return;
            chatSearchTask=chatSearchIo.submit(()->{
                List<DrawerChat> found=new ArrayList<>();long total=0;boolean incomplete=false,limited=false;
                for(DrawerChat entry:conversations){
                    if(Thread.currentThread().isInterrupted())return;
                    DrawerChat hit=null;
                    if(LocalChatLibrary.matches(entry.title,search)||LocalChatLibrary.matches(entry.model,search))hit=entry;
                    else{
                        try{
                            String raw=prefs.getString("chat_v4_"+entry.model+"_"+entry.id,"[]");
                            if(raw.length()>LocalChatLibrary.MAX_SEARCH_CONVERSATION||
                               total+raw.length()>LocalChatLibrary.MAX_SEARCH_TOTAL){incomplete=true;continue;}
                            total+=raw.length();
                            JSONArray saved=new JSONArray(raw);
                            for(int i=0;i<saved.length();i++){
                                if(Thread.currentThread().isInterrupted())return;
                                JSONObject item=saved.optJSONObject(i);if(item==null)continue;
                                String content=item.optString("content","");
                                if(LocalChatLibrary.matches(content,search)){
                                    hit=new DrawerChat(entry.model,entry.id,entry.title,entry.pinned,LocalChatLibrary.excerpt(content,search),i);break;
                                }
                            }
                        }catch(Exception e){incomplete=true;}
                    }
                    if(hit!=null){
                        if(found.size()==LocalChatLibrary.MAX_RESULTS){limited=true;break;}
                        found.add(hit);
                    }
                }
                final boolean partial=incomplete,capped=limited;
                ui.post(()->{
                    if(isFinishing()||isDestroyed()||serial!=chatSearchSerial||
                        (drawer!=null&&!drawer.isShowing())||(drawer==null&&currentRailDp<=0))return;
                    renderDrawerChatItems(list,drawer,found,false,capped,partial);
                });
            });
        },180);
    }
    private void renderDrawerChatItems(LinearLayout list,Dialog drawer,List<DrawerChat> conversations,boolean emptyQuery,boolean limited,boolean incomplete){
        list.removeAllViews();
        int count=0;
        for(DrawerChat entry:conversations){
            if(count>=LocalChatLibrary.MAX_RESULTS)break;
            count++;
            boolean active=!cloudMode&&entry.model.equals(currentModel)&&entry.id.equals(
                getPreferences(MODE_PRIVATE).getString("active_chat_"+entry.model,""));
            String display=(entry.pinned?"★  ":"")+entry.title;
            TextView item=drawerRow(display,"",active);
            if(!entry.excerpt.isEmpty()){
                item.setSingleLine(false);item.setMaxLines(3);
                item.setText(display+"\n"+entry.excerpt);
            }
            item.setContentDescription(getString(R.string.workspace_chat_model,entry.title,entry.model)+
                (entry.pinned?" · "+getString(R.string.chat_pinned):"")+
                (entry.excerpt.isEmpty()?"":". "+entry.excerpt));
            item.setSelected(active);item.setFocusable(true);item.setClickable(true);item.setMinHeight(dp(48));
            LinearLayout line=row();
            rowItem(line,item,1,3);
            Button manage=button("⋯",false);manage.setTextSize(19);
            manage.setMinimumWidth(dp(48));manage.setMinWidth(dp(48));manage.setMinimumHeight(dp(48));
            manage.setContentDescription(getString(R.string.chat_manage)+" · "+entry.title);
            rowItem(line,manage,0,0);
            LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(3);list.addView(line,p);
            item.setOnClickListener(v->openSavedConversation(entry,drawer));
            manage.setOnClickListener(v->manageConversation(entry,drawer));
        }
        if(count==0){
            TextView empty=text(getString(emptyQuery?R.string.workspace_no_history:R.string.workspace_no_results),13,MUTED,false);
            empty.setPadding(dp(13),dp(19),dp(10),dp(12));list.addView(empty,lp(-1,-2));
        }
        if(limited)margin(text(getString(R.string.chat_search_limited),11,MUTED,false),list,6,5);
        if(incomplete)margin(text(getString(R.string.chat_search_partial),11,MUTED,false),list,5,5);
    }

    /** Only updates title/pin metadata in the existing per-model index, never chat content. */
    private void updateConversationMetadata(DrawerChat entry,String title,Boolean pin,Dialog drawer){
        if(guestOnly||isUiBusy())return;
        if(title!=null&&!ChatOrganization.validTitle(title)){toast(getString(R.string.chat_title_invalid));return;}
        try{
            android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
            String key="chat_list_"+entry.model,raw=prefs.getString(key,"[]");
            JSONArray index=new JSONArray(raw);
            JSONObject selected=null;
            for(int i=0;i<index.length();i++){
                JSONObject obj=index.optJSONObject(i);
                if(obj!=null&&entry.id.equals(obj.optString("id"))){
                    if(selected!=null)throw new IllegalStateException("Duplicate conversation ID");
                    selected=obj;
                }
            }
            if(selected==null)throw new IllegalStateException("Conversation no longer exists");
            if(title!=null)selected.put("title",ChatOrganization.title(title));
            if(pin!=null)selected.put("pinned",pin);
            if(!raw.equals(prefs.getString(key,"[]")))throw new IllegalStateException("Conversation list changed");
            prefs.edit().putString(key,index.toString()).apply();
            if(drawer!=null)drawer.dismiss();
            refreshTabletRail();toast(getString(R.string.chat_manage_done));
        }catch(Exception e){toast(getString(R.string.chat_manage_failed));}
    }
    private void manageConversation(DrawerChat entry,Dialog drawer){
        if(guestOnly||isUiBusy()){toast(getString(R.string.busy));return;}
        String[] choices={getString(R.string.chat_rename),getString(entry.pinned?R.string.chat_unpin:R.string.chat_pin)};
        new AlertDialog.Builder(this).setTitle(entry.title).setItems(choices,(d,w)->{
            if(isUiBusy()||isFinishing()||isDestroyed())return;
            if(w==1){updateConversationMetadata(entry,null,!entry.pinned,drawer);return;}
            EditText title=edit(getString(R.string.chat_title_hint),entry.title,false);
            title.setSingleLine(true);
            title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(ChatOrganization.MAX_TITLE)});
            LinearLayout form=column();form.setPadding(dp(16),dp(6),dp(16),dp(6));form.addView(title,lp(-1,-2));
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.chat_rename).setView(form)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,null).create();
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String value=title.getText().toString();
                if(!ChatOrganization.validTitle(value)){toast(getString(R.string.chat_title_invalid));return;}
                dialog.dismiss();updateConversationMetadata(entry,value,null,drawer);
            });
        }).setNegativeButton(R.string.cancel,null).show();
    }

    private TextView drawerNavigationItem(String title){
        // The lower sidebar displays only these three titles—no subtitles or icons.
        boolean active=(getString(R.string.models).equals(title)&&activeTab==1)
            ||(getString(R.string.settings).equals(title)&&activeTab==2)
            ||(getString(R.string.devices).equals(title)&&activeTab==3);
        TextView item=text(title,14,WHITE,active);
        item.setSingleLine(true);
        item.setEllipsize(TextUtils.TruncateAt.END);
        item.setMinHeight(dp(42));
        item.setPadding(dp(12),dp(7),dp(10),dp(7));
        item.setBackground(bg(active?FIELD:BG,10));
        item.setSelected(active);
        item.setContentDescription(title+(active?" · "+getString(R.string.workspace_selected):""));
        item.setFocusable(true);
        item.setClickable(true);
        return item;
    }

    /**
     * Chat-style left sidebar with scrollable history and pinned app navigation.
     * The previous bottom navigation strip is intentionally removed.
     */
    private void showNavigationDrawer(){
        if(navigationDrawer!=null&&navigationDrawer.isShowing())return;
        Dialog drawer=new Dialog(this);
        navigationDrawer=drawer;
        LinearLayout shell=column();
        shell.setBackgroundColor(BG);
        shell.setElevation(dp(2));
        // Sidebar title and new conversation must clear the top status/cutout.
        shell.setPadding(dp(10),dp(14),dp(10),dp(10));
        shell.setOnApplyWindowInsetsListener((view,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){
                top=insets.getInsets(WindowInsets.Type.statusBars()|
                    WindowInsets.Type.displayCutout()).top;
                bottom=insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            }else{
                top=insets.getSystemWindowInsetTop();
                bottom=insets.getSystemWindowInsetBottom();
            }
            // Do not double count if Android already inset the dialog.
            int extraTop=Build.VERSION.SDK_INT>=35?top:0;
            int extraBottom=Build.VERSION.SDK_INT>=35?bottom:0;
            view.setPadding(dp(10),dp(14)+extraTop,dp(10),dp(10)+extraBottom);
            return insets;
        });

        LinearLayout heading=row();
        TextView product=text("OnHand AI",17,WHITE,true);
        rowItem(heading,product,1,8);
        Button close=button("×",false);
        close.setTextSize(20);
        close.setContentDescription(getString(R.string.drawer_close));
        rowItem(heading,close,0,0);
        close.setOnClickListener(v->drawer.dismiss());
        heading.setMinimumHeight(dp(48));
        margin(heading,shell,0,8);

        TextView newChatItem=drawerRow("✎   "+getString(R.string.new_chat),"",false);
        margin(newChatItem,shell,0,8);
        newChatItem.setOnClickListener(v->{
            if(chatBusy){toast(getString(R.string.new_chat_busy));return;}
            drawer.dismiss();
            newChat();
        });

        EditText search=edit(getString(R.string.chat_search_content),"",false);
        search.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(LocalChatLibrary.MAX_QUERY)});
        search.setSingleLine(true);
        search.setMinHeight(dp(44));
        margin(search,shell,0,9);
        margin(text(getString(R.string.recent_chats),12,MUTED,true),shell,0,5);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        LinearLayout chatList=column();
        chatList.setPadding(dp(0),dp(0),dp(2),dp(8));
        scroll.addView(chatList);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        final List<DrawerChat> conversations=allSavedConversations();
        renderDrawerChats(chatList,drawer,conversations,"");
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count){
                renderDrawerChats(chatList,drawer,conversations,s.toString());
            }
            @Override public void afterTextChanged(Editable s){}
        });

        View separator=new View(this);
        separator.setBackgroundColor(FIELD);
        LinearLayout.LayoutParams separatorParams=lp(-1,1);
        separatorParams.topMargin=dp(4);
        separatorParams.bottomMargin=dp(6);
        shell.addView(separator,separatorParams);

        TextView models=drawerNavigationItem(getString(R.string.models));
        TextView settings=drawerNavigationItem(getString(R.string.settings));
        TextView diagnostics=drawerNavigationItem(getString(R.string.devices));
        margin(models,shell,0,3);
        margin(settings,shell,0,3);
        margin(diagnostics,shell,0,7);
        models.setOnClickListener(v->{drawer.dismiss();showTab(1);});
        settings.setOnClickListener(v->{drawer.dismiss();showTab(2);});
        diagnostics.setOnClickListener(v->{drawer.dismiss();showTab(3);});

        drawer.setContentView(shell);
        drawer.setOnDismissListener(d->{
            chatSearchSerial++;
            if(chatSearchTask!=null)chatSearchTask.cancel(true);
            if(navigationDrawer==drawer)navigationDrawer=null;
        });
        Window window=drawer.getWindow();
        if(window!=null){
            window.setBackgroundDrawable(new ColorDrawable(BG));
            window.setStatusBarColor(BG);
            window.setNavigationBarColor(BG);
            window.getDecorView().setSystemUiVisibility(lightTheme()?
                (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR):0);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.22f);
            window.setGravity(Gravity.START|Gravity.TOP);
            if(animationsEnabled())window.setWindowAnimations(R.style.AiPocketDrawerMotion);
        }
        drawer.show();
        shell.post(shell::requestApplyInsets);
        if(window!=null){
            int available=getResources().getDisplayMetrics().widthPixels;
            int width=Math.min(dp(300),Math.max(dp(228),available-dp(56)));
            window.setLayout(width,WindowManager.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.START|Gravity.TOP);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void ensureSessionListed(String firstMessage) {
        try {
            String id = currentSessionId();
            JSONArray sessions = new JSONArray(getPreferences(MODE_PRIVATE).getString(listKey(), "[]"));
            for (int i = 0; i < sessions.length(); i++)
                if (id.equals(sessions.getJSONObject(i).optString("id"))) return;
            String title = firstMessage.replace('\n', ' ').trim();
            if (title.length() > 38) title = title.substring(0, 38) + "…";
            JSONArray newer = new JSONArray();
            newer.put(new JSONObject().put("id", id).put("title", title));
            for (int i = 0; i < sessions.length(); i++) newer.put(sessions.get(i));
            getPreferences(MODE_PRIVATE).edit().putString(listKey(), newer.toString()).apply();
            refreshTabletRail();
        } catch (Exception ignored) { }
    }
    private void loadHistory() {
        loadedHistoryScope=historyKey();
        if (!getPreferences(MODE_PRIVATE).contains(loadedHistoryScope)) {
            String legacy = getPreferences(MODE_PRIVATE).getString("chat_" + currentModel, "[]");
            if (!"[]".equals(legacy)) {
                getPreferences(MODE_PRIVATE).edit().putString(historyKey(), legacy).apply();
                try {
                    JSONArray previous = new JSONArray(legacy);
                    if (previous.length() > 0 && previous.optJSONObject(0) != null)
                        ensureSessionListed(previous.optJSONObject(0).optString("content",getString(R.string.legacy_chat_title)));
                } catch (Exception ignored) { }
            }
        }
        historyReadable=true;
        try { history = new JSONArray(getPreferences(MODE_PRIVATE).getString(loadedHistoryScope, "[]")); }
        catch (Exception ignored) { history = new JSONArray();historyReadable=false; }
        if(chatInput!=null){
            try{chatInput.setText(getPreferences(MODE_PRIVATE).getString(draftKey(loadedHistoryScope),""));}
            catch(Exception ignored){chatInput.setText("");}
        }
    }
    private static List<LocalChatTurns.Message> localMessages(JSONArray raw){
        List<LocalChatTurns.Message> out=new ArrayList<>();
        for(int i=0;i<raw.length();i++){
            JSONObject item=raw.optJSONObject(i);
            out.add(item==null?null:new LocalChatTurns.Message(item.optString("role",""),item.optString("content","")));
        }
        return out;
    }
    private static JSONArray modelContext(JSONArray source,String prompt)throws Exception{
        JSONArray request=new JSONArray();
        for(LocalChatTurns.Message item:LocalChatTurns.request(localMessages(source),prompt))
            request.put(new JSONObject().put("role",item.role).put("content",item.content));
        return request;
    }
    private static String shortTitle(String prompt){
        String title=prompt.replace('\n',' ').trim();
        return title.length()>38?title.substring(0,38)+"…":title;
    }
    private JSONArray indexWith(String id,String title)throws Exception{
        JSONArray previous=new JSONArray(getPreferences(MODE_PRIVATE).getString(listKey(),"[]"));
        for(int i=0;i<previous.length();i++){
            JSONObject row=previous.optJSONObject(i);
            if(row!=null&&id.equals(row.optString("id")))return previous;
        }
        JSONArray next=new JSONArray();
        next.put(new JSONObject().put("id",id).put("title",title));
        for(int i=0;i<previous.length();i++)next.put(previous.get(i));
        return next;
    }
    private void completeLocalTurn(String scope,String session,String original,String prompt,String answer)throws Exception{
        if(!historyReadable||!ownsLoadedScope()||!scope.equals(loadedHistoryScope)||!original.equals(history.toString())||
            !originalStillStored(scope,original))throw new IllegalStateException("Chat scope changed");
        if(!LocalChatTurns.validReply(answer))throw new IllegalArgumentException("Invalid response");
        JSONArray next=new JSONArray(original);
        next.put(new JSONObject().put("role","user").put("content",prompt));
        next.put(new JSONObject().put("role","assistant").put("content",answer));
        String encoded=next.toString();
        if(!LocalChatTurns.storageFits(next.length(),encoded.length()))throw new IllegalStateException("Chat storage limit");
        JSONArray index=indexWith(session,shortTitle(prompt));
        // One editor publishes the complete pair and index together; no provisional prompt in history.
        getPreferences(MODE_PRIVATE).edit().putString(scope,encoded)
            .putString(listKey(),index.toString()).remove(draftKey(scope)).apply();
        history=next;
        refreshTabletRail();
    }
    private void branchMessage(int selected,boolean edit){
        if(guestOnly||cloudMode||isUiBusy()||!historyReadable||!ownsLoadedScope())return;
        final String scope=loadedHistoryScope,original=history.toString();
        final LocalChatTurns.Branch plan;
        try{plan=LocalChatTurns.branch(localMessages(history),selected);}
        catch(Exception e){toast(getString(R.string.chat_branch_invalid));return;}
        final EditText question;
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(edit?R.string.chat_edit_question:R.string.chat_regenerate);
        if(edit){
            LinearLayout form=column();form.setPadding(dp(18),dp(8),dp(18),dp(8));
            margin(text(getString(R.string.chat_branch_notice),13,MUTED,false),form,0,8);
            question=edit(getString(R.string.message_hint),plan.prompt,true);
            question.setMinLines(3);question.setMaxLines(7);margin(question,form,0,4);
            builder.setView(form);
        }else{question=null;builder.setMessage(R.string.chat_branch_notice);}
        AlertDialog dialog=builder.setNegativeButton(R.string.cancel,null)
            .setPositiveButton(R.string.chat_branch_create,null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(isUiBusy()||cloudMode||!ownsLoadedScope()||!scope.equals(loadedHistoryScope)||!original.equals(history.toString())){
                dialog.dismiss();return;
            }
            String prompt=question==null?plan.prompt:question.getText().toString().trim();
            if(!LocalChatTurns.validPrompt(prompt)){toast(getString(R.string.chat_prompt_limit));return;}
            try{
                if(!originalStillStored(scope,original)){dialog.dismiss();toast(getString(R.string.chat_history_invalid));return;}
                JSONArray prefix=new JSONArray();
                JSONArray source=new JSONArray(original);
                for(int i=0;i<plan.prefixLength;i++)prefix.put(source.get(i));
                if(!LocalChatTurns.storageFits(prefix.length()+2,prefix.toString().length()+prompt.length())){
                    toast(getString(R.string.chat_storage_limit));return;
                }
                saveLocalDraft();
                String id=freshSessionId(),target="chat_v4_"+currentModel+"_"+id;
                JSONArray index=indexWith(id,getString(R.string.chat_branch_title,shortTitle(prompt)));
                getPreferences(MODE_PRIVATE).edit().putString(target,prefix.toString())
                    .putString(listKey(),index.toString()).putString(sessionKey(),id)
                    .putString(draftKey(target),prompt).apply();
                history=prefix;historyReadable=true;loadedHistoryScope=target;chatInput.setText(prompt);
                dialog.dismiss();renderHistory();showTab(0);sendChat();
            }catch(Exception e){toast(getString(R.string.chat_save_failed));}
        });
    }
    private void renderHistory(){renderHistory(true);}
    private void renderHistory(boolean follow) {
        if (messages == null) return;
        if(exportConversation!=null)exportConversation.setEnabled(historyReadable&&!chatBusy&&history.length()>0);
        int oldY=messageScroll==null?0:messageScroll.getScrollY();
        messages.removeAllViews();
        if(!historyReadable){
            margin(text(getString(R.string.chat_history_invalid),14,WHITE,false),messages,8,8);
            return;
        }
        if (history.length() == 0) {
            margin(text(getString(R.string.welcome), 26, WHITE, true), messages, 45, 9);
            margin(text(getString(R.string.welcome_sub),
                13, MUTED, false), messages, 0, 20);
            for (String hint : new String[]{getString(R.string.example_write),getString(R.string.example_plan),getString(R.string.example_code)}) {
                Button suggestion = button("✦  " + hint, false);
                margin(suggestion, messages, 4, 4);
                suggestion.setOnClickListener(v -> chatInput.setText(hint));
            }
            if (knownModels.isEmpty()) {
                Button install = button(getString(R.string.install_model), true);
                margin(install, messages, 18, 0);
                install.setOnClickListener(v -> showTab(1));
            }
        }
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if(item!=null)bubble(item.optString("role"),item.optString("content"),false,i);
        }
        if(follow)scrollBottom();
        else if(messageScroll!=null)messageScroll.post(()->messageScroll.scrollTo(0,oldY));
        refreshTabletRail();
    }
    private TextView bubble(String role,String content){return bubble(role,content,false,-1);}
    private TextView bubble(String role,String content,boolean pending){return bubble(role,content,pending,-1);}
    private TextView bubble(String role,String content,boolean pending,int index) {
        boolean user="user".equals(role);
        TextView t=text(user?content:"",15,WHITE,false);
        t.setTextIsSelectable(true);t.setLineSpacing(dp(3),1f);
        t.setPadding(dp(12),dp(12),dp(12),dp(12));
        t.setAutoLinkMask(0);
        LinearLayout wrapper=column();
        if(index>=0)wrapper.setTag("chat-message-"+index);
        wrapper.setGravity(user?Gravity.RIGHT:Gravity.LEFT);
        if(user){
            t.setMaxWidth(chatBubbleMaxWidth());
            t.setBackground(bg(BUBBLE,17));
            wrapper.addView(t,lp(-2,-2));
            if(index>=0){
                Button revise=button(getString(R.string.chat_edit_question),false);
                revise.setMinimumHeight(dp(48));wrapper.addView(revise,lp(-2,-2));
                revise.setOnClickListener(v->branchMessage(index,true));
            }
        }else{
            t.setBackgroundColor(BG);
            wrapper.addView(t,lp(-1,-2));
            LinearLayout rich=column();rich.setTag("rich-answer-body");rich.setVisibility(View.GONE);
            wrapper.addView(rich,lp(-1,-2));
            LinearLayout actions=row();actions.setTag("rich-answer-actions");
            Button copy=button(getString(R.string.copy),false);
            Button read=button(getString(R.string.read_answer),false);
            copy.setMinHeight(dp(48));copy.setMinimumHeight(dp(48));
            read.setMinHeight(dp(48));read.setMinimumHeight(dp(48));
            rowItem(actions,copy,1,6);rowItem(actions,read,1,0);
            copy.setOnClickListener(v->{
                Object raw=t.getTag();if(!(raw instanceof String))return;
                ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if(cb!=null){cb.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name),(String)raw));toast(getString(R.string.copied));}
            });
            read.setOnClickListener(v->{
                Object raw=t.getTag();if(!(raw instanceof String)||chatBusy||!foreground)return;
                if(voice==null)voice=new OnHandsVoice(this);
                if(voice.speaking()){voice.cancel();return;}
                voice.cancel();voice.speak(ChatMarkup.parse((String)raw).text);
            });
            wrapper.addView(actions,lp(-1,-2));
            if(index>=0&&"assistant".equals(role)){
                Button regenerate=button(getString(R.string.chat_regenerate),false);
                regenerate.setMinimumHeight(dp(48));wrapper.addView(regenerate,lp(-2,-2));
                regenerate.setOnClickListener(v->branchMessage(index,false));
            }
            actions.setVisibility(pending?View.GONE:View.VISIBLE);
            if(pending)t.setText(getString(R.string.app_name)+"\n"+content);
            else showAssistant(t,content,"",true);
        }
        margin(wrapper,messages,5,9);
        return t;
    }
    private void showAssistant(TextView t,String raw,String suffix,boolean actionable){
        t.setTag(actionable?raw:null);
        boolean richShown=false;
        if(t.getParent() instanceof LinearLayout){
            LinearLayout wrapper=(LinearLayout)t.getParent();
            LinearLayout rich=wrapper.findViewWithTag("rich-answer-body");
            View actions=wrapper.findViewWithTag("rich-answer-actions");
            if(rich!=null){
                rich.removeAllViews();
                View formatted=actionable?ChatRichView.create(this,getString(R.string.app_name),raw,suffix,WHITE,FIELD,uiTextScale):null;
                if(formatted!=null){rich.addView(formatted,lp(-1,-2));richShown=true;}
                rich.setVisibility(richShown?View.VISIBLE:View.GONE);
            }
            if(actions!=null)actions.setVisibility(actionable?View.VISIBLE:View.GONE);
        }
        t.setVisibility(richShown?View.GONE:View.VISIBLE);
        if(!richShown)t.setText(ChatPresentation.format(getString(R.string.app_name)+"\n",raw,suffix,FIELD));
        else t.setText("");
    }
    private boolean followsLatest(){
        if(messageScroll==null||messageScroll.getChildCount()==0)return true;
        View content=messageScroll.getChildAt(0);
        return content.getBottom()-messageScroll.getHeight()-messageScroll.getScrollY()<=dp(96);
    }
    private void scrollBottom() { if(messageScroll!=null)messageScroll.post(()->messageScroll.fullScroll(View.FOCUS_DOWN)); }
    private void setChatBusy(boolean busy) {
        chatBusy = busy;
        if(chatInput!=null)chatInput.setEnabled(!busy);
        sendButton.setEnabled(!busy);sendButton.setVisibility(busy?View.GONE:View.VISIBLE);
        stopChatButton.setEnabled(busy);stopChatButton.setVisibility(busy?View.VISIBLE:View.GONE);
        if(cloudShortcut!=null)cloudShortcut.setEnabled(!busy);
        modelChooser.setEnabled(!busy && !knownModels.isEmpty());
        if(voiceButton!=null)voiceButton.setEnabled(!busy);
        if(attachFileButton!=null)attachFileButton.setEnabled(!busy&&!attachmentReading);
        if(exportConversation!=null)exportConversation.setEnabled(!busy&&historyReadable&&history.length()>0);
    }
    private void sendChat() {
        String prompt=chatInput.getText().toString().trim();
        if(prompt.isEmpty())return;
        if(!LocalChatTurns.validPrompt(prompt)){toast(getString(R.string.chat_prompt_limit));return;}
        if(!historyReadable||!ownsLoadedScope()){toast(getString(R.string.chat_history_invalid));return;}
        if(currentModel.isEmpty()||!knownModels.contains(currentModel)){toast(getString(R.string.need_model));showTab(1);return;}
        if(isUiBusy()){toast(getString(benchmarkBusy?R.string.benchmark_wait:R.string.busy));return;}
        final String modelAtStart=currentModel,sessionAtStart=currentSessionId(),scopeAtStart=historyKey(),original=history.toString();
        if(!LocalChatTurns.storageFits(history.length()+2,original.length()+prompt.length())){
            toast(getString(R.string.chat_storage_limit));return;
        }
        final JSONArray requestHistory;
        try{requestHistory=modelContext(history,prompt);}catch(Exception e){toast(getString(R.string.chat_history_invalid));return;}
        final boolean speakThisTurn=speakNextAnswer;speakNextAnswer=false;
        if(voice!=null)voice.cancel();
        try{persistLocalDraft(scopeAtStart,sessionAtStart,prompt);}
        catch(Exception e){toast(getString(R.string.chat_history_invalid));return;}
        pendingLocalPrompt=prompt;
        chatInput.setText("");
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(chatInput.getWindowToken(),0);
        renderHistory();
        if(history.length()==0)messages.removeAllViews();
        bubble("user",prompt);
        TextView assistant=bubble("assistant",getString(R.string.thinking),true);
        lastStreamUi=0;
        chatCancelled=false;setChatBusy(true);scrollBottom();
        io.execute(() -> {
            final StringBuilder answer = new StringBuilder();
            String error = null;
            try {
                if ("cooperative".equals(computePreference())) {
                    StringBuilder reason = new StringBuilder();
                    ui.post(() -> activeEngineLabel.setText(getString(R.string.engine_rpc_load)));
                    if (!DistributedRunner.ensure(getApplicationContext(), modelAtStart, reason))
                        throw new java.io.IOException(getString(R.string.coop_error_fallback,UiText.error(this,reason.toString())));
                    checkChatCancelled();
                    HttpURLConnection connection = OllamaHttp.newCooperativeChatConnection();
                    publishChatConnection(connection);
                    ui.post(() -> activeEngineLabel.setText(getString(R.string.engine_rpc_test)));
                    OllamaHttp.streamGpuChat(connection, OllamaHttp.gpuChatBody(modelAtStart, requestHistory),
                        item -> appendAnswer(item, answer, assistant));
                } else {
                String choice = computePreference();
                boolean tryGpu = !"cpu".equals(choice) && computeReport.gpuCompute &&
                    GpuRunner.packaged(getApplicationContext());
                boolean gpu = false;
                StringBuilder fallback = new StringBuilder();
                if (tryGpu && !chatCancelled) {
                    ui.post(() -> activeEngineLabel.setText(getString(R.string.engine_gpu_test)));
                    // Release the CPU model before allocating the second GPU runtime.
                    try {
                        OllamaHttp.post("/api/generate", new JSONObject()
                            .put("model", modelAtStart).put("keep_alive", 0)
                            .put("stream", false), "POST");
                    } catch (Exception ignored) { }
                    gpu = GpuRunner.ensure(getApplicationContext(), modelAtStart, fallback);
                }
                checkChatCancelled();
                final boolean verifiedGpu = gpu;
                final String failedReason = fallback.toString();
                ui.post(() -> activeEngineLabel.setText(verifiedGpu ?
                    getString(R.string.engine_gpu_verified):
                    getString(R.string.engine_cpu_verified)+(failedReason.isEmpty()?"":" "+getString(R.string.engine_gpu_failed))));
                if (gpu) {
                    try {
                        HttpURLConnection connection = OllamaHttp.newGpuChatConnection();
                        publishChatConnection(connection);
                        OllamaHttp.streamGpuChat(connection, OllamaHttp.gpuChatBody(modelAtStart, requestHistory),
                            item -> appendAnswer(item, answer, assistant));
                    } catch (Exception gpuError) {
                        if(chatCancelled||Thread.currentThread().isInterrupted()||answer.length()>0)throw gpuError; // never duplicate a partial/cancelled response
                        ui.post(() -> activeEngineLabel.setText(getString(R.string.engine_cpu_fallback)));
                        HttpURLConnection connection = OllamaHttp.newChatConnection();
                        publishChatConnection(connection);
                        OllamaHttp.streamChat(connection, OllamaHttp.chatBody(modelAtStart, requestHistory),
                            item -> appendAnswer(item, answer, assistant));
                    }
                } else {
                    HttpURLConnection connection = OllamaHttp.newChatConnection();
                    publishChatConnection(connection);
                    OllamaHttp.streamChat(connection, OllamaHttp.chatBody(modelAtStart, requestHistory),
                        item -> appendAnswer(item, answer, assistant));
                }
                }
                checkChatCancelled();
                if(!LocalChatTurns.validReply(answer.toString()))throw new java.io.IOException("CHAT_EMPTY_ANSWER");
            } catch (Exception ex) { error=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage(); }
            finally { chatConnection = null; }
            String finalError=error,finalAnswer=answer.toString();
            ui.post(()->{
                if(isFinishing()||isDestroyed())return;
                boolean follow=followsLatest();
                boolean sameScope=modelAtStart.equals(currentModel)&&ownsLoadedScope()&&scopeAtStart.equals(loadedHistoryScope);
                boolean saved=false;String problem=finalError;
                if(sameScope&&!chatCancelled&&problem==null){
                    try{completeLocalTurn(scopeAtStart,sessionAtStart,original,prompt,finalAnswer);saved=true;}
                    catch(Exception e){problem="CHAT_SAVE_FAILED";}
                }
                pendingLocalPrompt="";setChatBusy(false);
                if(saved){
                    renderHistory(follow);
                    if(speakThisTurn&&foreground&&voice!=null)voice.speak(finalAnswer);
                }else if(sameScope){
                    chatInput.setText(prompt);
                    String notice=chatCancelled?getString(R.string.generation_cancelled):localChatError(problem);
                    String suffix="\n\n"+getString(R.string.chat_draft_recovered);
                    if(!finalAnswer.isEmpty())showAssistant(assistant,finalAnswer,"\n\n"+notice+suffix,true);
                    else showAssistant(assistant,notice,suffix,false);
                    if(follow)scrollBottom();
                }else{
                    // Original draft remains stored under its captured scope.
                    renderHistory(follow);
                }
            });
        });
    }
    private String localChatError(String error){
        if("CHAT_STREAM_INCOMPLETE".equals(error))return getString(R.string.chat_stream_incomplete);
        if("CHAT_REPLY_LIMIT".equals(error))return getString(R.string.chat_reply_limit);
        if("CHAT_EMPTY_ANSWER".equals(error))return getString(R.string.chat_empty_answer);
        if("CHAT_SAVE_FAILED".equals(error))return getString(R.string.chat_save_failed);
        return getString(R.string.error_label)+UiText.error(this,error);
    }
    private void checkChatCancelled()throws java.io.InterruptedIOException{
        if(chatCancelled||Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("CHAT_CANCELLED");
    }
    private void publishChatConnection(HttpURLConnection connection)throws Exception{
        chatConnection=connection;
        if(chatCancelled||Thread.currentThread().isInterrupted()){
            connection.disconnect();throw new java.io.InterruptedIOException("CHAT_CANCELLED");
        }
    }
    private void appendAnswer(JSONObject item,StringBuilder answer,TextView assistant)throws Exception{
        checkChatCancelled();
        JSONObject message = item.optJSONObject("message");
        if (message == null) return;
        String part = message.optString("content", "");
        if (part.isEmpty()) return;
        if((long)answer.length()+part.length()>LocalChatTurns.MAX_REPLY)throw new java.io.IOException("CHAT_REPLY_LIMIT");
        answer.append(part);
        long now=android.os.SystemClock.elapsedRealtime();
        if(lastStreamUi!=0&&now-lastStreamUi<80)return;
        lastStreamUi=now;
        String display=answer.toString();
        ui.post(()->{if(isFinishing()||isDestroyed())return;boolean follow=followsLatest();assistant.setText(getString(R.string.app_name)+"\n"+display);if(follow)scrollBottom();});
    }
    private void stopChat() {
        chatCancelled = true;
        if(voice!=null)voice.cancel();
        HttpURLConnection c = chatConnection;
        if (c != null) c.disconnect();
    }

    private void pickChatAttachment(){
        if(guestOnly||cloudMode||isUiBusy()||!historyReadable||!ownsLoadedScope()){toast(getString(R.string.busy));return;}
        if(currentModel.isEmpty()){toast(getString(R.string.need_model));return;}
        final String scope=loadedHistoryScope;
        new AlertDialog.Builder(this).setTitle(R.string.attachment_pick).setMessage(R.string.attachment_formats)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,(d,w)->{
                if(isUiBusy()||cloudMode||!ownsLoadedScope()||!scope.equals(loadedHistoryScope))return;
                attachmentScope=scope;
                try{
                    Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    picker.addCategory(Intent.CATEGORY_OPENABLE);picker.setType("*/*");
                    picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    picker.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/*","application/json","application/octet-stream",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/pdf"});
                    startActivityForResult(picker,PICK_ATTACHMENT);
                }catch(Exception e){attachmentScope="";toast(getString(R.string.chat_export_unavailable));}
            }).show();
    }
    private void finishChatAttachment(int result,Intent data){
        String scope=attachmentScope;attachmentScope="";
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(guestOnly||cloudMode||isUiBusy()||!ownsLoadedScope()||!scope.equals(loadedHistoryScope)||!"content".equals(uri.getScheme())){
            toast(getString(R.string.attachment_scope_changed));return;
        }
        attachmentReading=true;long serial=++attachmentSerial;
        if(attachFileButton!=null)attachFileButton.setEnabled(false);
        if(sendButton!=null)sendButton.setEnabled(false);
        toast(getString(R.string.attachment_reading));
        io.execute(()->{
            AttachmentText.Result extracted=null;int error=R.string.attachment_invalid;
            try{
                String name=null;long bytes=-1;
                try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
                    if(cursor!=null&&cursor.moveToFirst()){
                        int ni=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME),si=cursor.getColumnIndex(OpenableColumns.SIZE);
                        if(ni>=0)name=cursor.getString(ni);
                        if(si>=0&&!cursor.isNull(si))bytes=cursor.getLong(si);
                    }
                }
                boolean pdf=PdfExcerpt.supports(name);
                if(pdf&&Build.VERSION.SDK_INT<35)throw new AttachmentText.Failure(AttachmentText.Problem.PDF_VERSION);
                if(bytes>AttachmentText.maxBytes(name))throw new AttachmentText.Failure(AttachmentText.Problem.TOO_LARGE);
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    extracted=pdf?PdfTextClient.read(this,name,in):AttachmentText.read(name,in);
                }
            }catch(AttachmentText.Failure e){
                error=e.problem==AttachmentText.Problem.UNSUPPORTED?R.string.attachment_unsupported:
                    e.problem==AttachmentText.Problem.TOO_LARGE?R.string.attachment_too_large:
                    e.problem==AttachmentText.Problem.EMPTY?R.string.attachment_empty:
                    e.problem==AttachmentText.Problem.PDF_VERSION?R.string.attachment_pdf_version:
                    e.problem==AttachmentText.Problem.PDF_NO_TEXT?R.string.attachment_pdf_no_text:
                    e.problem==AttachmentText.Problem.PDF_PROTECTED?R.string.attachment_pdf_protected:
                    e.problem==AttachmentText.Problem.PDF_TIMEOUT?R.string.attachment_pdf_timeout:R.string.attachment_invalid;
            }catch(Exception ignored){}
            final AttachmentText.Result preview=extracted;final int fail=error;
            ui.post(()->{
                if(isFinishing()||isDestroyed()||serial!=attachmentSerial)return;
                attachmentReading=false;
                if(attachFileButton!=null)attachFileButton.setEnabled(!chatBusy);
                if(sendButton!=null)sendButton.setEnabled(!chatBusy);
                if(cloudMode||!ownsLoadedScope()||!scope.equals(loadedHistoryScope)||activeTab!=0){toast(getString(R.string.attachment_scope_changed));return;}
                if(preview==null){toast(getString(fail));return;}
                showAttachmentPreview(scope,preview);
            });
        });
    }
    private void showAttachmentPreview(String scope,AttachmentText.Result preview){
        ScrollView scroll=new ScrollView(this);
        LinearLayout panel=column();panel.setPadding(dp(16),dp(8),dp(16),dp(8));scroll.addView(panel);
        margin(text(preview.name,15,WHITE,true),panel,0,6);
        margin(text(getString(R.string.attachment_notice),12,WHITE,false),panel,0,6);
        margin(text(getString(R.string.attachment_rpc_notice),12,MUTED,false),panel,0,6);
        if(preview.office)margin(text(getString(R.string.attachment_office_note),12,MUTED,false),panel,0,6);
        if(preview.pdf){
            margin(text(getString(R.string.attachment_pdf_note,preview.pdfExamined,preview.pdfPages,preview.pdfTextPages),12,MUTED,false),panel,0,6);
            margin(text(getString(R.string.attachment_pdf_privacy),12,MUTED,false),panel,0,6);
        }
        if(preview.partial)margin(text(getString(R.string.attachment_partial),12,MUTED,true),panel,0,6);
        EditText excerpt=edit(getString(R.string.attachment_preview),preview.text,true);
        excerpt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(AttachmentText.MAX_EXCERPT)});
        excerpt.setMinLines(4);excerpt.setMaxLines(10);excerpt.setSaveEnabled(false);panel.addView(excerpt,lp(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.attachment_preview).setView(scroll)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.attachment_insert,null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(isUiBusy()||cloudMode||!ownsLoadedScope()||!scope.equals(loadedHistoryScope)||activeTab!=0){
                dialog.dismiss();toast(getString(R.string.attachment_scope_changed));return;
            }
            try{
                String combined=AttachmentText.include(chatInput.getText().toString(),preview.name,excerpt.getText().toString());
                chatInput.setText(combined);chatInput.setSelection(combined.length());saveLocalDraft();
                dialog.dismiss();toast(getString(R.string.attachment_added));
            }catch(IllegalArgumentException e){toast(getString(R.string.attachment_context_limit));}
        });
    }

    private void clearExportRequest(){exportScope="";exportDigest="";exportModel="";exportJson=false;}
    private void chooseChatExport(){
        if(isUiBusy()||cloudMode||guestOnly||!ownsLoadedScope()||!historyReadable)return;
        if(history.length()==0){toast(getString(R.string.chat_export_empty));return;}
        new AlertDialog.Builder(this).setTitle(R.string.chat_export).setMessage(R.string.chat_export_notice)
            .setNegativeButton(R.string.cancel,null).setNeutralButton("JSON",(d,w)->beginChatExport(true))
            .setPositiveButton("Markdown",(d,w)->beginChatExport(false)).show();
    }
    private void beginChatExport(boolean json){
        if(isUiBusy()||cloudMode||!ownsLoadedScope()||!historyReadable||history.length()==0)return;
        try{
            String raw=getPreferences(MODE_PRIVATE).getString(loadedHistoryScope,"");
            if(raw.length()>LocalChatLibrary.MAX_EXPORT){toast(getString(R.string.chat_export_large));return;}
            if(raw.isEmpty()||!new JSONArray(raw).toString().equals(history.toString())){
                toast(getString(R.string.chat_export_changed));return;
            }
            exportScope=loadedHistoryScope;exportDigest=LocalChatLibrary.digest(raw);exportModel=currentModel;exportJson=json;
            Intent picker=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType(json?"application/json":"text/markdown");
            picker.putExtra(Intent.EXTRA_TITLE,"OnHand-AI-"+System.currentTimeMillis()+(json?".json":".md"));
            startActivityForResult(picker,EXPORT_CHAT);
        }catch(Exception e){clearExportRequest();toast(getString(R.string.chat_export_unavailable));}
    }
    private void finishChatExport(int result,Intent data){
        String scope=exportScope,hash=exportDigest,model=exportModel;boolean json=exportJson;
        clearExportRequest();
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(guestOnly||scope.isEmpty()||model.isEmpty()||!scope.startsWith("chat_v4_"+model+"_")||hash.length()!=64){
            toast(getString(R.string.chat_export_changed));return;
        }
        Uri destination=data.getData();
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        final String user=getString(R.string.chat_export_user),assistant=getString(R.string.chat_export_assistant),other=getString(R.string.chat_export_other);
        io.execute(()->{
            int message=R.string.chat_export_failed;
            try{
                String raw=prefs.getString(scope,"");
                if(raw.length()>LocalChatLibrary.MAX_EXPORT)message=R.string.chat_export_large;
                else if(raw.isEmpty()||!LocalChatLibrary.digest(raw).equals(hash))message=R.string.chat_export_changed;
                else{
                    JSONArray snapshot=new JSONArray(raw);
                    String content=json?raw:LocalChatLibrary.markdown(model,localMessages(snapshot),user,assistant,other);
                    if(content.length()>LocalChatLibrary.MAX_EXPORT)message=R.string.chat_export_large;
                    else{
                        try(java.io.OutputStream out=getContentResolver().openOutputStream(destination,"wt")){
                            if(out==null)throw new java.io.IOException("Document output unavailable");
                            out.write(content.getBytes(StandardCharsets.UTF_8));out.flush();
                        }
                        message=R.string.chat_export_done;
                    }
                }
            }catch(Exception ignored){}
            final int state=message;
            ui.post(()->{if(!isFinishing()&&!isDestroyed())toast(getString(state));});
        });
    }
    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if(code==PICK_ATTACHMENT){finishChatAttachment(result,data);return;}
        if(code==EXPORT_CHAT){finishChatExport(result,data);return;}
        if (code != PICK_GGUF || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String displayName = "model.gguf";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) displayName = cursor.getString(idx);
            }
        } catch (Exception ignored) { }
        if (displayName == null || !displayName.toLowerCase().endsWith(".gguf")) {
            toast(getString(R.string.gguf_pick)); return;
        }
        final String modelName = "imported-" + System.currentTimeMillis();
        progressStatus.setText(getString(R.string.gguf_importing));
        io.execute(() -> {
            File copied = null, modelfile = null;
            String importStatus = "";
            try {
                File imports = new File(getFilesDir(), "imports");
                if (!imports.exists() && !imports.mkdirs()) throw new Exception(getString(R.string.file_directory_failed));
                copied = new File(imports, modelName + ".gguf");
                modelfile = new File(imports, modelName + ".Modelfile");
                long available = new StatFs(imports.getAbsolutePath()).getAvailableBytes();
                long total = 0;
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(copied)) {
                    if (in == null) throw new Exception(getString(R.string.file_open_failed));
                    byte[] buf = new byte[1024 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        total += n;
                        if (total > available - (256L * 1024 * 1024))
                            throw new Exception(getString(R.string.storage_low));
                        out.write(buf, 0, n);
                    }
                }
                try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(modelfile), StandardCharsets.UTF_8)) {
                    w.write("FROM " + copied.getAbsolutePath() + "\n");
                }
                ProcessBuilder pb = new ProcessBuilder(OllamaForegroundService.ollamaBinary(this).getAbsolutePath(),
                    "create", modelName, "-f", modelfile.getAbsolutePath());
                OllamaForegroundService.configureEnvironment(this, pb);
                Process p = pb.start();
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (output.length() > 3500) output.delete(0, 1500);
                        output.append(line).append('\n');
                        String now = line;
                        ui.post(() -> progressStatus.setText(getString(R.string.gguf_register_progress,now)));
                    }
                }
                int exit = p.waitFor();
                if (exit != 0) throw new Exception(getString(R.string.error_create_exit,exit,output.toString()));
                importStatus = getString(R.string.gguf_register_done,modelName);
                // The created Ollama model has its own blob, so remove the temporary copy.
                if (copied != null) copied.delete();
            } catch (Exception e) { importStatus = getString(R.string.gguf_import_error,e.getMessage()); }
            finally {
                if (modelfile != null) modelfile.delete();
                if (copied != null) copied.delete();
            }
            String finalResult = importStatus;
            ui.post(() -> { progressStatus.setText(finalResult); refreshModels(); });
        });
    }

    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
}
