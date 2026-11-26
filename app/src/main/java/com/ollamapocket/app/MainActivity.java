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
    private boolean guestOnly, foreground, speakNextAnswer;
    private OnHandsVoice voice;
    private Button voiceButton, cloudShortcut;
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
                            String identity=deviceProfile==null?"기기 확인 중":
                                deviceProfile.device+" · "+deviceProfile.cores+"코어";
                            performanceHudView.setText(identity+"\n"+reading.compact());
                            performanceHudView.setContentDescription(
                                "기기 상태 실시간 갱신. "+reading.detail()+
                                ". 탭하면 상세 진단을 볼 수 있습니다.");
                        }
                        if(activeTab==3)refreshDiagnosticViews();
                    });
                }catch(Exception e){
                    ui.post(()->{
                        if(performanceHudView!=null&&!isFinishing())
                            performanceHudView.setText("성능 HUD 다시 측정 중… · 진단 탭에서 확인");
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
        if(state!=null&&chatInput!=null)
            chatInput.setText(state.getString("draftMessage",""));
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
        if(chatInput!=null)out.putString("draftMessage",chatInput.getText().toString());
    }
    @Override protected void onResume() {
        super.onResume();
        foreground=true;
        if(guestOnly)return;
        ui.post(poll);
        ui.post(hudPoll);
        ui.removeCallbacks(sunsetThemePoll);
        ui.post(sunsetThemePoll);
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastPauseElapsed > 0 && now - lastPauseElapsed > 120_000
                && getPreferences(MODE_PRIVATE).getBoolean("welcome_v2_seen", false)
                && !pullBusy && !chatBusy && !benchmarkBusy && (voice==null||(!voice.listening()&&!voice.speaking()))) {
            ads.showAppOpenIfReady();
        }
    }
    @Override protected void onPause() {
        foreground=false;
        if(voice!=null)voice.cancel();
        lastPauseElapsed = android.os.SystemClock.elapsedRealtime();
        ui.removeCallbacks(poll);
        ui.removeCallbacks(hudPoll);
        ui.removeCallbacks(sunsetThemePoll);
        super.onPause();
    }
    @Override protected void onDestroy() {
        if(navigationDrawer!=null&&navigationDrawer.isShowing())navigationDrawer.dismiss();
        if (chatConnection != null) chatConnection.disconnect();
        if (pullConnection != null) pullConnection.disconnect();
        ui.removeCallbacks(hudPoll);
        ui.removeCallbacks(sunsetThemePoll);
        if(onlineSearchRunnable!=null)ui.removeCallbacks(onlineSearchRunnable);
        if(onlineSearchTask!=null)onlineSearchTask.cancel(true);
        if(onlineHfTask!=null)onlineHfTask.cancel(true);
        io.shutdownNow();
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
        if(voice!=null)voice.cancel();
        startActivity(new Intent(this,CloudChatActivity.class));
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
            int top=0,bottom=0;
            if(Build.VERSION.SDK_INT>=30){
                top=insets.getInsets(WindowInsets.Type.statusBars()|
                    WindowInsets.Type.displayCutout()).top;
                bottom=insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            }else{
                top=insets.getSystemWindowInsetTop();
                bottom=insets.getSystemWindowInsetBottom();
            }
            int safeTop=dp(15)+(Build.VERSION.SDK_INT>=35?top:0);
            int safeBottom=dp(10)+(Build.VERSION.SDK_INT>=35?bottom:0);
            if(view.getPaddingTop()!=safeTop||view.getPaddingBottom()!=safeBottom)
                view.setPadding(dp(12),safeTop,dp(12),safeBottom);
            return insets;
        });
        setContentView(root);
        root.post(root::requestApplyInsets);
        // Mobile-friendly WebUI-inspired navigation; independent Android widgets and branding.
        LinearLayout header = row();
        Button menu = button("☰", false);
        menu.setContentDescription(getString(R.string.menu));
        menu.setTextSize(20);
        menu.setMinWidth(dp(46));menu.setMinimumWidth(dp(46));
        rowItem(header, menu, 0, 9);
        menu.setOnClickListener(v -> showNavigationDrawer());
        LinearLayout branding = column();
        topPageTitle = text("On Hands AI", 19, WHITE, true);
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
            String status=sample==null?getString(R.string.hud_check):sample.detail();
            if(deviceProfile!=null)status=deviceProfile.description()+"\n\n"+status;
            if(computeReport!=null)status+="\n\n"+computeReport.description();
            if("host".equals(CoopSession.get(this).role()))
                status+="\n협력 노드: "+CoopSession.get(this).readyNodes().size()+"대";
            new AlertDialog.Builder(this).setTitle(getString(R.string.hud_title))
                .setMessage(status).setPositiveButton(getString(R.string.close),null).show();
        });
        pages = new FrameLayout(this);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, 0, 1);
        root.addView(pages, contentParams);
        createChatPage(); createModelPage(); createServerPage(); createDiagnosticsPage();
        pages.addView(chatPage); pages.addView(modelPage);
        pages.addView(serverPage); pages.addView(diagnosticPage);
        // Navigation is exclusively in the top-left drawer, not below the composer.
        // This avoids clipping against Android gesture navigation and enlarged fonts.
    }

    private void createChatPage() {
        chatPage = column();
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
        margin(modelRow, chatPage, 2, 7);
        activeEngineLabel = text(getString(R.string.engine_idle), 11, MUTED, false);
        margin(activeEngineLabel, chatPage, 0, 7);
        modelChooser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (ignoreSelection || knownModels.isEmpty() || position >= knownModels.size()) return;
                String model = knownModels.get(position);
                if (model.equals(currentModel)) return;
                if (chatBusy) { toast(getString(R.string.model_wait)); return; }
                currentModel = model;
                getPreferences(MODE_PRIVATE).edit().putString("model", model).apply();
                loadHistory(); renderHistory();
            }
        });
        messageScroll = new ScrollView(this); messageScroll.setFillViewport(true);
        messageScroll.setBackgroundColor(BG);
        messages = column(); messages.setPadding(dp(4), dp(18), dp(4), dp(12));
        messageScroll.addView(messages);
        LinearLayout.LayoutParams scrollParam = new LinearLayout.LayoutParams(-1, 0, 1);
        chatPage.addView(messageScroll, scrollParam);
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
        margin(composer, chatPage, 8, 2);
        stopChatButton.setEnabled(false);stopChatButton.setVisibility(View.GONE);
        sendButton.setMinWidth(dp(48));sendButton.setMinimumWidth(dp(48));
        stopChatButton.setMinWidth(dp(48));stopChatButton.setMinimumWidth(dp(48));
        voiceButton.setMinWidth(dp(48));voiceButton.setMinimumWidth(dp(48));
        sendButton.setOnClickListener(v -> sendChat());
        stopChatButton.setOnClickListener(v -> stopChat());
        margin(text(getString(R.string.disclaimer),
            10, MUTED, false), chatPage, 0, 0);
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
        Button manualToggle=button("고급 옵션 · 모델명 직접 입력 ▾",false);
        margin(manualToggle,content,0,7);
        LinearLayout manualDownloadPanel=column();
        manualDownloadPanel.setVisibility(View.GONE);
        margin(text("통합 검색에서 찾지 못한 Ollama 태그만 직접 입력하세요.",
            12,MUTED,false),manualDownloadPanel,0,7);
        String recommendedDefault="qwen2.5:0.5b";
        for(DeviceProfiler.Recommendation rec:DeviceProfiler.recommend(deviceProfile)){
            if(rec.selectable){recommendedDefault=rec.model;break;}
        }
        modelInput=edit("모델 태그 예: qwen2.5:1.5b",recommendedDefault,false);
        margin(modelInput,manualDownloadPanel,0,7);
        pullButton=button("태그로 다운로드",true);
        margin(pullButton,manualDownloadPanel,0,8);
        pullButton.setOnClickListener(v->pullModel());
        margin(manualDownloadPanel,content,0,6);
        manualToggle.setOnClickListener(v->{
            boolean expanded=manualDownloadPanel.getVisibility()==View.VISIBLE;
            manualDownloadPanel.setVisibility(expanded?View.GONE:View.VISIBLE);
            manualToggle.setText(expanded?"고급 옵션 · 모델명 직접 입력 ▾":
                "고급 옵션 · 모델명 직접 입력 ▴");
        });

        LinearLayout downloadStatusRow=row();
        progressStatus=text("모델 선택 후 다운로드 진행 상황이 여기에 표시됩니다.",12,MUTED,false);
        rowItem(downloadStatusRow,progressStatus,1,6);
        downloadRetryButton=button("재시도",false);
        rowItem(downloadStatusRow,downloadRetryButton,0,6);
        downloadRetryButton.setVisibility(View.GONE);
        downloadRetryButton.setOnClickListener(v->{
            if(lastFailedPullModel.isEmpty()||pullBusy)return;
            modelInput.setText(lastFailedPullModel);
            pullModel();
        });
        cancelPullButton=button("다운로드 취소",false);
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
        margin(text("광고"+(BuildConfig.DEBUG?" · 테스트":""),11,MUTED,false),content,0,3);
        downloadAdSlot=new FrameLayout(this);margin(downloadAdSlot,content,0,4);
        ads.register(downloadAdSlot,AdSupport.PLACEMENT_DOWNLOAD);
        margin(text("모델 다운로드와 광고에는 데이터가 사용되며 통신료가 발생할 수 있습니다.",
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
            SolarTheme.CITY_LABELS[SolarTheme.cityIndex(chosenCity)],false);
        margin(cityChoice,content,0,12);
        cityChoice.setVisibility("auto".equals(themeMode)?View.VISIBLE:View.GONE);
        cityChoice.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle(getString(R.string.theme_city_title))
            .setSingleChoiceItems(SolarTheme.CITY_LABELS,SolarTheme.cityIndex(chosenCity),
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
        return chatBusy||pullBusy||diagnosticBusy||benchmarkBusy
               ||HuggingFaceCatalog.isDownloading();
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
        margin(text("기기 / 연결 진단",23,WHITE,true),content,0,5);
        margin(text("현재 기기와 같은 세션에 참가한 기기를 확인합니다.",13,MUTED,false),
            content,0,12);

        LinearLayout ownTitle=row();
        rowItem(ownTitle,text("내 기기",18,WHITE,true),1,5);
        Button refresh=button("진단 새로고침",false);
        rowItem(ownTitle,refresh,0,0);
        margin(ownTitle,content,0,7);
        deviceSummary=text(deviceProfile.description(),13,WHITE,false);
        deviceSummary.setPadding(dp(14),dp(13),dp(14),dp(13));
        deviceSummary.setBackground(bg(CARD,14));
        margin(deviceSummary,content,0,7);
        margin(text("모델 구동 적합도",18,WHITE,true),content,0,7);
        suitabilitySummary=text("설치 모델 확인 중…",13,WHITE,false);
        suitabilitySummary.setPadding(dp(14),dp(13),dp(14),dp(13));
        suitabilitySummary.setBackground(bg(CARD,14));
        margin(suitabilitySummary,content,0,10);
        computeSummary=text(computeReport.description(),12,MUTED,false);
        computeSummary.setPadding(dp(14),dp(12),dp(14),dp(12));
        computeSummary.setBackground(bg(CARD,14));
        margin(computeSummary,content,0,8);
        localConnectionStatus=text("연결 상태 확인 중",12,MUTED,false);
        margin(localConnectionStatus,content,0,10);
        refresh.setOnClickListener(v->{
            deviceProfile=DeviceProfiler.scan(this);
            computeReport=ComputeProfiler.scan(this);
            refreshDiagnosticViews();
            populateModelFilters();
            renderInstalledModels();
        });
        margin(text("권한 안내",16,WHITE,true),content,0,6);
        margin(text("기기 모델·CPU·RAM·저장공간 진단은 Android 시스템에서 " +
            "공개하는 정보로 수행하며 별도 위치 권한은 요청하지 않습니다. " +
            "일부 GPU 사용률·NPU 정보는 제조사 제한으로 조회할 수 없습니다. " +
            "Wi-Fi 세션 검색에는 네트워크·멀티캐스트 권한을 사용합니다.",
            12,MUTED,false),content,0,7);
        Button notification=button("알림 권한 확인 / 요청",false);
        margin(notification,content,0,14);
        notification.setOnClickListener(v->{
            if(Build.VERSION.SDK_INT<33){
                toast("Android 12 이하: 알림 권한 별도 요청 불필요");return;
            }
            if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    ==PackageManager.PERMISSION_GRANTED){
                toast("알림 권한이 허용되어 있습니다.");return;
            }
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},3);
        });

        margin(text("세션 연결 진단",18,WHITE,true),content,0,6);
        connectionSessionStatus=text("협력 세션 상태 확인 중",13,WHITE,false);
        connectionSessionStatus.setPadding(dp(14),dp(12),dp(14),dp(12));
        connectionSessionStatus.setBackground(bg(CARD,14));
        margin(connectionSessionStatus,content,0,8);
        Button coop=button("호스트 세션 생성 · 주변 기기 참가",true);
        margin(coop,content,0,9);
        coop.setOnClickListener(v->CoopUi.show(this));
        nodeRows=column();margin(nodeRows,content,0,10);

        margin(text("성능 및 실제 추론 테스트",18,WHITE,true),content,5,7);
        Button benchmark=button("모델 최적화 벤치마크 · tok/s",true);
        margin(benchmark,content,0,7);
        benchmark.setOnClickListener(v->{
            if(isUiBusy()){toast("진행 중인 작업이 끝난 뒤 측정하세요");return;}
            ModelBenchmark.show(this,currentModel,
                ()->benchmarkBusy=true,()->{
                    benchmarkBusy=false;
                    renderInstalledModels();
                    if(activeTab==3)refreshDiagnosticViews();
                });
        });
        Button test=button("CPU · Vulkan GPU 실측 검사",false);
        margin(test,content,0,6);
        diagnosticStatus=text("설치 모델을 CPU/GPU에서 실행해 추론 성공 여부를 확인합니다.",
            12,MUTED,false);
        margin(diagnosticStatus,content,0,10);
        test.setOnClickListener(v->beginHardwareSelfTest());

        margin(text("엔진 실행 로그",16,WHITE,true),content,0,6);
        logText=text("로그 로딩 중",11,MUTED,false);
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
            if(cap==null)return "네트워크: 연결 확인 불가 · 세션 검색을 위해 Wi-Fi 연결 필요";
            String kind=cap.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    ?"Wi-Fi":cap.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                    ?"모바일 데이터":"기타 네트워크";
            boolean internet=cap.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            String ip=CoopSession.localIPv4();
            return "네트워크: "+kind+(internet?" · 인터넷 연결 확인":" · 인터넷 확인 불가")
                    +(ip.isEmpty()?"":"\n로컬 IPv4: "+ip);
        }catch(Exception ex){return "네트워크: 시스템 정보 접근 제한";}
    }

    private void refreshDiagnosticViews(){
        if(deviceSummary==null||computeSummary==null||connectionSessionStatus==null)return;
        deviceSummary.setText(deviceProfile.description()+
            (lastHud==null?"":"\n\n실시간: "+lastHud.detail()));
        computeSummary.setText(computeReport.description());
        if(suitabilitySummary!=null)suitabilitySummary.setText(suitabilityOverview());
        if(localConnectionStatus!=null)localConnectionStatus.setText(networkDiagnostics());
        CoopSession session=CoopSession.get(this);
        connectionSessionStatus.setText(session.status());
        if(nodeRows==null)return;
        nodeRows.removeAllViews();
        if("host".equals(session.role())&&!session.nodes().isEmpty()){
            for(CoopSession.Node node:session.nodes()){
                long age=Math.max(0,(android.os.SystemClock.elapsedRealtime()-node.lastSeen)/1000);
                String state=(age<30&&node.workerOk)?"연결 정상":"연결 상태 확인 필요";
                String info=node.name+"  ·  "+state+
                    "\n기기: "+node.deviceModel+" · Android API "+node.androidApi+
                    "\n칩셋: "+node.chip+" · "+node.cpuCores+"코어 · "+node.abi+
                    "\nRAM "+node.totalRamMiB+" MiB / 안전 사용 가능 "+node.safeRamMiB+
                    " MiB / 저장공간 여유 "+node.freeStorageMiB+" MiB"+
                    "\nCPU "+(node.cpuPermille<0?"조회 불가":(node.cpuPermille/10.0)+"%")+
                    " · GPU "+(node.gpuPermille<0?"조회 불가":(node.gpuPermille/10.0)+"%")+
                    " · Vulkan "+(node.vulkanCompute?"감지":"미확인")+
                    " · NPU 후보 "+node.nnapiAccelerators+"개"+
                    "\n배터리 "+(node.batteryTenths<0?"조회 불가":(node.batteryTenths/10.0)+"℃")+
                    " · 열 상태 "+(node.thermalStatus<0?"조회 불가":node.thermalStatus)+
                    " · RPC "+(node.workerOk?"실행":"중지")+
                    "\n마지막 보고 "+age+"초 전";
                TextView card=text(info,12,WHITE,false);
                card.setPadding(dp(14),dp(12),dp(14),dp(12));
                card.setBackground(bg(CARD,14));margin(card,nodeRows,0,8);
            }
        }else if("guest".equals(session.role())){
            String summary=session.peerDiagnostics();
            TextView text=text(summary,12,WHITE,false);
            text.setPadding(dp(14),dp(12),dp(14),dp(12));
            text.setBackground(bg(CARD,14));margin(text,nodeRows,0,7);
        }else{
            TextView none=text("참가 중인 보조 기기가 없습니다.\n동일 Wi-Fi에서 세션을 만들거나 참가하세요.",
                    13,MUTED,false);
            none.setPadding(dp(14),dp(14),dp(14),dp(14));
            none.setBackground(bg(CARD,14));margin(none,nodeRows,0,5);
        }
    }

    private void beginHardwareSelfTest() {
        if (diagnosticBusy || chatBusy || pullBusy || benchmarkBusy) {
            toast("다른 작업이 끝난 뒤 검사하세요"); return;
        }
        if (currentModel == null || currentModel.isEmpty() || knownModels.isEmpty()) {
            toast("모델을 먼저 설치한 뒤 검사하세요"); showTab(1); return;
        }
        if (!OllamaForegroundService.isRunning()) {
            toast("로컬 Ollama 서버가 실행 중이어야 합니다"); return;
        }
        new AlertDialog.Builder(this)
            .setTitle("실제 추론 확인")
            .setMessage("모델: " + currentModel + "\n\n기기에서 CPU 응답을 생성하고 GPU가 지원되면 " +
                "Vulkan 오프로드와 토큰 생성까지 확인합니다. 발열과 배터리 사용량이 증가할 수 있습니다. " +
                "새 모델은 다운로드하지 않으며 NPU 실행은 현재 지원하지 않습니다.")
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton("검사 시작", (d,which) -> {
                diagnosticBusy = true;
                diagnosticStatus.setText("실측 검사 시작...");
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
                        computed = "검사 실행 오류: " + e.getClass().getSimpleName() + " · " + e.getMessage();
                    }
                    final String result = computed;
                    ui.post(() -> {
                        diagnosticBusy = false;
                        if (isFinishing()) return;
                        diagnosticStatus.setText("검사가 완료됐습니다. 보고서를 확인하세요.");
                        new AlertDialog.Builder(this)
                            .setTitle("기기 내 CPU/GPU/NPU 진단 결과")
                            .setMessage(result)
                            .setNegativeButton(getString(R.string.close), null)
                            .setPositiveButton("결과 복사", (dialog, action) -> {
                                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                                clipboard.setPrimaryClip(ClipData.newPlainText("On Hands AI 진단 결과", result));
                                toast("진단 결과 복사됨");
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
        String gpuInfo = !GpuRunner.packaged(this) ? "GPU · 실행 엔진이 APK에 없음" :
            (computeReport.gpuCompute ? "GPU · Vulkan 실행 가능 여부 시도" : "GPU · Vulkan compute 감지되지 않음");
        String npuInfo = computeReport.nnapiAccelerators > 0 ?
            "NPU · 가속기 후보 감지 (모델 백엔드 미지원)" :
            "NPU · 지원되는 추론 백엔드 없음";
        String[] options = {"자동 · GPU 확인 후 CPU 대체", "CPU · Ollama 실행", gpuInfo, npuInfo};
        new AlertDialog.Builder(this).setTitle("추론 장치 선택")
            .setMessage("연산 장치를 감지한 뒤 실제 추론 가능 여부를 확인합니다. " +
                "GPU는 Vulkan runner 탑재·오프로드 확인 조건을 충족해야 사용하며, " +
                "NPU는 전용 제조사 백엔드가 없어 선택할 수 없습니다.")
            .setSingleChoiceItems(options,
                "cpu".equals(computePreference()) ? 1 : "gpu".equals(computePreference()) ? 2 : 0,
                (dialog, which) -> {
                    if (chatBusy) { toast("답변 생성 중에는 변경할 수 없습니다"); return; }
                    if (which == 3) {
                        toast("NPU용 GGUF 추론 백엔드가 없습니다"); return;
                    }
                    if (which == 2 && (!computeReport.gpuCompute || !GpuRunner.packaged(this))) {
                        toast("이 기기/APK에서는 Vulkan GPU 실행을 확인할 수 없습니다"); return;
                    }
                    String pref = which == 1 ? "cpu" : which == 2 ? "gpu" : "auto";
                    getPreferences(MODE_PRIVATE).edit().putString("compute_preference", pref).apply();
                    if (which == 1) GpuRunner.stop();
                    if (activeEngineLabel != null)
                        activeEngineLabel.setText("선택한 모드: " + pref + " · 다음 답변부터 적용");
                    toast("연산 모드 저장 · 다음 답변에서 확인");
                    dialog.dismiss();
                }).setNegativeButton(getString(R.string.cancel), null).show();
    }

    private boolean animationsEnabled() {
        return smoothMotion && ValueAnimator.areAnimatorsEnabled();
    }

    private void showTab(int index) {
        int target=Math.max(0,Math.min(3,index));
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
        if(topPageTitle!=null)topPageTitle.setText("On Hands AI");
        if(topPageSubtitle!=null){
            int[] subtitles={R.string.chat,R.string.models,R.string.settings,R.string.devices};
            topPageSubtitle.setText(getString(subtitles[activeTab])+"  ·  "+getString(R.string.local));
        }
        if(activeTab==1)loadRecommendedVariants(false);
        if(activeTab==3)refreshDiagnosticViews();
    }

    private void showFirstRunGuide() {
        LinearLayout guide = column(); guide.setPadding(dp(20), dp(12), dp(20), dp(5));
        margin(text(getString(R.string.onboarding_title), 20, WHITE, true), guide, 0, 8);
        margin(text(getString(R.string.onboarding_body),13,WHITE,false),guide,0,11);
        // Keep existing ad code but exclude first-run banner in the candidate.
        FrameLayout guideAd = new FrameLayout(this);
        guideAd.setVisibility(View.GONE); // First-run flow stays ad-free.
        AlertDialog dialog = new AlertDialog.Builder(this).setView(guide)
            .setNegativeButton(R.string.chat, (d,w) -> showTab(0))
            .setNeutralButton(R.string.guest, (d,w) -> enterGuestMode())
            .setPositiveButton(R.string.install_model, (d,w) -> showTab(1)).create();
        dialog.setOnDismissListener(d -> {
            getPreferences(MODE_PRIVATE).edit().putBoolean("welcome_v2_seen", true).apply();
            ads.detach(guideAd);
        });
        dialog.show();
        ads.prepareConsent();
    }

    private void startServer() {
        Intent intent = new Intent(this, OllamaForegroundService.class).setAction(OllamaForegroundService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            toast("로컬 Ollama 실행 요청");
        } catch (Exception e) { toast("서버 시작 실패: " + e.getMessage()); }
    }
    private void stopServer() {
        if (chatBusy) stopChat();
        GpuRunner.stop();
        if (pullBusy) { pullCancelled = true; if (pullConnection != null) pullConnection.disconnect(); }
        Intent intent = new Intent(this, OllamaForegroundService.class).setAction(OllamaForegroundService.ACTION_STOP);
        startService(intent);
    }
    private static String installedSize(long bytes) {
        if(bytes<=0)return "용량 미확인";
        return String.format(Locale.KOREA,"%.2f GB",bytes/1_000_000_000.0);
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
            ?java.util.Collections.singletonList("설치된 모델 없음"):names;
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
        if(!names.isEmpty()&&idx<0){
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
            catch(Exception ex){error=ex.getMessage()==null?"온라인 확인 실패":ex.getMessage();}
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

    private String suitabilityOverview(){
        StringBuilder display=new StringBuilder();
        display.append("기기: ").append(deviceProfile.device)
           .append("  ·  RAM ").append(String.format(Locale.KOREA,"%.1f",deviceProfile.ramGb))
           .append("GB · CPU ").append(deviceProfile.cores).append("코어\n");
        if(!currentModel.isEmpty()&&modelBytes.containsKey(currentModel)){
            long weight=modelBytes.getOrDefault(currentModel,0L);
            ModelPerformanceAdvisor.Result current=advise(currentModel,
                weight/(1024.0*1024.0*1024.0));
            display.append("\n현재 모델: ").append(currentModel).append("\n")
                .append(current.summary()).append("\n");
        }else display.append("\n설치된 모델을 선택하면 적합도가 표시됩니다.\n");
        display.append("\n추천 후보 (CPU 단독 기준):");
        int shown=0;
        for(DeviceProfiler.Recommendation recommended:DeviceProfiler.recommend(deviceProfile)){
            if(!recommended.selectable)continue;
            ModelPerformanceAdvisor.Result score=advise(recommended.model,
                recommended.downloadGb*1_000_000_000/(1024.0*1024.0*1024.0));
            display.append("\n • ").append(recommended.model).append(" · ")
                .append(score.suitability).append(" · ").append(score.throughput);
            if(++shown>=3)break;
        }
        if(shown==0)display.append("\n추천 가능한 모델이 없습니다.");
        display.append("\n\n기기 스펙만으로 실제 tok/s를 정확히 알 수 없습니다."+
            " 실측 전 숫자는 표시하지 않거나 비교 측정 기반 참고 추정치로 표기합니다.");
        return display.toString();
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
        modelListViewButton.setContentDescription("리스트형 보기"+(modelListView?" · 선택됨":""));
        modelGridViewButton.setContentDescription("카드형 보기"+(!modelListView?" · 선택됨":""));
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
        if(tile.id.equals(downloadingCardId)&&pullBusy){toast("다운로드 중");return;}
        if(isUiBusy()){toast("진행 중인 추론·다운로드·벤치마크를 마친 뒤 변경하세요");return;}
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
                labels[i]=v.label()+"\n"+v.quant+" · "+(v.bytes>0?installedSize(v.bytes):"용량 미확인")+
                    (best.get(v.group())==v?" (추천)":"")+(knownModels.contains(v.model)?" · 설치됨":"");
            }
            new AlertDialog.Builder(this).setTitle("크기·양자화 버전 선택")
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
        return String.format(Locale.KOREA," · 실측 %.1f tok/s%s",
            speed,gpu!=null&&(cpu==null||gpu>cpu)?" GPU":" CPU");
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
        card.setContentDescription(item.title+", "+item.id+", "+
            ModelTileCatalog.status(item,downloadingCardId)+", "+item.description+
            ". 터치하여 양자화 버전 선택");
        TypedValue ripple=new TypedValue();
        if(getTheme().resolveAttribute(android.R.attr.selectableItemBackground,ripple,true)
                &&ripple.resourceId!=0)
            card.setForeground(getDrawable(ripple.resourceId));
        tilePressEffect(card);

        TextView statusText=text(
            (item.id.equals(downloadingCardId)&&pullBusy)?"↓ 다운로드 중":
            item.installed?(selected?"● 사용 중":"● 설치됨"):"○ 미설치",
            10,ink,true);
        statusText.setSingleLine(true);
        statusText.setEllipsize(TextUtils.TruncateAt.END);
        statusText.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        LinearLayout statusRow=row();
        rowItem(statusRow,statusText,1,3);
        if(item.featured){
            TextView recommendation=text("(추천)",10,secondary,true);
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

        String suffix=item.bytes>0?installedSize(item.bytes):"용량 미확인";
        String provider=remote!=null?remote.source:item.installed?"내 기기":"Ollama";
        TextView details=text(item.quantization+" · "+suffix+"\n"+
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

        TextView hint=text("양자화 버전 선택 ›",11,secondary,false);
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
        card.setContentDescription(item.title+", "+item.id+", "+item.quantization+", "+
            ModelTileCatalog.status(item,downloadingCardId)+", "+
            (item.featured?"기기 추천, ":"")+"터치하여 양자화 버전 선택");
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
        String provider=remote!=null?remote.source:item.installed?"내 기기":"Ollama";
        TextView details=text(item.quantization+"  ·  "+
            (item.bytes>0?installedSize(item.bytes):"용량 미확인")+"  ·  "+provider+
            benchmarkLabel(item.id),11,secondary,false);
        details.setSingleLine(true);
        details.setEllipsize(TextUtils.TruncateAt.END);
        margin(details,body,0,0);
        rowItem(line,body,1,8);

        LinearLayout trailing=column();
        trailing.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        TextView status=text(item.id.equals(downloadingCardId)&&pullBusy?"↓ 다운로드 중":
            item.installed?(selected?"● 사용 중":"● 설치됨"):"○ 미설치",
            10,ink,true);
        status.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        status.setSingleLine(true);
        margin(status,trailing,0,5);
        if(item.featured){
            TextView recommended=text("(추천)",11,secondary,true);
            recommended.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            recommended.setSingleLine(true);
            margin(recommended,trailing,0,5);
        }
        TextView more=text("선택 ›",12,secondary,false);
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
            status.setText(downloadingPercent<0?"↓ 다운로드 준비 중":
                "↓ 다운로드 중 · 레이어 "+downloadingPercent+"%");
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
            error=provider+": "+(ex.getMessage()==null?"연결 오류":ex.getMessage());
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
            modelSearchStatus.setText(recommendationLoading?"실제 등록 양자화·용량 확인 중…":
                !recommendationError.isEmpty()?"온라인 추천 조회 실패 · 새로고침으로 재시도":
                "추천은 RAM·저장공간·CPU 기준 추정이며 실제 실행은 벤치마크에서 확인하세요.");
        }else if(query.length()<2)modelSearchStatus.setText("두 글자 이상 입력하면 자동 검색합니다.");
        else if(query.length()>120)modelSearchStatus.setText("검색어는 120자 이내로 입력하세요.");
        else if(onlineSearchPending)modelSearchStatus.setText(filterDisplay(selectedSource)+" · "+onlineVariants.size()+
            "개 우선 표시 · 나머지 검색 중… (최근 검색 10분 캐시)");
        else modelSearchStatus.setText("온라인 양자화 버전 "+onlineVariants.size()+"개 · "+
            (onlineSearchError.isEmpty()?"용량 미확인 항목은 정렬 마지막에 표시":onlineSearchError+" · 검색 버튼으로 재시도"));
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
            String metadata=items.size()+"개 양자화 · "+best.quantization+" · "+
                (best.bytes>0?installedSize(best.bytes):"용량 미확인")+" · "+source;
            if(items.stream().anyMatch(t->t.id.equals(downloadingCardId)&&pullBusy))
                metadata+=" · 다운로드 중";
            TextView info=text(metadata,11,MUTED,false);
            info.setSingleLine(true);info.setEllipsize(TextUtils.TruncateAt.END);
            margin(info,details,0,0);
            rowItem(heading,details,1,6);
            TextView arrow=text(opened?"⌄":"›",22,MUTED,false);
            arrow.setGravity(Gravity.CENTER);
            rowItem(heading,arrow,0,0);
            heading.setContentDescription(best.title+", 양자화 "+items.size()+
                "개, "+(opened?"접기":"펼치기"));
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
                "검색 결과 "+shown.size()+"개 버전 · "+selectedSort:
                "추천 양자화 버전 · "+shown.size()+"개");
        if(shown.isEmpty()){
            String emptyMessage=searching?
                (onlineSearchPending?"Ollama 온라인 모델을 검색 중입니다…":
                (filter.length()<2?"설치된 일치 모델이 없습니다. 두 글자 이상 입력하세요.":
                "조건에 맞는 모델이 없습니다. 다른 검색어를 시도하세요.")):
                (all.isEmpty()?
                "현재 RAM·저장공간 조건에서 안전하게 추천할 모델이 없습니다.\n진단 탭에서 기기 상태를 확인하세요.":
                "추천 모델이 없습니다. 필터 조건을 조정하세요.");
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
            toast("설치된 모델이 없습니다. 추천 카드를 눌러 다운로드하세요.");
            return;
        }
        String[] entries=knownModels.toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setTitle("설치된 모델 선택")
            .setItems(entries,(dialog,index)->{
                if(chatBusy||benchmarkBusy||diagnosticBusy){
                    toast("실행 중인 작업을 마친 뒤 모델을 바꾸세요");return;
                }
                selectModel(entries[index]);
                showTab(0);
            })
            .setNegativeButton(getString(R.string.close),null).show();
    }

    private void selectModel(String name) {
        if(!knownModels.contains(name))return;
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
            toast("모델명을 확인하세요"); return;
        }
        if (pullBusy || benchmarkBusy || HuggingFaceCatalog.isDownloading()) {
            toast("다른 모델 다운로드 또는 벤치마크 종료 후 이용하세요");return;
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
            new AlertDialog.Builder(this).setTitle("저장공간 부족")
                .setMessage("필요 공간(여유분 포함): "+installedSize(reserved)+
                    "\n현재 여유 공간: "+installedSize(availableBytes)+
                    "\n다른 모델·파일을 정리한 뒤 다시 시도하세요.")
                .setPositiveButton("확인",null).show();
            return;
        }
        String storageHint=featuredBytes>0 ?
            ("모델 크기: "+installedSize(featuredBytes)+" · 권장 여유 "+installedSize(reserved)+"\n"):
            "모델 크기 미확인 · 저장공간을 직접 확인하세요.\n";
        String memoryHint=featuredBytes>0 ?
            ("RAM 적합도: "+advise(model,featuredBytes/(1024.0*1024.0*1024.0))
                .suitability+" (사전 추정)\n\n"):"\n";
        new AlertDialog.Builder(this)
            .setTitle("다운로드 · 데이터 요금 안내")
            .setMessage("선택 모델: " + model + "\n"+storageHint+memoryHint+
                "모델 다운로드는 수백 MB~수십 GB의 인터넷 데이터를 사용할 수 있습니다. " +
                "Wi-Fi나 모바일 데이터 요금제에 따라 통신료가 발생할 수 있습니다. " +
                "저장공간과 인터넷 연결을 확인하세요.\n\n" +
                "다운로드 중 표시되는 광고에도 인터넷 데이터가 사용될 수 있습니다.")
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton("확인 후 다운로드", (d,w) -> beginDownload(model))
            .show();
    }

    private void beginDownload(String model) {
        if (pullBusy) return;
        if (!OllamaForegroundService.isRunning()) {
            toast("로컬 서버 시작 중입니다. 연결되면 다시 다운로드하세요.");
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
        progressStatus.setText(model + " 다운로드 준비 중…");
        io.execute(() -> {
            String error = null;
            try {
                HttpURLConnection connection = OllamaHttp.newPullConnection(); pullConnection = connection;
                OllamaHttp.stream(connection, OllamaHttp.pullBody(model), item -> {
                    String status = item.optString("status", "진행 중");
                    long total = item.optLong("total", 0), completed = item.optLong("completed", 0);
                    String info=model+" · "+ModelDownloadBudget.layerLabel(status,completed,total);
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
                progressStatus.setText(pullCancelled ? "다운로드 중단" : failure != null ?
                    "다운로드 실패: " + failure : "모델 설치 완료: " + model);
                lastFailedPullModel=!pullCancelled&&failure!=null?model:"";
                if(downloadRetryButton!=null)
                    downloadRetryButton.setVisibility(lastFailedPullModel.isEmpty()?View.GONE:View.VISIBLE);
                downloadProgress.setIndeterminate(false);
                if (failure == null && !pullCancelled) downloadProgress.setProgress(100);
                else downloadProgress.setVisibility(View.GONE);
                if (failure == null && !pullCancelled) {
                    refreshModels();
                    new AlertDialog.Builder(this).setTitle("모델 준비 완료")
                        .setMessage(model + "이 설치됐습니다. 이제 이 기기에서 질문할 수 있습니다.")
                        .setNegativeButton("모델 화면 유지", null)
                        .setPositiveButton("채팅 시작", (d,w) -> showTab(0)).show();
                }
            });
        });
    }

    private String sessionKey() { return "active_chat_" + currentModel; }
    private String currentSessionId() {
        String id = getPreferences(MODE_PRIVATE).getString(sessionKey(), "");
        if (id.isEmpty()) {
            id = Long.toString(System.currentTimeMillis());
            getPreferences(MODE_PRIVATE).edit().putString(sessionKey(), id).apply();
        }
        return id;
    }
    private String listKey() { return "chat_list_" + currentModel; }
    private String historyKey() { return "chat_v4_" + currentModel + "_" + currentSessionId(); }
    private void newChat() {
        if (chatBusy) { toast(getString(R.string.new_chat_busy)); return; }
        if(voice!=null)voice.cancel();
        getPreferences(MODE_PRIVATE).edit().putString(sessionKey(), Long.toString(System.currentTimeMillis())).apply();
        history = new JSONArray();
        renderHistory();
        showTab(0);
    }
    private static final class DrawerChat {
        final String model, id, title;
        final long created;
        DrawerChat(String model,String id,String title) {
            this.model=model;this.id=id;this.title=title;
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
                    String label=obj.optString("title","대화");
                    result.add(new DrawerChat(model,id,label));
                }
            }catch(Exception ignored){}
        }
        Collections.sort(result,(a,b)->Long.compare(b.created,a.created));
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
        if(chatBusy||benchmarkBusy||diagnosticBusy){
            toast("진행 중인 응답이나 검사를 마친 뒤 대화를 바꾸세요");
            return;
        }
        if(!knownModels.contains(entry.model)){
            drawer.dismiss();
            toast("이 대화의 모델이 현재 설치되어 있지 않습니다: "+entry.model);
            showTab(1);
            return;
        }
        if(!entry.model.equals(currentModel))selectModel(entry.model);
        getPreferences(MODE_PRIVATE).edit()
            .putString("active_chat_"+entry.model,entry.id).apply();
        loadHistory();renderHistory();
        drawer.dismiss();
        showTab(0);
    }

    private void renderDrawerChats(LinearLayout list,Dialog drawer,
                                   List<DrawerChat> conversations,String query){
        list.removeAllViews();
        String search=query==null?"":query.toLowerCase(Locale.ROOT).trim();
        int count=0;
        for(DrawerChat entry:conversations){
            if(!search.isEmpty() &&
               !entry.title.toLowerCase(Locale.ROOT).contains(search) &&
               !entry.model.toLowerCase(Locale.ROOT).contains(search))continue;
            if(count++>=120)break;
            boolean active=entry.model.equals(currentModel)
                &&entry.id.equals(getPreferences(MODE_PRIVATE)
                    .getString("active_chat_"+entry.model,""));
            TextView item=drawerRow(entry.title,"",active);
            item.setContentDescription(entry.title+" · 모델 "+entry.model);
            LinearLayout.LayoutParams p=lp(-1,-2);
            p.bottomMargin=dp(3);
            list.addView(item,p);
            item.setOnClickListener(v->openSavedConversation(entry,drawer));
        }
        if(count==0){
            TextView empty=text(search.isEmpty()?
                "저장된 대화가 없습니다.\n위의 '새 대화'로 시작하세요.":
                "검색 결과가 없습니다.",13,MUTED,false);
            empty.setPadding(dp(13),dp(19),dp(10),dp(12));
            list.addView(empty,lp(-1,-2));
        }
    }

    private TextView drawerNavigationItem(String title){
        // The lower sidebar displays only these three titles—no subtitles or icons.
        boolean active=("모델 선택".equals(title)&&activeTab==1)
            ||("설정".equals(title)&&activeTab==2)
            ||("기기 연결 진단".equals(title)&&activeTab==3);
        TextView item=text(title,14,WHITE,active);
        item.setSingleLine(true);
        item.setEllipsize(TextUtils.TruncateAt.END);
        item.setMinHeight(dp(42));
        item.setPadding(dp(12),dp(7),dp(10),dp(7));
        item.setBackground(bg(active?FIELD:BG,10));
        item.setContentDescription(title+(active?" · 선택됨":""));
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
        TextView product=text("On Hands AI",17,WHITE,true);
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
            if(chatBusy){toast("생성 중에는 새 대화를 시작할 수 없습니다");return;}
            drawer.dismiss();
            newChat();
        });

        EditText search=edit(getString(R.string.search_chats),"",false);
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
            for (int i = 0; i < Math.min(39, sessions.length()); i++) newer.put(sessions.getJSONObject(i));
            getPreferences(MODE_PRIVATE).edit().putString(listKey(), newer.toString()).apply();
        } catch (Exception ignored) { }
    }
    private void loadHistory() {
        if (!getPreferences(MODE_PRIVATE).contains(historyKey())) {
            String legacy = getPreferences(MODE_PRIVATE).getString("chat_" + currentModel, "[]");
            if (!"[]".equals(legacy)) {
                getPreferences(MODE_PRIVATE).edit().putString(historyKey(), legacy).apply();
                try {
                    JSONArray previous = new JSONArray(legacy);
                    if (previous.length() > 0 && previous.optJSONObject(0) != null)
                        ensureSessionListed(previous.optJSONObject(0).optString("content", "이전 대화"));
                } catch (Exception ignored) { }
            }
        }
        try { history = new JSONArray(getPreferences(MODE_PRIVATE).getString(historyKey(), "[]")); }
        catch (Exception ignored) { history = new JSONArray(); }
    }
    private void storeHistory() {
        if (history.length() > 32) {
            JSONArray trimmed = new JSONArray();
            for (int i = history.length() - 32; i < history.length(); i++) trimmed.put(history.opt(i));
            history = trimmed;
        }
        getPreferences(MODE_PRIVATE).edit().putString(historyKey(), history.toString()).apply();
    }
    private void addHistory(String role, String content) {
        if ("user".equals(role) && history.length() == 0) ensureSessionListed(content);
        try { history.put(new JSONObject().put("role", role).put("content", content)); storeHistory(); }
        catch (Exception ignored) { }
    }
    private void renderHistory() {
        if (messages == null) return;
        messages.removeAllViews();
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
            if (item != null) bubble(item.optString("role"), item.optString("content"));
        }
        scrollBottom();
    }
    private TextView bubble(String role,String content){return bubble(role,content,false);}
    private TextView bubble(String role,String content,boolean pending) {
        boolean user="user".equals(role);
        TextView t=text(user?content:"",15,WHITE,false);
        t.setTextIsSelectable(true);t.setLineSpacing(dp(3),1f);
        t.setPadding(dp(12),dp(12),dp(12),dp(12));
        t.setAutoLinkMask(0);
        LinearLayout wrapper=column();
        wrapper.setGravity(user?Gravity.RIGHT:Gravity.LEFT);
        if(user){
            t.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels*0.82));
            t.setBackground(bg(BUBBLE,17));
            wrapper.addView(t,lp(-2,-2));
        }else{
            t.setBackgroundColor(BG);
            wrapper.addView(t,lp(-1,-2));
            LinearLayout actions=row();
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
            actions.setVisibility(pending?View.GONE:View.VISIBLE);
            if(pending)t.setText(getString(R.string.app_name)+"\n"+content);
            else showAssistant(t,content,"",true);
        }
        margin(wrapper,messages,5,9);
        return t;
    }
    private void showAssistant(TextView t,String raw,String suffix,boolean actionable){
        t.setText(ChatPresentation.format(getString(R.string.app_name)+"\n",raw,suffix,FIELD));
        t.setTag(actionable?raw:null);
        if(t.getParent() instanceof LinearLayout){
            LinearLayout wrapper=(LinearLayout)t.getParent();
            if(wrapper.getChildCount()>1)wrapper.getChildAt(1).setVisibility(actionable?View.VISIBLE:View.GONE);
        }
    }
    private boolean followsLatest(){
        if(messageScroll==null||messageScroll.getChildCount()==0)return true;
        View content=messageScroll.getChildAt(0);
        return content.getBottom()-messageScroll.getHeight()-messageScroll.getScrollY()<=dp(96);
    }
    private void scrollBottom() { if(messageScroll!=null)messageScroll.post(()->messageScroll.fullScroll(View.FOCUS_DOWN)); }
    private void setChatBusy(boolean busy) {
        chatBusy = busy;
        sendButton.setEnabled(!busy);sendButton.setVisibility(busy?View.GONE:View.VISIBLE);
        stopChatButton.setEnabled(busy);stopChatButton.setVisibility(busy?View.VISIBLE:View.GONE);
        if(cloudShortcut!=null)cloudShortcut.setEnabled(!busy);
        modelChooser.setEnabled(!busy && !knownModels.isEmpty());
        if(voiceButton!=null)voiceButton.setEnabled(!busy);
    }
    private void sendChat() {
        String prompt = chatInput.getText().toString().trim();
        if (prompt.isEmpty()) return;
        if (currentModel.isEmpty() || knownModels.isEmpty()) { toast(getString(R.string.need_model)); showTab(1); return; }
        if (chatBusy || benchmarkBusy) {
            if(benchmarkBusy)toast(getString(R.string.benchmark_wait));
            return;
        }
        final boolean speakThisTurn=speakNextAnswer;
        speakNextAnswer=false;
        if(voice!=null)voice.cancel();
        // Clear the input first, then append to the selected model's history.
        chatInput.setText("");
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(chatInput.getWindowToken(), 0);
        if(history.length()==0)messages.removeAllViews(); // Remove empty-state cards on first send.
        addHistory("user", prompt);
        bubble("user", prompt);
        TextView assistant = bubble("assistant", getString(R.string.thinking),true);
        lastStreamUi=0;
        setChatBusy(true); chatCancelled = false;
        scrollBottom();
        final String modelAtStart = currentModel;
        final JSONArray requestHistory;
        try { requestHistory = new JSONArray(history.toString()); }
        catch (Exception ex) { setChatBusy(false); return; }
        io.execute(() -> {
            final StringBuilder answer = new StringBuilder();
            String error = null;
            try {
                if ("cooperative".equals(computePreference())) {
                    StringBuilder reason = new StringBuilder();
                    ui.post(() -> activeEngineLabel.setText("협력 RPC 모델 로딩 · 보조 기기로 데이터 전송 중"));
                    if (!DistributedRunner.ensure(getApplicationContext(), modelAtStart, reason))
                        throw new java.io.IOException("협력 모델 실행 실패: " + reason +
                            " · 보조 노드가 끊어졌다면 작은 단독 모델로 변경하세요.");
                    if (chatCancelled) {
                        ui.post(() -> { showAssistant(assistant,getString(R.string.stopped),"",false); setChatBusy(false); });
                        return;
                    }
                    HttpURLConnection connection = OllamaHttp.newCooperativeChatConnection();
                    chatConnection = connection;
                    ui.post(() -> activeEngineLabel.setText("협력 RPC 경로 연결 · 실제 토큰 생성 확인 중"));
                    OllamaHttp.streamGpu(connection, OllamaHttp.gpuChatBody(modelAtStart, requestHistory),
                        item -> appendAnswer(item, answer, assistant));
                } else {
                String choice = computePreference();
                boolean tryGpu = !"cpu".equals(choice) && computeReport.gpuCompute &&
                    GpuRunner.packaged(getApplicationContext());
                boolean gpu = false;
                StringBuilder fallback = new StringBuilder();
                if (tryGpu && !chatCancelled) {
                    ui.post(() -> activeEngineLabel.setText("GPU Vulkan 실행 검증 중…"));
                    // Release the CPU model before allocating the second GPU runtime.
                    try {
                        OllamaHttp.post("/api/generate", new JSONObject()
                            .put("model", modelAtStart).put("keep_alive", 0)
                            .put("stream", false), "POST");
                    } catch (Exception ignored) { }
                    gpu = GpuRunner.ensure(getApplicationContext(), modelAtStart, fallback);
                }
                if (chatCancelled) {
                    ui.post(() -> { showAssistant(assistant,getString(R.string.stopped),"",false); setChatBusy(false); });
                    return;
                }
                final boolean verifiedGpu = gpu;
                final String failedReason = fallback.toString();
                ui.post(() -> activeEngineLabel.setText(verifiedGpu ?
                    "실제 연산: GPU · Vulkan 레이어 오프로드 검증" :
                    "실제 연산: CPU · Ollama" + (failedReason.isEmpty() ? "" : " (GPU 전환 실패)")));
                if (gpu) {
                    try {
                        HttpURLConnection connection = OllamaHttp.newGpuChatConnection();
                        chatConnection = connection;
                        OllamaHttp.streamGpu(connection, OllamaHttp.gpuChatBody(modelAtStart, requestHistory),
                            item -> appendAnswer(item, answer, assistant));
                    } catch (Exception gpuError) {
                        if (answer.length() > 0) throw gpuError; // never duplicate partial responses
                        ui.post(() -> activeEngineLabel.setText("GPU 추론 실패 · CPU Ollama로 전환"));
                        HttpURLConnection connection = OllamaHttp.newChatConnection();
                        chatConnection = connection;
                        OllamaHttp.stream(connection, OllamaHttp.chatBody(modelAtStart, requestHistory),
                            item -> appendAnswer(item, answer, assistant));
                    }
                } else {
                    HttpURLConnection connection = OllamaHttp.newChatConnection();
                    chatConnection = connection;
                    OllamaHttp.stream(connection, OllamaHttp.chatBody(modelAtStart, requestHistory),
                        item -> appendAnswer(item, answer, assistant));
                }
                }
            } catch (Exception ex) { error = ex.getMessage(); }
            finally { chatConnection = null; }
            String finalError = error, finalAnswer = answer.toString();
            ui.post(() -> {
                if(isFinishing()||isDestroyed())return;
                boolean follow=followsLatest();
                if (!finalAnswer.isEmpty()) {
                    showAssistant(assistant,finalAnswer,chatCancelled?"\n\n"+getString(R.string.generation_cancelled):finalError!=null?"\n\n"+getString(R.string.error_label)+finalError:"",true);
                    // Keep incomplete responses visible but out of next turn's request context.
                    if (!chatCancelled && finalError == null && modelAtStart.equals(currentModel))
                        addHistory("assistant", finalAnswer);
                } else {
                    showAssistant(assistant,chatCancelled?getString(R.string.stopped):getString(R.string.error_label)+finalError,"",false);
                }
                setChatBusy(false);
                if(speakThisTurn&&foreground&&!isDestroyed()&&!chatCancelled&&finalError==null&&!finalAnswer.isEmpty()&&voice!=null)voice.speak(finalAnswer);
                if(follow)scrollBottom();
            });
        });
    }
    private void appendAnswer(JSONObject item, StringBuilder answer, TextView assistant) {
        JSONObject message = item.optJSONObject("message");
        if (message == null) return;
        String part = message.optString("content", "");
        if (part.isEmpty()) return;
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

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
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
            toast("GGUF 파일을 선택하세요"); return;
        }
        final String modelName = "imported-" + System.currentTimeMillis();
        progressStatus.setText("GGUF 가져오는 중 · 화면을 유지하세요");
        io.execute(() -> {
            File copied = null, modelfile = null;
            String importStatus = "";
            try {
                File imports = new File(getFilesDir(), "imports");
                if (!imports.exists() && !imports.mkdirs()) throw new Exception("저장 폴더 생성 실패");
                copied = new File(imports, modelName + ".gguf");
                modelfile = new File(imports, modelName + ".Modelfile");
                long available = new StatFs(imports.getAbsolutePath()).getAvailableBytes();
                long total = 0;
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(copied)) {
                    if (in == null) throw new Exception("파일을 열 수 없음");
                    byte[] buf = new byte[1024 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        total += n;
                        if (total > available - (256L * 1024 * 1024))
                            throw new Exception("저장 공간 부족");
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
                        ui.post(() -> progressStatus.setText("GGUF 등록: " + now));
                    }
                }
                int exit = p.waitFor();
                if (exit != 0) throw new Exception("ollama create 종료 코드 " + exit + ": " + output);
                importStatus = "GGUF 등록 완료: " + modelName;
                // The created Ollama model has its own blob, so remove the temporary copy.
                if (copied != null) copied.delete();
            } catch (Exception e) { importStatus = "GGUF 가져오기 실패: " + e.getMessage(); }
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
