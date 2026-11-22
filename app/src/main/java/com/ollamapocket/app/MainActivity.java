package com.ollamapocket.app;

import android.Manifest;
import android.app.Activity;
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

/** All UI is native Android, with no account, cloud SDK, WebView or remote backend. */
public final class MainActivity extends Activity {
    private static final int PICK_GGUF = 701;
    private static final int BG = Color.rgb(12, 18, 30);
    private static final int CARD = Color.rgb(24, 34, 51);
    private static final int FIELD = Color.rgb(36, 46, 64);
    private static final int ACCENT = Color.rgb(65, 211, 176);
    private static final int WHITE = Color.rgb(237, 243, 249);
    private static final int MUTED = Color.rgb(152, 169, 188);
    private static final int DANGER = Color.rgb(238, 119, 130);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private FrameLayout pages;
    private LinearLayout chatPage, modelPage, serverPage;
    private LinearLayout messages, modelRows;
    private ScrollView messageScroll;
    private Spinner modelChooser;
    private TextView serverStatus, miniStatus, progressStatus, logText;
    private EditText chatInput, modelInput;
    private Button sendButton, stopChatButton, pullButton, cancelPullButton;
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
            logText.setText(OllamaForegroundService.logs());
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
    }

    @Override protected void onResume() { super.onResume(); ui.post(poll); }
    @Override protected void onPause() { ui.removeCallbacks(poll); super.onPause(); }
    @Override protected void onDestroy() {
        if (chatConnection != null) chatConnection.disconnect();
        if (pullConnection != null) pullConnection.disconnect();
        io.shutdownNow();
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
        LinearLayout root = column(); root.setBackgroundColor(BG); root.setPadding(dp(15), dp(12), dp(15), dp(8));
        setContentView(root);
        TextView heading = text("Ollama  Pocket", 25, WHITE, true); margin(heading, root, 1, 3);
        miniStatus = text("○ 서버 확인 중", 12, ACCENT, false); margin(miniStatus, root, 0, 12);
        LinearLayout tabs = row();
        Button chatTab = button("대화", true), modelsTab = button("모델", false), serverTab = button("서버", false);
        rowItem(tabs, chatTab, 1, 6); rowItem(tabs, modelsTab, 1, 6); rowItem(tabs, serverTab, 1, 0);
        root.addView(tabs, lp(-1, 42));
        chatTab.setOnClickListener(v -> showTab(0));
        modelsTab.setOnClickListener(v -> showTab(1));
        serverTab.setOnClickListener(v -> showTab(2));
        pages = new FrameLayout(this); LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, 0, 1);
        contentParams.topMargin = dp(13); root.addView(pages, contentParams);
        createChatPage(); createModelPage(); createServerPage();
        pages.addView(chatPage); pages.addView(modelPage); pages.addView(serverPage);
        TextView foot = text("100% 기기 내 추론  ·  클라우드 전송 없음", 11, MUTED, false);
        foot.setGravity(Gravity.CENTER); margin(foot, root, 8, 0);
    }

    private void createChatPage() {
        chatPage = column();
        TextView modelLabel = text("사용 모델", 12, MUTED, false); margin(modelLabel, chatPage, 0, 4);
        modelChooser = new Spinner(this); modelChooser.setBackground(bg(FIELD, 12));
        modelChooser.setPadding(dp(7), dp(3), dp(7), dp(3));
        margin(modelChooser, chatPage, 0, 8);
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
        messageScroll = new ScrollView(this); messageScroll.setFillViewport(true); messageScroll.setBackground(bg(CARD, 14));
        messages = column(); messages.setPadding(dp(12), dp(12), dp(12), dp(12));
        messageScroll.addView(messages);
        LinearLayout.LayoutParams scrollParam = new LinearLayout.LayoutParams(-1, 0, 1);
        chatPage.addView(messageScroll, scrollParam);
        chatInput = edit("질문을 입력하세요", "", true);
        LinearLayout.LayoutParams inputP = lp(-1, -2); inputP.topMargin = dp(9); chatPage.addView(chatInput, inputP);
        LinearLayout chatButtons = row();
        sendButton = button("전송", true); stopChatButton = button("생성 중지", false);
        rowItem(chatButtons, sendButton, 1, 6); rowItem(chatButtons, stopChatButton, 1, 6);
        Button clear = button("새 대화", false); rowItem(chatButtons, clear, 1, 0);
        margin(chatButtons, chatPage, 8, 0);
        stopChatButton.setEnabled(false); stopChatButton.setAlpha(0.5f);
        sendButton.setOnClickListener(v -> sendChat());
        stopChatButton.setOnClickListener(v -> stopChat());
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("대화 초기화").setMessage("선택한 모델의 대화 기록을 삭제할까요?")
            .setNegativeButton("취소", null).setPositiveButton("삭제", (d, w) -> {
                if (chatBusy) stopChat();
                history = new JSONArray(); storeHistory(); renderHistory();
            }).show());
    }

    private void createModelPage() {
        modelPage = column();
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content = column(); content.setPadding(dp(2), dp(4), dp(2), dp(12));
        scroll.addView(content); modelPage.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        margin(text("모델 다운로드", 20, WHITE, true), content, 3, 5);
        margin(text("최초 다운로드에만 인터넷이 필요합니다. 이후 오프라인 실행됩니다.", 12, MUTED, false), content, 0, 9);
        modelInput = edit("예: gemma3:1b 또는 qwen2.5:0.5b", "gemma3:1b", false);
        margin(modelInput, content, 0, 8);
        LinearLayout pullRow = row(); pullButton = button("모델 다운로드", true); cancelPullButton = button("취소", false);
        rowItem(pullRow, pullButton, 1, 7); rowItem(pullRow, cancelPullButton, 1, 0);
        margin(pullRow, content, 0, 8);
        cancelPullButton.setEnabled(false); cancelPullButton.setAlpha(0.5f);
        pullButton.setOnClickListener(v -> pullModel());
        cancelPullButton.setOnClickListener(v -> { pullCancelled = true; if (pullConnection != null) pullConnection.disconnect(); });
        progressStatus = text("작은 모델 권장: qwen2.5:0.5b / gemma3:1b", 12, MUTED, false);
        margin(progressStatus, content, 0, 14);
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
    }

    private void createServerPage() {
        serverPage = column();
        margin(text("로컬 Ollama 엔진", 20, WHITE, true), serverPage, 4, 8);
        serverStatus = text("상태 확인 중", 15, ACCENT, true); margin(serverStatus, serverPage, 0, 8);
        LinearLayout controls = row();
        Button start = button("서버 시작", true), stop = button("서버 종료", false);
        rowItem(controls, start, 1, 7); rowItem(controls, stop, 1, 0);
        margin(controls, serverPage, 0, 12);
        start.setOnClickListener(v -> startServer());
        stop.setOnClickListener(v -> stopServer());
        margin(text("모델 저장: 이 앱의 비공개 저장소\nAPI: http://127.0.0.1:11434\n추론: ARM64 CPU · 컨텍스트 2048\n외부 네트워크 공개 안 함",
            13, MUTED, false), serverPage, 1, 17);
        margin(text("실행 로그", 16, WHITE, true), serverPage, 0, 6);
        ScrollView logScroll = new ScrollView(this); logScroll.setBackground(bg(CARD, 12));
        logText = text("로딩 중", 11, MUTED, false); logText.setPadding(dp(12), dp(12), dp(12), dp(12));
        logScroll.addView(logText); serverPage.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showTab(int index) {
        chatPage.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        modelPage.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        serverPage.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
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
        pullBusy = true; pullCancelled = false;
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
                    ui.post(() -> progressStatus.setText(info));
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
                if (failure == null && !pullCancelled) refreshModels();
            });
        });
    }

    private String historyKey() { return "chat_" + currentModel; }
    private void loadHistory() {
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
        try { history.put(new JSONObject().put("role", role).put("content", content)); storeHistory(); }
        catch (Exception ignored) { }
    }
    private void renderHistory() {
        if (messages == null) return;
        messages.removeAllViews();
        if (history.length() == 0) {
            margin(text("Ollama가 이 스마트폰에서 직접 답변합니다.\n먼저 모델 탭에서 작은 모델을 설치하세요.",
                14, MUTED, false), messages, 13, 12);
        }
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item != null) bubble(item.optString("role"), item.optString("content"));
        }
        scrollBottom();
    }
    private TextView bubble(String role, String content) {
        boolean user = "user".equals(role);
        TextView t = text((user ? "나" : "Ollama") + "\n" + content, 14, WHITE, false);
        t.setPadding(dp(13), dp(11), dp(13), dp(11));
        t.setBackground(bg(user ? Color.rgb(32, 79, 76) : FIELD, 13));
        margin(t, messages, 3, 7);
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
                HttpURLConnection connection = OllamaHttp.newChatConnection(); chatConnection = connection;
                OllamaHttp.stream(connection, OllamaHttp.chatBody(modelAtStart, requestHistory), item -> {
                    JSONObject message = item.optJSONObject("message");
                    if (message != null) {
                        String part = message.optString("content", "");
                        if (!part.isEmpty()) {
                            answer.append(part);
                            String display = answer.toString();
                            ui.post(() -> { assistant.setText("Ollama\n" + display); scrollBottom(); });
                        }
                    }
                });
            } catch (Exception ex) { error = ex.getMessage(); }
            finally { chatConnection = null; }
            String finalError = error, finalAnswer = answer.toString();
            ui.post(() -> {
                if (!finalAnswer.isEmpty()) {
                    assistant.setText("Ollama\n" + finalAnswer + (chatCancelled ? "\n\n[생성 중단]" : ""));
                    // Keep incomplete responses visible but out of next turn's request context.
                    if (!chatCancelled && finalError == null && modelAtStart.equals(currentModel))
                        addHistory("assistant", finalAnswer);
                } else {
                    assistant.setText("Ollama\n" + (chatCancelled ? "생성 중단됨" : "오류: " + finalError));
                }
                setChatBusy(false);
                scrollBottom();
            });
        });
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
