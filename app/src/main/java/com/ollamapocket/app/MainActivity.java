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
    // Palette values are mutable only during Activity creation/theme recreation.
    private static int BG = Color.rgb(14, 16, 19);
    private static int CARD = Color.rgb(21, 23, 27);
    private static int FIELD = Color.rgb(38, 40, 45);
    private static int ACCENT = Color.rgb(242, 243, 245);
    private static int WHITE = Color.rgb(242, 244, 247);
    private static int MUTED = Color.rgb(158, 163, 171);
    private static int DANGER = Color.rgb(238, 119, 130);
    private static int BUBBLE = Color.rgb(44,46,51);
    private String uiTheme = "dark";
    private float uiTextScale = 1.0f;
    private int activeTab = 0;
    private boolean showHud = true;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private FrameLayout pages;
    private LinearLayout chatPage, modelPage, serverPage, diagnosticPage, nodeRows;
    private TextView localConnectionStatus, connectionSessionStatus, modelListHeader;
    private EditText modelFilter;
    private Spinner familyFilter, quantizationFilter;
    private String selectedModelFamily="전체 모델";
    private String selectedQuantization="전체 양자화";
    private final Map<String,String> modelMetadata = new HashMap<>();
    private final Map<String,String> modelQuantization = new HashMap<>();
    private final Map<String,Long> modelBytes = new HashMap<>();
    private TextView suitabilitySummary;
    private DeviceProfiler.Profile deviceProfile;
    private ComputeProfiler.Report computeReport;
    private TextView computeSummary;
    private TextView deviceSummary;
    private LinearLayout recommendationRows;
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
    private Button sendButton, stopChatButton, pullButton, cancelPullButton;
    private boolean diagnosticBusy;
    private volatile boolean benchmarkBusy;
    private TextView diagnosticStatus, performanceHudView;
    private volatile PerformanceHud.Sample lastHud;
    private final Runnable hudPoll=new Runnable() {
        @Override public void run() {
            io.execute(()->{
                PerformanceHud.Sample reading=PerformanceHud.sample(getApplicationContext());
                ui.post(()->{
                    lastHud=reading;
                    if(performanceHudView!=null&&!isFinishing())
                        performanceHudView.setText(reading.compact());
                    if(activeTab==3&&!isFinishing())refreshDiagnosticViews();
                });
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
                "서버 실행 중 · 127.0.0.1:11434" : "서버 중지 / 시작 중");
            logText.setText(OllamaForegroundService.logs() + "\n--- GPU Vulkan ---\n" + GpuRunner.logs());
            if (!serverPollBusy) {
                serverPollBusy = true;
                io.execute(() -> {
                    String label;
                    try {
                        JSONObject version = OllamaHttp.get("/api/version");
                        label = "● 로컬 연결됨 · Ollama " + version.optString("version", "");
                    } catch (Exception ex) { label = "○ 연결 대기 / 서버 중지"; }
                    final String value = label;
                    ui.post(() -> { miniStatus.setText(value); serverPollBusy = false; });
                });
            }
            ui.postDelayed(this, 3500);
        }
    };

    private void applyUiPreferences() {
        uiTheme=getPreferences(MODE_PRIVATE).getString("ui_theme","dark");
        String font=getPreferences(MODE_PRIVATE).getString("font_size","normal");
        uiTextScale="large".equals(font)?1.13f:"extra".equals(font)?1.26f:1.0f;
        showHud=getPreferences(MODE_PRIVATE).getBoolean("show_hud",true);
        if("mono_light".equals(uiTheme)) {
            BG=Color.rgb(250,250,250); CARD=Color.WHITE; FIELD=Color.rgb(234,234,234);
            ACCENT=Color.rgb(23,23,23); WHITE=Color.rgb(24,24,24);
            MUTED=Color.rgb(86,86,86); DANGER=Color.rgb(155,35,35);
            BUBBLE=Color.rgb(231,231,231);
        } else if("mono_dark".equals(uiTheme)) {
            BG=Color.BLACK;CARD=Color.rgb(12,12,12);FIELD=Color.rgb(32,32,32);
            ACCENT=Color.WHITE;WHITE=Color.WHITE;MUTED=Color.rgb(182,182,182);
            DANGER=Color.rgb(242,140,140);BUBBLE=Color.rgb(38,38,38);
        } else {
            BG=Color.rgb(14,16,19);CARD=Color.rgb(21,23,27);FIELD=Color.rgb(38,40,45);
            ACCENT=Color.rgb(242,243,245);WHITE=Color.rgb(242,244,247);
            MUTED=Color.rgb(158,163,171);DANGER=Color.rgb(238,119,130);
            BUBBLE=Color.rgb(44,46,51);
        }
    }
    @Override public void onCreate(Bundle state) {
        String wanted=getPreferences(MODE_PRIVATE).getString("ui_theme","dark");
        setTheme("mono_light".equals(wanted)?
            android.R.style.Theme_Material_Light_NoActionBar:
            android.R.style.Theme_Material_NoActionBar);
        super.onCreate(state);
        applyUiPreferences();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
            "mono_light".equals(uiTheme)?
                (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR):0);
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
        ui.post(poll);
        ui.post(hudPoll);
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastPauseElapsed > 0 && now - lastPauseElapsed > 120_000
                && getPreferences(MODE_PRIVATE).getBoolean("welcome_v2_seen", false)
                && !pullBusy && !chatBusy) {
            ads.showAppOpenIfReady();
        }
    }
    @Override protected void onPause() {
        lastPauseElapsed = android.os.SystemClock.elapsedRealtime();
        ui.removeCallbacks(poll);
        ui.removeCallbacks(hudPoll);
        super.onPause();
    }
    @Override protected void onDestroy() {
        if(navigationDrawer!=null&&navigationDrawer.isShowing())navigationDrawer.dismiss();
        if (chatConnection != null) chatConnection.disconnect();
        if (pullConnection != null) pullConnection.disconnect();
        ui.removeCallbacks(hudPoll);
        io.shutdownNow();
        ads.destroy();
        super.onDestroy();
    }

    private int dp(int size) { return (int)(size * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout.LayoutParams lp(int width, int height) { return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height)); }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g;
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
        b.setBackground(bg(primary ? ACCENT : FIELD, 10)); return b;
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
        root.setPadding(dp(12), dp(8), dp(12), dp(7));
        setContentView(root);
        // Mobile-friendly WebUI-inspired navigation; independent Android widgets and branding.
        LinearLayout header = row();
        Button menu = button("☰", false);
        menu.setContentDescription("대화 목록과 앱 메뉴 열기");
        menu.setTextSize(20);
        menu.setMinWidth(dp(46));menu.setMinimumWidth(dp(46));
        rowItem(header, menu, 0, 12);
        menu.setOnClickListener(v -> showNavigationDrawer());
        LinearLayout branding = column();
        topPageTitle = text("AI in Pocket", 19, WHITE, true);
        branding.addView(topPageTitle);
        topPageSubtitle=text("대화  ·  Client for Ollama", 10, MUTED, false);
        branding.addView(topPageSubtitle);
        rowItem(header, branding, 1, 5);
        Button fresh = button("＋ 새 대화", false);
        rowItem(header, fresh, 0, 0);
        fresh.setOnClickListener(v -> newChat());
        root.addView(header, lp(-1, 50));
        miniStatus = text("● 로컬 AI 엔진 확인 중", 11, MUTED, false);
        margin(miniStatus, root, 2, 6);
        performanceHudView=text("CPU 측정 중 · GPU 조회 중 · RAM 확인 중",11,MUTED,false);
        performanceHudView.setPadding(dp(10),dp(8),dp(10),dp(8));
        performanceHudView.setBackground(bg(FIELD,9));
        margin(performanceHudView,root,0,5);
        performanceHudView.setVisibility(showHud?View.VISIBLE:View.GONE);
        performanceHudView.setOnClickListener(v->{
            PerformanceHud.Sample sample=lastHud;
            String status=sample==null?"기기 성능 지표 확인 중":sample.detail();
            if("host".equals(CoopSession.get(this).role()))
                status+="\n협력 노드: "+CoopSession.get(this).readyNodes().size()+"대";
            new AlertDialog.Builder(this).setTitle("실시간 성능 HUD")
                .setMessage(status).setPositiveButton("닫기",null).show();
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
        TextView modelLabel = text("사용 모델", 12, MUTED, false);
        rowItem(modelRow, modelLabel, 0, 12);
        modelChooser = new Spinner(this); modelChooser.setBackground(bg(FIELD, 12));
        modelChooser.setPadding(dp(12), dp(4), dp(8), dp(4));
        rowItem(modelRow, modelChooser, 1, 0);
        margin(modelRow, chatPage, 2, 7);
        activeEngineLabel = text("실제 연산: CPU (Ollama) · 장치 선택은 설정에서 변경", 11, MUTED, false);
        margin(activeEngineLabel, chatPage, 0, 7);
        modelChooser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (ignoreSelection || knownModels.isEmpty() || position >= knownModels.size()) return;
                String model = knownModels.get(position);
                if (model.equals(currentModel)) return;
                if (chatBusy) { toast("응답 완료 후 모델을 변경하세요"); return; }
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
        chatInput = edit("메시지 입력…", "", true);
        chatInput.setMinLines(1); chatInput.setMaxLines(4);
        rowItem(composer, chatInput, 1, 7);
        sendButton = button("↑", true);
        sendButton.setTextSize(19);
        rowItem(composer, sendButton, 0, 4);
        stopChatButton = button("■", false);
        rowItem(composer, stopChatButton, 0, 0);
        margin(composer, chatPage, 8, 2);
        stopChatButton.setEnabled(false); stopChatButton.setAlpha(0.5f);
        sendButton.setOnClickListener(v -> sendChat());
        stopChatButton.setOnClickListener(v -> stopChat());
        margin(text("AI 답변에 오류가 있을 수 있습니다. 중요한 정보는 확인하세요.",
            10, MUTED, false), chatPage, 0, 0);
    }

    private void createModelPage() {
        modelPage=column();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout content=column();content.setPadding(dp(4),dp(8),dp(4),dp(18));
        scroll.addView(content);modelPage.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        margin(text("모델 선택",23,WHITE,true),content,0,3);
        margin(text("설치된 모델을 탭하면 바로 대화할 수 있습니다.",13,MUTED,false),content,0,10);
        LinearLayout top=row();
        modelListHeader=text("설치된 모델",17,WHITE,true);
        rowItem(top,modelListHeader,1,5);
        Button refresh=button("새로고침",false);rowItem(top,refresh,0,0);
        refresh.setOnClickListener(v->refreshModels());
        margin(top,content,0,7);
        modelFilter=edit("설치된 모델 검색","",false);
        margin(modelFilter,content,0,9);
        modelFilter.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){renderInstalledModels();}
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
        margin(text("모델 계열  ·  양자화 등급/버전",11,MUTED,false),content,0,9);
        populateModelFilters();
        modelRows=column();margin(modelRows,content,0,14);
        margin(text("새 모델 다운로드",18,WHITE,true),content,0,6);
        margin(text("① 모델 선택  →  ② 다운로드  →  ③ 대화 시작",13,MUTED,false),content,0,8);
        LinearLayout downloadButtons=row();
        Button hfBrowse=button("Hugging Face 검색",true);
        Button importButton=button("GGUF 가져오기",false);
        rowItem(downloadButtons,hfBrowse,1,6);rowItem(downloadButtons,importButton,1,0);
        margin(downloadButtons,content,0,8);
        hfBrowse.setOnClickListener(v->{
            if(chatBusy||pullBusy||benchmarkBusy||HuggingFaceCatalog.isDownloading()){
                toast("다른 작업이 종료된 뒤 이용하세요");return;
            }
            HuggingFaceCatalog.open(this,this::refreshModels);
        });
        importButton.setOnClickListener(v->{
            if(!OllamaForegroundService.isRunning()){toast("먼저 Ollama 서버를 켜세요");return;}
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");startActivityForResult(intent,PICK_GGUF);
        });
        String recommendedDefault="qwen2.5:0.5b";
        for(DeviceProfiler.Recommendation rec:DeviceProfiler.recommend(deviceProfile)){
            if(rec.selectable){recommendedDefault=rec.model;break;}
        }
        modelInput=edit("모델명 예: qwen2.5:1.5b",recommendedDefault,false);
        margin(modelInput,content,0,7);
        LinearLayout pullRow=row();
        pullButton=button("모델 다운로드",true);cancelPullButton=button("취소",false);
        rowItem(pullRow,pullButton,1,6);rowItem(pullRow,cancelPullButton,0,0);
        margin(pullRow,content,0,8);
        cancelPullButton.setEnabled(false);cancelPullButton.setAlpha(0.55f);
        pullButton.setOnClickListener(v->pullModel());
        cancelPullButton.setOnClickListener(v->{
            pullCancelled=true;if(pullConnection!=null)pullConnection.disconnect();
        });
        progressStatus=text("다운로드 전 데이터 요금 안내가 표시됩니다.",12,MUTED,false);
        margin(progressStatus,content,0,5);
        downloadProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        downloadProgress.setMax(100);downloadProgress.setProgress(0);
        downloadProgress.setVisibility(View.GONE);
        margin(downloadProgress,content,0,8);
        margin(text("광고"+(BuildConfig.DEBUG?" · 테스트":""),11,MUTED,false),content,0,3);
        downloadAdSlot=new FrameLayout(this);margin(downloadAdSlot,content,0,4);
        ads.register(downloadAdSlot,AdSupport.PLACEMENT_DOWNLOAD);
        margin(text("모델 다운로드와 광고에는 데이터가 사용되며 통신료가 발생할 수 있습니다.",
            11,MUTED,false),content,0,15);
        margin(text("이 기기에 추천하는 모델",18,WHITE,true),content,0,6);
        recommendationRows=column();margin(recommendationRows,content,0,8);
        renderRecommendations();
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
        margin(text("설정",23,WHITE,true),content,0,5);
        margin(text("앱 화면과 추론 방식을 원하는 대로 설정하세요.",13,MUTED,false),
            content,0,15);

        margin(text("화면 및 가독성",17,WHITE,true),content,0,8);
        String currentTheme="mono_light".equals(uiTheme)?"흑백 라이트"
                :"mono_dark".equals(uiTheme)?"흑백 다크":"기본 다크";
        Button theme=button("화면 스타일  ·  "+currentTheme,false);
        margin(theme,content,0,8);
        theme.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("화면 스타일")
            .setSingleChoiceItems(new String[]{"기본 다크","흑백 라이트 (흰 배경)","흑백 다크 (검정 배경)"},
                "mono_light".equals(uiTheme)?1:"mono_dark".equals(uiTheme)?2:0,
                (dialog,which)->{
                    dialog.dismiss();
                    applySetting("ui_theme",which==1?"mono_light":which==2?"mono_dark":"dark");
                }).setNegativeButton("취소",null).show());

        String f=getPreferences(MODE_PRIVATE).getString("font_size","normal");
        String sizeLabel="large".equals(f)?"크게":"extra".equals(f)?"매우 크게":"기본";
        Button typeSize=button("글자 크기  ·  "+sizeLabel,false);
        margin(typeSize,content,0,8);
        typeSize.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("화면 글자 크기")
            .setSingleChoiceItems(new String[]{"기본 (100%)","크게 (113%)","매우 크게 (126%)"},
                "large".equals(f)?1:"extra".equals(f)?2:0,
                (dialog,which)->{
                    dialog.dismiss();
                    applySetting("font_size",which==1?"large":which==2?"extra":"normal");
                }).setNegativeButton("취소",null).show());

        Button hud=button("실시간 성능 HUD  ·  "+(showHud?"표시":"숨김"),false);
        margin(hud,content,0,14);
        hud.setOnClickListener(v->{
            if(isUiBusy()){toast("진행 중인 작업이 끝난 뒤 변경하세요");return;}
            getPreferences(MODE_PRIVATE).edit().putBoolean("show_hud",!showHud).apply();
            recreate();
        });

        margin(text("로컬 추론",17,WHITE,true),content,0,8);
        Button compute=button("추론 장치 선택  ·  CPU / GPU / 협력",false);
        margin(compute,content,0,8);
        compute.setOnClickListener(v->showComputeChoiceDialog());
        serverStatus=text("로컬 서버 상태 확인 중",13,MUTED,true);
        margin(serverStatus,content,0,8);
        LinearLayout controls=row();
        Button start=button("서버 시작",true),stop=button("서버 종료",false);
        rowItem(controls,start,1,7);rowItem(controls,stop,1,0);
        margin(controls,content,0,14);
        start.setOnClickListener(v->startServer());
        stop.setOnClickListener(v->stopServer());
        margin(text("기본적으로 모델 추론은 기기 안에서 실행됩니다. " +
            "협력 추론은 같은 Wi-Fi의 승인된 기기에만 연결하세요.",
            12,MUTED,false),content,0,16);

        margin(text("광고 및 개인정보",17,WHITE,true),content,0,8);
        Button privacy=button("광고 개인정보 설정",false);
        margin(privacy,content,0,7);
        privacy.setOnClickListener(v->ads.showPrivacyChoices());
        margin(text("광고와 모델 다운로드에는 인터넷 데이터가 필요하며 통신료가 " +
            "발생할 수 있습니다. 개발용 APK에서는 테스트 광고만 표시합니다.",
            12,MUTED,false),content,0,16);

        margin(text("앱 정보",17,WHITE,true),content,0,8);
        Button license=button("오픈소스 · 상표 및 모델 라이선스",false);
        margin(license,content,0,5);
        license.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("오픈소스 및 비공식 클라이언트")
            .setMessage("AI in Pocket - Client for Ollama는 Ollama 공식 앱이 아닙니다. " +
                "앱 자체 코드는 MIT-0, Ollama/llama.cpp와 모델 가중치는 각자의 " +
                "라이선스가 적용됩니다. 모델의 상업적 이용 가능 여부를 확인하세요.")
            .setPositiveButton("닫기",null).show());
        margin(text("AI in Pocket · 독립 안드로이드 클라이언트",12,MUTED,false),content,8,0);
    }

    private boolean isUiBusy(){
        return chatBusy||pullBusy||diagnosticBusy||benchmarkBusy
               ||HuggingFaceCatalog.isDownloading();
    }

    private void applySetting(String key,String value){
        if(isUiBusy()){toast("진행 중인 작업이 끝난 뒤 변경하세요");return;}
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
            renderRecommendations();
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
            .setNegativeButton("취소", null)
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
                            .setNegativeButton("닫기", null)
                            .setPositiveButton("결과 복사", (dialog, action) -> {
                                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                                clipboard.setPrimaryClip(ClipData.newPlainText("AI in Pocket 진단 결과", result));
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
                }).setNegativeButton("취소", null).show();
    }

    private void showTab(int index) {
        activeTab=Math.max(0,Math.min(3,index));
        chatPage.setVisibility(activeTab==0?View.VISIBLE:View.GONE);
        modelPage.setVisibility(activeTab==1?View.VISIBLE:View.GONE);
        serverPage.setVisibility(activeTab==2?View.VISIBLE:View.GONE);
        diagnosticPage.setVisibility(activeTab==3?View.VISIBLE:View.GONE);
        if(topPageTitle!=null)topPageTitle.setText("AI in Pocket");
        if(topPageSubtitle!=null){
            String[] subtitles={"대화","모델 선택","설정","기기 / 연결 진단"};
            topPageSubtitle.setText(subtitles[activeTab]+"  ·  로컬 AI");
        }
        if(activeTab==3)refreshDiagnosticViews();
    }

    private void renderRecommendations() {
        if (recommendationRows == null || modelInput == null) return;
        recommendationRows.removeAllViews();
        margin(text("기기 성능을 바탕으로 한 추정 추천입니다. 실제 성능은 다를 수 있습니다.",
            11, MUTED, false), recommendationRows, 0, 7);
        List<DeviceProfiler.Recommendation> choices = DeviceProfiler.recommend(deviceProfile);
        int added = 0;
        for (DeviceProfiler.Recommendation item : choices) {
            if (!item.selectable) continue;
            LinearLayout card = column();
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.setBackground(bg(CARD, 12));
            ModelPerformanceAdvisor.Result perf=advise(item.model,
                item.downloadGb*1_000_000_000/(1024.0*1024.0*1024.0));
            margin(text(item.label()+"\n적합도: "+perf.suitability+
                " · "+perf.throughput,12,WHITE,false),card,0,5);
            Button select = button("이 모델 선택", item.category.equals("추천"));
            margin(select, card, 3, 0);
            select.setOnClickListener(v -> {
                modelInput.setText(item.model);
                toast(item.model + " 선택 완료 · 다운로드를 누르세요");
            });
            margin(card, recommendationRows, 3, 4);
            if (++added >= 3) break;
        }
        if (added == 0)
            margin(text("이 기기에서 안전하게 추천할 모델이 없습니다. RAM 또는 저장공간을 확인하세요.",
                12, DANGER, false), recommendationRows, 4, 8);
    }

    private void showFirstRunGuide() {
        LinearLayout guide = column(); guide.setPadding(dp(20), dp(12), dp(20), dp(5));
        margin(text("AI in Pocket 시작 안내", 20, WHITE, true), guide, 0, 8);
        margin(text("1. 모델 탭에서 휴대폰 성능을 진단하세요.\n" +
            "2. 추천 모델을 선택하고 다운로드하세요.\n" +
            "3. 채팅 탭에서 오프라인으로 질문할 수 있습니다.\n\n" +
            "필수 안내: 모델 다운로드(수백 MB~수 GB)와 광고 표시에는 " +
            "인터넷 데이터가 사용되며 요금제에 따라 통신료가 발생할 수 있습니다. " +
            "Wi-Fi 이용을 권장합니다. 이 화면은 언제든 닫을 수 있습니다.",
            13, WHITE, false), guide, 0, 11);
        margin(text("광고" + (BuildConfig.DEBUG ? " · Google 테스트 배너" : ""), 11, MUTED, false), guide, 0, 4);
        FrameLayout guideAd = new FrameLayout(this);
        margin(guideAd, guide, 0, 4);
        ads.register(guideAd, AdSupport.PLACEMENT_ONBOARDING);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(guide)
            .setNegativeButton("닫고 채팅", (d,w) -> showTab(0))
            .setPositiveButton("모델 선택 시작", (d,w) -> showTab(1)).create();
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
                    modelRows.removeAllViews();
                    margin(text("로컬 서버 연결을 확인하세요. 설정에서 서버를 시작할 수 있습니다.",
                        13,DANGER,false),modelRows,7,8);
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
        ignoreSelection=false;
        populateModelFilters();
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

    private ArrayAdapter<String> makeFilterAdapter(List<String> options) {
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item,options){
            @Override public View getView(int pos,View recycled,ViewGroup parent){
                TextView view=text(getItem(pos),13,WHITE,true);
                view.setPadding(dp(11),dp(8),dp(3),dp(8));
                return view;
            }
            @Override public View getDropDownView(int pos,View recycled,ViewGroup parent){
                TextView view=text(getItem(pos),13,WHITE,false);
                view.setPadding(dp(14),dp(12),dp(10),dp(12));
                view.setBackground(bg(CARD,0));
                return view;
            }
        };
        return adapter;
    }

    private void populateModelFilters(){
        if(familyFilter==null||quantizationFilter==null)return;
        List<String> families=new ArrayList<>();
        families.add("전체 모델");
        List<String> foundFamilies=new ArrayList<>();
        List<String> quantized=new ArrayList<>();
        for(String name:knownModels){
            String family=modelFamily(name,modelMetadata.getOrDefault(name,""));
            if(!foundFamilies.contains(family))foundFamilies.add(family);
            String q=modelQuantization.getOrDefault(name,"미확인");
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

    private void renderInstalledModels() {
        if(modelRows==null)return;
        modelRows.removeAllViews();
        String filter=modelFilter==null?"":modelFilter.getText().toString().trim()
             .toLowerCase(Locale.ROOT);
        int matched=0;
        for(String name:knownModels){
            String meta=modelMetadata.getOrDefault(name,"로컬 모델");
            String family=modelFamily(name,meta);
            String quant=modelQuantization.getOrDefault(name,"미확인");
            if(!ModelListFilter.matches(name,meta,quant,filter,
                    selectedModelFamily,selectedQuantization))continue;
            matched++;
            boolean selected=name.equals(currentModel);
            LinearLayout card=column();
            card.setPadding(dp(14),dp(12),dp(14),dp(12));
            card.setBackground(bg(CARD,14));
            LinearLayout heading=row();
            TextView title=text(name,16,WHITE,true);
            title.setMaxLines(2);
            rowItem(heading,title,1,4);
            if(selected)rowItem(heading,text("● 선택됨",11,MUTED,true),0,0);
            margin(heading,card,0,6);
            ModelPerformanceAdvisor.Result perf=advise(name,
                modelBytes.getOrDefault(name,0L)/(1024.0*1024.0*1024.0));
            margin(text("계열: "+family+"   ·   양자화: "+quant+
                "\n"+meta+"\n적합도: "+perf.suitability+"   ·   "+perf.throughput,
                12,MUTED,false),card,0,8);
            LinearLayout actions=row();
            Button use=button(selected?"대화 계속":"선택하고 대화",selected);
            Button delete=button("삭제",false);
            rowItem(actions,use,1,7);rowItem(actions,delete,0,0);
            margin(actions,card,0,0);
            margin(card,modelRows,0,7);
            use.setOnClickListener(v->{
                if(chatBusy||benchmarkBusy||diagnosticBusy) {
                    toast("진행 중인 작업을 끝낸 뒤 모델을 바꾸세요");return;
                }
                if(!selected)selectModel(name);
                showTab(0);
            });
            delete.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("모델 삭제")
                .setMessage(name+" 모델 파일을 이 기기에서 삭제할까요?")
                .setNegativeButton("취소",null)
                .setPositiveButton("삭제",(d,w)->io.execute(()->{
                    try {
                        JSONObject body=new JSONObject();body.put("model",name);
                        OllamaHttp.post("/api/delete",body,"DELETE");
                        ui.post(()->{toast("모델 삭제 완료");refreshModels();});
                    }catch(Exception ex){
                        ui.post(()->toast("삭제 실패: "+ex.getMessage()));
                    }
                })).show());
        }
        if(modelListHeader!=null)
            modelListHeader.setText("설치 모델  "+knownModels.size()+"개  ·  표시 "+matched+"개");
        if(matched==0) {
            String hint=knownModels.isEmpty()?"모델이 없습니다.\n아래에서 모델을 다운로드하세요."
                :"조건에 일치하는 모델이 없습니다. 검색어 또는 필터를 변경하세요.";
            TextView empty=text(hint,14,MUTED,false);
            empty.setPadding(dp(14),dp(18),dp(14),dp(18));
            empty.setBackground(bg(CARD,14));
            margin(empty,modelRows,0,8);
        }
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
        new AlertDialog.Builder(this)
            .setTitle("다운로드 · 데이터 요금 안내")
            .setMessage("선택 모델: " + model + "\n\n모델 다운로드는 수백 MB~수 GB의 " +
                "인터넷 데이터를 사용할 수 있습니다. Wi-Fi나 모바일 데이터 요금제에 따라 " +
                "통신료가 발생할 수 있습니다. 저장공간과 인터넷 연결을 확인하세요.\n\n" +
                "다운로드 중 표시되는 광고에도 인터넷 데이터가 사용될 수 있습니다.")
            .setNegativeButton("취소", null)
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
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgress.setIndeterminate(true);
        ads.showBanner(downloadAdSlot);
        pullButton.setEnabled(false); pullButton.setAlpha(0.5f);
        cancelPullButton.setEnabled(true); cancelPullButton.setAlpha(1f);
        progressStatus.setText(model + " 다운로드 준비 중…");
        io.execute(() -> {
            String error = null;
            try {
                HttpURLConnection connection = OllamaHttp.newPullConnection(); pullConnection = connection;
                OllamaHttp.stream(connection, OllamaHttp.pullBody(model), item -> {
                    String status = item.optString("status", "진행 중");
                    long total = item.optLong("total", 0), completed = item.optLong("completed", 0);
                    String info = model + " · " + status + (total > 0 ? " (" + (completed * 100 / total) + "%)" : "");
                    ui.post(() -> {
                        progressStatus.setText(info);
                        if (total > 0) {
                            downloadProgress.setIndeterminate(false);
                            downloadProgress.setProgress((int)Math.max(0, Math.min(100, completed * 100 / total)));
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
                pullButton.setEnabled(true); pullButton.setAlpha(1f);
                cancelPullButton.setEnabled(false); cancelPullButton.setAlpha(0.5f);
                progressStatus.setText(pullCancelled ? "다운로드 중단" : failure != null ?
                    "다운로드 실패: " + failure : "모델 설치 완료: " + model);
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
        if (chatBusy) { toast("답변 생성 중에는 새 대화를 만들 수 없습니다"); return; }
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
        TextView t=text(title+(subtitle.isEmpty()?"":"\n"+subtitle),
                subtitle.isEmpty()?15:14,WHITE,active);
        t.setSingleLine(false);
        t.setMaxLines(subtitle.isEmpty()?2:3);
        t.setPadding(dp(14),dp(10),dp(12),dp(10));
        t.setMinHeight(dp(subtitle.isEmpty()?46:58));
        t.setBackground(bg(active?FIELD:CARD,12));
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
            TextView item=drawerRow(entry.title,entry.model,active);
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

    private TextView drawerNavigationItem(String mark,String title,String description){
        TextView item=text(mark+"   "+title+"\n        "+description,14,WHITE,true);
        item.setMinHeight(dp(56));
        item.setPadding(dp(14),dp(8),dp(12),dp(8));
        item.setBackground(bg(CARD,11));
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
        shell.setBackgroundColor(CARD);
        shell.setPadding(dp(12),dp(12),dp(12),dp(8));
        // Keep the pinned profile and app navigation clear of gesture/status bars.
        if(Build.VERSION.SDK_INT>=29){
            shell.setOnApplyWindowInsetsListener((view,insets)->{
                int top=Math.min(dp(35),Math.max(0,insets.getSystemWindowInsetTop()));
                int bottom=Math.min(dp(65),Math.max(0,insets.getSystemWindowInsetBottom()));
                view.setPadding(dp(12),dp(12)+top,dp(12),dp(8)+bottom);
                return insets;
            });
        }

        LinearLayout heading=row();
        TextView product=text("AI in Pocket",19,WHITE,true);
        rowItem(heading,product,1,8);
        Button close=button("×",false);
        close.setTextSize(23);
        close.setContentDescription("사이드바 닫기");
        rowItem(heading,close,0,0);
        close.setOnClickListener(v->drawer.dismiss());
        margin(heading,shell,0,14);

        TextView newChatItem=drawerRow("✎   새 대화","",false);
        margin(newChatItem,shell,0,12);
        newChatItem.setOnClickListener(v->{
            if(chatBusy){toast("생성 중에는 새 대화를 시작할 수 없습니다");return;}
            drawer.dismiss();
            newChat();
        });

        EditText search=edit("대화 검색","",false);
        search.setSingleLine(true);
        search.setMinHeight(dp(48));
        margin(search,shell,0,13);
        margin(text("최근 대화",12,MUTED,true),shell,0,7);
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
        separatorParams.topMargin=dp(6);
        separatorParams.bottomMargin=dp(8);
        shell.addView(separator,separatorParams);
        margin(text("메뉴",12,MUTED,true),shell,0,4);

        TextView models=drawerNavigationItem("◫","모델 선택","설치 모델 · Hugging Face");
        TextView settings=drawerNavigationItem("⚙","설정","화면 · 추론 엔진 · 개인정보");
        TextView diagnostics=drawerNavigationItem("▦","기기/연결 진단","기기 사양 · 연결 노드 · 성능");
        margin(models,shell,0,2);
        margin(settings,shell,0,2);
        margin(diagnostics,shell,0,5);
        models.setOnClickListener(v->{drawer.dismiss();showTab(1);});
        settings.setOnClickListener(v->{drawer.dismiss();showTab(2);});
        diagnostics.setOnClickListener(v->{drawer.dismiss();showTab(3);});

        LinearLayout profile=row();
        profile.setGravity(Gravity.CENTER_VERTICAL);
        profile.setPadding(dp(8),dp(8),dp(8),dp(8));
        profile.setBackground(bg(FIELD,14));
        android.widget.ImageView avatar=new android.widget.ImageView(this);
        avatar.setImageResource(R.mipmap.ic_launcher);
        avatar.setContentDescription("풀을 뜯는 라마 앱 아이콘");
        LinearLayout.LayoutParams avatarSize=lp(42,42);
        avatarSize.rightMargin=dp(10);
        profile.addView(avatar,avatarSize);
        LinearLayout identity=column();
        identity.addView(text("AI in Pocket",14,WHITE,true));
        identity.addView(text("로컬 AI · "+(currentModel.isEmpty()?"모델 미선택":currentModel),
            11,MUTED,false));
        rowItem(profile,identity,1,0);
        margin(profile,shell,3,2);
        profile.setOnClickListener(v->{
            drawer.dismiss();
            showTab(2); // Profile-style quick access to local app settings.
        });

        drawer.setContentView(shell);
        drawer.setOnDismissListener(d->{
            if(navigationDrawer==drawer)navigationDrawer=null;
        });
        Window window=drawer.getWindow();
        if(window!=null){
            window.setBackgroundDrawable(new ColorDrawable(CARD));
            window.setStatusBarColor(BG);
            window.setNavigationBarColor(BG);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.42f);
            window.setGravity(Gravity.START|Gravity.TOP);
        }
        drawer.show();
        if(window!=null){
            int available=getResources().getDisplayMetrics().widthPixels;
            int width=Math.min(dp(370),Math.max(dp(240),available-dp(48)));
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
            margin(text("무엇을 도와드릴까요?", 26, WHITE, true), messages, 45, 9);
            margin(text("원하는 질문을 입력하거나 아래 예시를 선택하세요.",
                13, MUTED, false), messages, 0, 20);
            for (String hint : new String[]{"글을 자연스럽게 다듬어줘", "오늘 할 일을 정리해줘", "파이썬 코드 예시 작성"}) {
                Button suggestion = button("✦  " + hint, false);
                margin(suggestion, messages, 4, 4);
                suggestion.setOnClickListener(v -> chatInput.setText(hint));
            }
            if (knownModels.isEmpty()) {
                Button install = button("모델 설치하러 가기", true);
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
    private TextView bubble(String role, String content) {
        boolean user = "user".equals(role);
        TextView t = text((user ? "" : "AI in Pocket\n") + content, 14, WHITE, false);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        if (user) {
            t.setBackground(bg(BUBBLE,17));
        } else {
            t.setBackgroundColor(BG);
        }
        LinearLayout wrapper = row();
        wrapper.setGravity(user ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams params = lp(-2, -2);
        params.width = user ? -2 : -1;
        wrapper.addView(t, params);
        margin(wrapper, messages, 5, 9);
        scrollBottom();
        return t;
    }
    private void scrollBottom() { if (messageScroll != null) messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN)); }
    private void setChatBusy(boolean busy) {
        chatBusy = busy;
        sendButton.setEnabled(!busy); sendButton.setAlpha(busy ? 0.5f : 1f);
        stopChatButton.setEnabled(busy); stopChatButton.setAlpha(busy ? 1f : 0.5f);
        modelChooser.setEnabled(!busy && !knownModels.isEmpty());
    }
    private void sendChat() {
        String prompt = chatInput.getText().toString().trim();
        if (prompt.isEmpty()) return;
        if (currentModel.isEmpty() || knownModels.isEmpty()) { toast("모델 탭에서 모델을 설치하세요"); showTab(1); return; }
        if (chatBusy || benchmarkBusy) {
            if(benchmarkBusy)toast("벤치마크 측정이 끝난 뒤 채팅하세요");
            return;
        }
        // Clear the input first, then append to the selected model's history.
        chatInput.setText("");
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(chatInput.getWindowToken(), 0);
        addHistory("user", prompt);
        bubble("user", prompt);
        TextView assistant = bubble("assistant", "생성 중…");
        setChatBusy(true); chatCancelled = false;
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
                        ui.post(() -> { assistant.setText("AI in Pocket\n생성 중단됨"); setChatBusy(false); });
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
                    ui.post(() -> { assistant.setText("AI in Pocket\n생성 중단됨"); setChatBusy(false); });
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
                if (!finalAnswer.isEmpty()) {
                    assistant.setText("AI in Pocket\n" + finalAnswer + (chatCancelled ? "\n\n[생성 중단]" : ""));
                    // Keep incomplete responses visible but out of next turn's request context.
                    if (!chatCancelled && finalError == null && modelAtStart.equals(currentModel))
                        addHistory("assistant", finalAnswer);
                } else {
                    assistant.setText("AI in Pocket\n" + (chatCancelled ? "생성 중단됨" : "오류: " + finalError));
                }
                setChatBusy(false);
                scrollBottom();
            });
        });
    }
    private void appendAnswer(JSONObject item, StringBuilder answer, TextView assistant) {
        JSONObject message = item.optJSONObject("message");
        if (message == null) return;
        String part = message.optString("content", "");
        if (part.isEmpty()) return;
        answer.append(part);
        String display = answer.toString();
        ui.post(() -> { assistant.setText("AI in Pocket\n" + display); scrollBottom(); });
    }
    private void stopChat() {
        chatCancelled = true;
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
