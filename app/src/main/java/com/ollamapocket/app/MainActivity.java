package com.ollamapocket.app;

import android.Manifest;
import android.app.Activity;
import android.widget.ProgressBar;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.app.AlertDialog;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Android UI. Local inference only; optional Google demo banners use the network. */
public final class MainActivity extends Activity {
    private static final int PICK_GGUF = 701;
    private static final int BG = Color.rgb(14, 16, 19);
    private static final int CARD = Color.rgb(21, 23, 27);
    private static final int FIELD = Color.rgb(38, 40, 45);
    private static final int ACCENT = Color.rgb(242, 243, 245);
    private static final int WHITE = Color.rgb(242, 244, 247);
    private static final int MUTED = Color.rgb(158, 163, 171);
    private static final int DANGER = Color.rgb(238, 119, 130);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private FrameLayout pages;
    private LinearLayout chatPage, modelPage, serverPage;
    private DeviceProfiler.Profile deviceProfile;
    private ComputeProfiler.Report computeReport;
    private TextView computeSummary;
    private TextView deviceSummary;
    private LinearLayout recommendationRows;
    private ProgressBar downloadProgress;
    private FrameLayout downloadAdSlot;
    private AdSupport ads;
    private long lastPauseElapsed;
    private Button[] navButtons;
    private LinearLayout messages, modelRows;
    private ScrollView messageScroll;
    private Spinner modelChooser;
    private TextView serverStatus, miniStatus, progressStatus, logText, activeEngineLabel;
    private EditText chatInput, modelInput;
    private Button sendButton, stopChatButton, pullButton, cancelPullButton;
    private boolean diagnosticBusy;
    private TextView diagnosticStatus;
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        deviceProfile = DeviceProfiler.scan(this);
        computeReport = ComputeProfiler.scan(this);
        ads = new AdSupport(this);
        currentModel = getPreferences(MODE_PRIVATE).getString("model", "");
        createUi();
        loadHistory();
        renderHistory();
        showTab(0);
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3);
        }
        startServer();
        ui.postDelayed(this::refreshModels, 2000);
        if (!getPreferences(MODE_PRIVATE).getBoolean("welcome_v2_seen", false)) {
            ui.postDelayed(this::showFirstRunGuide, 400);
        } else {
            ads.prepareConsent();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        ui.post(poll);
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
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (chatConnection != null) chatConnection.disconnect();
        if (pullConnection != null) pullConnection.disconnect();
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
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private EditText edit(String hint, String value, boolean multiline) {
        EditText e = new EditText(this); e.setTextColor(WHITE); e.setHintTextColor(MUTED);
        e.setTextSize(14); e.setHint(hint); e.setText(value); e.setPadding(dp(12), dp(9), dp(12), dp(9));
        e.setBackground(bg(FIELD, 12)); e.setSingleLine(!multiline);
        if (multiline) { e.setMinLines(2); e.setMaxLines(4); }
        return e;
    }
    private Button button(String label, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setTextSize(13); b.setTextColor(primary ? BG : WHITE);
        b.setPadding(dp(11), dp(4), dp(11), dp(4)); b.setMinHeight(0); b.setMinimumHeight(0);
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
        rowItem(header, menu, 0, 9);
        menu.setOnClickListener(v -> showChatList());
        LinearLayout branding = column();
        branding.addView(text("AI in Pocket", 19, WHITE, true));
        branding.addView(text("Client for Ollama", 10, MUTED, false));
        rowItem(header, branding, 1, 5);
        Button fresh = button("＋ 새 대화", false);
        rowItem(header, fresh, 0, 0);
        fresh.setOnClickListener(v -> newChat());
        root.addView(header, lp(-1, 50));
        miniStatus = text("● 로컬 AI 엔진 확인 중", 11, MUTED, false);
        margin(miniStatus, root, 2, 6);
        pages = new FrameLayout(this);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, 0, 1);
        root.addView(pages, contentParams);
        createChatPage(); createModelPage(); createServerPage();
        pages.addView(chatPage); pages.addView(modelPage); pages.addView(serverPage);
        LinearLayout tabs = row();
        navButtons = new Button[]{button("채팅", true), button("모델 받기", false), button("설정", false)};
        for (int i = 0; i < navButtons.length; i++) {
            final int page = i;
            rowItem(tabs, navButtons[i], 1, i < 2 ? 5 : 0);
            navButtons[i].setOnClickListener(v -> showTab(page));
        }
        margin(tabs, root, 8, 2);
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
        modelPage = column();
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content = column(); content.setPadding(dp(2), dp(4), dp(2), dp(12));
        scroll.addView(content); modelPage.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        margin(text("내 기기에 맞는 모델 선택", 20, WHITE, true), content, 3, 6);
        deviceSummary = text(deviceProfile.description(), 12, WHITE, false);
        deviceSummary.setPadding(dp(12), dp(12), dp(12), dp(12));
        deviceSummary.setBackground(bg(CARD, 12));
        margin(deviceSummary, content, 0, 9);
        margin(text("CPU · GPU · NPU 연산 상태", 17, WHITE, true), content, 0, 7);
        computeSummary = text(computeReport.description(), 12, WHITE, false);
        computeSummary.setPadding(dp(12), dp(12), dp(12), dp(12));
        computeSummary.setBackground(bg(CARD, 12));
        margin(computeSummary, content, 0, 9);
        Button scan = button("기기 다시 진단", false);
        margin(scan, content, 0, 9);
        scan.setOnClickListener(v -> {
            deviceProfile = DeviceProfiler.scan(this);
            computeReport = ComputeProfiler.scan(this);
            deviceSummary.setText(deviceProfile.description());
            computeSummary.setText(computeReport.description());
            renderRecommendations();
        });
        recommendationRows = column(); margin(recommendationRows, content, 0, 14);
        margin(text("① 모델을 고르고  ② 다운로드  ③ 채팅", 13, ACCENT, true), content, 0, 10);
        margin(text("모델 다운로드", 20, WHITE, true), content, 3, 5);
        margin(text("최초 다운로드에만 인터넷이 필요합니다. 이후 오프라인 실행됩니다.", 12, MUTED, false), content, 0, 9);
        String recommendedDefault = "qwen2.5:0.5b";
        for (DeviceProfiler.Recommendation rec : DeviceProfiler.recommend(deviceProfile)) {
            if (rec.selectable) { recommendedDefault = rec.model; break; }
        }
        modelInput = edit("모델 선택 또는 직접 입력", recommendedDefault, false);
        margin(modelInput, content, 0, 8);
        LinearLayout pullRow = row(); pullButton = button("모델 다운로드", true); cancelPullButton = button("취소", false);
        rowItem(pullRow, pullButton, 1, 7); rowItem(pullRow, cancelPullButton, 1, 0);
        margin(pullRow, content, 0, 8);
        cancelPullButton.setEnabled(false); cancelPullButton.setAlpha(0.5f);
        pullButton.setOnClickListener(v -> pullModel());
        cancelPullButton.setOnClickListener(v -> { pullCancelled = true; if (pullConnection != null) pullConnection.disconnect(); });
        progressStatus = text("다운로드 전에 데이터 요금 안내를 확인합니다.", 12, MUTED, false);
        margin(progressStatus, content, 0, 5);
        downloadProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        downloadProgress.setMax(100);
        downloadProgress.setProgress(0);
        downloadProgress.setVisibility(View.GONE);
        margin(downloadProgress, content, 0, 8);
        margin(text("광고" + (BuildConfig.DEBUG ? " · Google 테스트 배너" : ""), 11, MUTED, false), content, 0, 3);
        downloadAdSlot = new FrameLayout(this);
        margin(downloadAdSlot, content, 0, 4);
        ads.register(downloadAdSlot, AdSupport.PLACEMENT_DOWNLOAD);
        margin(text("광고는 네트워크 데이터를 사용하며 요금제에 따라 통신료가 발생할 수 있습니다.",
            11, MUTED, false), content, 0, 16);
        LinearLayout titleRow = row();
        rowItem(titleRow, text("설치된 모델", 20, WHITE, true), 1, 0);
        Button refresh = button("새로고침", false); rowItem(titleRow, refresh, 0, 0);
        margin(titleRow, content, 0, 8);
        refresh.setOnClickListener(v -> refreshModels());
        modelRows = column(); margin(modelRows, content, 0, 12);
        Button importButton = button("GGUF 파일 가져오기", false);
        margin(importButton, content, 9, 6);
        importButton.setOnClickListener(v -> {
            if (!OllamaForegroundService.isRunning()) { toast("서버를 먼저 켜세요"); return; }
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_GGUF);
        });
        margin(text("GGUF를 선택하면 앱 내부로 복사한 뒤 Ollama에 등록합니다.\n등록 성공 시 임시 복사본은 삭제됩니다.",
            12, MUTED, false), content, 0, 8);
        renderRecommendations();
    }

    private void createServerPage() {
        serverPage = column();
        margin(text("설정 · 로컬 엔진", 20, WHITE, true), serverPage, 4, 8);
        Button compute = button("CPU / GPU / NPU 실행 장치", false);
        margin(compute, serverPage, 0, 10);
        compute.setOnClickListener(v -> showComputeChoiceDialog());
        Button test = button("CPU · GPU 실제 연산 검사", true);
        margin(test, serverPage, 0, 7);
        diagnosticStatus = text("설치된 모델을 실제 실행해 CPU/GPU 결과를 확인합니다. NPU는 미지원입니다.", 12, MUTED, false);
        margin(diagnosticStatus, serverPage, 0, 8);
        test.setOnClickListener(v -> beginHardwareSelfTest());
        Button privacy = button("광고 개인정보 설정", false);
        margin(privacy, serverPage, 0, 6);
        privacy.setOnClickListener(v -> ads.showPrivacyChoices());
        margin(text("광고에는 인터넷 연결이 필요하고 데이터 요금이 발생할 수 있습니다. " +
            (BuildConfig.DEBUG ? "현재 개발용 앱은 Google 제공 테스트 광고로 수익이 발생하지 않습니다." :
                "운영 광고는 동의 상태와 네트워크에 따라 표시되지 않을 수도 있습니다."),
            12, MUTED, false), serverPage, 0, 9);
        Button license = button("오픈소스·상표 안내", false);
        margin(license, serverPage, 0, 10);
        license.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("오픈소스 · 비공식 클라이언트")
            .setMessage("AI in Pocket - Client for Ollama는 Ollama 공식 제품이 아니며 Ollama와 제휴하지 않습니다. " +
                "Ollama 명칭은 호환 소프트웨어의 출처 설명에만 사용합니다. " +
                "앱의 독자 코드: MIT-0. Ollama 및 llama.cpp의 라이선스와 모델별 라이선스는 각각 적용됩니다. " +
                "모델 다운로드 전 해당 모델의 사용 조건도 확인하세요.")
            .setPositiveButton("확인", null).show());
        serverStatus = text("상태 확인 중", 15, ACCENT, true); margin(serverStatus, serverPage, 0, 8);
        LinearLayout controls = row();
        Button start = button("서버 시작", true), stop = button("서버 종료", false);
        rowItem(controls, start, 1, 7); rowItem(controls, stop, 1, 0);
        margin(controls, serverPage, 0, 12);
        start.setOnClickListener(v -> startServer());
        stop.setOnClickListener(v -> stopServer());
        margin(text("모델 저장: 앱 전용 저장소 · 외부 네트워크 공개 없음\n" +
            "CPU: Ollama / GPU: 별도 Vulkan llama.cpp 엔진 (탑재 시)\n" +
            "NPU: 지원 드라이버 검색만 수행, 전용 추론 엔진 미포함\n" +
            "GPU 추론 불가 시 실제 연산 장치를 CPU로 전환합니다.",
            12, MUTED, false), serverPage, 1, 17);
        margin(text("실행 로그", 16, WHITE, true), serverPage, 0, 6);
        ScrollView logScroll = new ScrollView(this); logScroll.setBackground(bg(CARD, 12));
        logText = text("로딩 중", 11, MUTED, false); logText.setPadding(dp(12), dp(12), dp(12), dp(12));
        logScroll.addView(logText); serverPage.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void beginHardwareSelfTest() {
        if (diagnosticBusy || chatBusy || pullBusy) {
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
        chatPage.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        modelPage.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        serverPage.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < navButtons.length; i++) {
            boolean selected = i == index;
            navButtons[i].setBackground(bg(selected ? ACCENT : FIELD, 10));
            navButtons[i].setTextColor(selected ? BG : WHITE);
        }
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
            margin(text(item.label(), 12, WHITE, false), card, 0, 5);
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
    private void refreshModels() {
        io.execute(() -> {
            try {
                JSONObject result = OllamaHttp.get("/api/tags");
                JSONArray list = result.optJSONArray("models");
                List<String> names = new ArrayList<>();
                if (list != null) for (int i = 0; i < list.length(); i++) {
                    String name = list.optJSONObject(i).optString("name", "");
                    if (!name.isEmpty()) names.add(name);
                }
                ui.post(() -> updateModels(names));
            } catch (Exception error) {
                ui.post(() -> {
                    modelRows.removeAllViews();
                    margin(text("서버 응답 없음. 서버 탭에서 확인하세요.", 12, DANGER, false), modelRows, 8, 8);
                });
            }
        });
    }

    private void updateModels(List<String> names) {
        ignoreSelection = true;
        knownModels.clear(); knownModels.addAll(names);
        List<String> shown = names.isEmpty() ? java.util.Collections.singletonList("설치된 모델 없음") : names;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, shown);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelChooser.setAdapter(adapter);
        int idx = names.indexOf(currentModel);
        if (!names.isEmpty() && idx < 0) {
            currentModel = names.get(0);
            getPreferences(MODE_PRIVATE).edit().putString("model", currentModel).apply();
            loadHistory(); renderHistory();
            idx = 0;
        }
        if (idx >= 0) modelChooser.setSelection(idx);
        modelChooser.setEnabled(!names.isEmpty() && !chatBusy);
        ignoreSelection = false;
        modelRows.removeAllViews();
        if (names.isEmpty()) {
            margin(text("모델이 없습니다. 위에서 모델을 다운로드하세요.", 13, MUTED, false), modelRows, 10, 8);
            return;
        }
        for (String name : names) {
            LinearLayout entry = row(); entry.setPadding(dp(11), dp(7), dp(11), dp(7)); entry.setBackground(bg(CARD, 11));
            TextView label = text(name, 14, WHITE, false); label.setSingleLine(false);
            rowItem(entry, label, 1, 4);
            Button use = button("선택", false); rowItem(entry, use, 0, 4);
            Button delete = button("삭제", false); rowItem(entry, delete, 0, 0);
            margin(entry, modelRows, 3, 3);
            use.setOnClickListener(v -> {
                if (chatBusy) { toast("생성 중에는 변경할 수 없습니다"); return; }
                currentModel = name;
                getPreferences(MODE_PRIVATE).edit().putString("model", currentModel).apply();
                ignoreSelection = true; modelChooser.setSelection(knownModels.indexOf(name)); ignoreSelection = false;
                loadHistory(); renderHistory(); showTab(0);
            });
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("모델 삭제").setMessage(name + " 파일을 기기에서 삭제할까요?")
                .setNegativeButton("취소", null).setPositiveButton("삭제", (d, w) -> {
                    io.execute(() -> {
                        try {
                            JSONObject body = new JSONObject(); body.put("model", name);
                            OllamaHttp.post("/api/delete", body, "DELETE");
                            ui.post(() -> { toast("삭제 완료"); refreshModels(); });
                        } catch (Exception ex) { ui.post(() -> toast("삭제 실패: " + ex.getMessage())); }
                    });
                }).show());
        }
    }

    private void pullModel() {
        String model = modelInput.getText().toString().trim();
        if (model.isEmpty() || !model.matches("[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,119}")) {
            toast("모델명을 확인하세요"); return;
        }
        if (pullBusy) return;
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
    private void showChatList() {
        if (chatBusy) { toast("답변 생성 후 대화 기록을 열 수 있습니다"); return; }
        JSONArray sessions;
        try { sessions = new JSONArray(getPreferences(MODE_PRIVATE).getString(listKey(), "[]")); }
        catch (Exception ignored) { sessions = new JSONArray(); }
        final JSONArray list = sessions;
        String[] titles = new String[list.length()];
        for (int i = 0; i < list.length(); i++) {
            JSONObject chat = list.optJSONObject(i);
            titles[i] = chat == null ? "대화" : chat.optString("title", "대화");
        }
        new AlertDialog.Builder(this)
            .setTitle("대화 기록 · " + (currentModel.isEmpty() ? "모델 미선택" : currentModel))
            .setItems(titles, (d, index) -> {
                JSONObject chat = list.optJSONObject(index);
                if (chat == null) return;
                getPreferences(MODE_PRIVATE).edit().putString(sessionKey(), chat.optString("id")).apply();
                loadHistory(); renderHistory(); showTab(0);
            })
            .setPositiveButton("＋ 새 대화", (d, w) -> newChat())
            .setNeutralButton("모델 관리", (d, w) -> showTab(1))
            .setNegativeButton("닫기", null).show();
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
            t.setBackground(bg(Color.rgb(44, 46, 51), 17));
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
        if (chatBusy) return;
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
