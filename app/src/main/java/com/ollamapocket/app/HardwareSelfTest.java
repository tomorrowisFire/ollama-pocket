package com.ollamapocket.app;

import android.content.Context;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.Locale;

/**
 * Tests the actual on-device inference paths, not just advertised hardware features.
 * Never uploads a diagnostic report or an input prompt; only the loopback servers are used.
 * Call on a background thread. The short test is not a thermal or long-term benchmark.
 */
public final class HardwareSelfTest {
    private HardwareSelfTest() { }

    public interface Progress { void onProgress(String message); }

    private static final String PROMPT = "숫자 1부터 5까지 쉼표로만 적어줘.";

    private static JSONArray messages() throws Exception {
        JSONArray list = new JSONArray();
        list.put(new JSONObject().put("role", "user").put("content", PROMPT));
        return list;
    }

    private static final class Trial {
        int chars;
        long millis;
        long firstTokenMillis;
        long evalCount;
        long evalDurationNanos;
        String preview;
        boolean hadResponse;
    }

    private static Trial cpuTrial(String model) throws Exception {
        Trial result = new Trial();
        long start = SystemClock.elapsedRealtime();
        long[] first = { -1 };
        StringBuilder preview = new StringBuilder();
        HttpURLConnection conn = OllamaHttp.newChatConnection();
        OllamaHttp.stream(conn, OllamaHttp.chatBody(model, messages()), item -> {
            JSONObject message = item.optJSONObject("message");
            if (message != null) {
                String text = message.optString("content", "");
                if (!text.isEmpty()) {
                    if (first[0] < 0) first[0] = SystemClock.elapsedRealtime() - start;
                    result.chars += text.length();
                    if (preview.length() < 120) preview.append(text, 0, Math.min(text.length(), 120 - preview.length()));
                }
            }
            if (item.optBoolean("done", false)) {
                result.evalCount = item.optLong("eval_count", 0);
                result.evalDurationNanos = item.optLong("eval_duration", 0);
            }
        });
        result.millis = SystemClock.elapsedRealtime() - start;
        result.firstTokenMillis = first[0];
        result.preview = preview.toString();
        result.hadResponse = result.chars > 0;
        return result;
    }

    private static Trial gpuTrial(String model) throws Exception {
        Trial result = new Trial();
        long start = SystemClock.elapsedRealtime();
        long[] first = { -1 };
        StringBuilder preview = new StringBuilder();
        HttpURLConnection conn = OllamaHttp.newGpuChatConnection();
        OllamaHttp.streamGpu(conn, OllamaHttp.gpuChatBody(model, messages()), item -> {
            JSONObject message = item.optJSONObject("message");
            if (message != null) {
                String text = message.optString("content", "");
                if (!text.isEmpty()) {
                    if (first[0] < 0) first[0] = SystemClock.elapsedRealtime() - start;
                    result.chars += text.length();
                    if (preview.length() < 120) preview.append(text, 0, Math.min(text.length(), 120 - preview.length()));
                }
            }
        });
        result.millis = SystemClock.elapsedRealtime() - start;
        result.firstTokenMillis = first[0];
        result.preview = preview.toString();
        result.hadResponse = result.chars > 0;
        return result;
    }

    private static String summary(String engine, Trial t) {
        StringBuilder line = new StringBuilder();
        line.append(engine).append(": ").append(t.hadResponse ? "응답 생성 성공" : "응답 없음");
        line.append(" · 총 ").append(t.millis).append("ms");
        if (t.firstTokenMillis >= 0) line.append(" · 첫 토큰까지 ").append(t.firstTokenMillis).append("ms");
        line.append(" · ").append(t.chars).append("글자");
        if (t.evalDurationNanos > 0 && t.evalCount > 0) {
            double tps = (t.evalCount * 1_000_000_000.0) / t.evalDurationNanos;
            line.append(" · 생성 ").append(t.evalCount).append("토큰 / ")
                    .append(String.format(Locale.US, "%.1f", tps)).append("토큰/초");
        }
        return line.toString();
    }

    public static String run(Context context, String model, Progress callback) {
        StringBuilder report = new StringBuilder();
        report.append("AI in Pocket · 기기 내 추론 실측 검사\n")
                .append("모델: ").append(model).append("\n")
                .append("단회 짧은 프롬프트 테스트이며 장시간 안정성·발열 보증이 아닙니다.\n\n");
        ComputeProfiler.Report device = ComputeProfiler.scan(context);
        report.append("[하드웨어] \n").append(device.description()).append("\n\n");
        if (model == null || model.isEmpty()) {
            return report.append("[중단] 설치된 모델을 먼저 선택하세요.\n").toString();
        }
        try {
            callback.onProgress("CPU · 실제 응답 생성 중...");
            Trial cpu = cpuTrial(model);
            report.append("[CPU 실측]\n").append(summary("Ollama", cpu)).append("\n");
            if (!cpu.hadResponse) report.append("CPU 모델 응답 없음 · 시험 실패\n");
        } catch (Exception e) {
            report.append("[CPU 실측] 실패: ").append(e.getClass().getSimpleName())
                    .append(" · ").append(e.getMessage()).append("\n");
        }
        report.append("\n");

        if (!GpuRunner.packaged(context)) {
            report.append("[GPU 실측] 건너뜀 · Vulkan 추론 실행파일이 APK에 없음\n");
        } else if (!device.gpuCompute) {
            report.append("[GPU 실측] 건너뜀 · Android Vulkan compute 기능 미보고\n");
        } else {
            callback.onProgress("GPU · 모델 준비 / 레이어 오프로드 검증 중 (기기에 따라 수십 초)");
            StringBuilder reason = new StringBuilder();
            long begin = SystemClock.elapsedRealtime();
            boolean ready = GpuRunner.ensure(context, model, reason);
            long initMillis = SystemClock.elapsedRealtime() - begin;
            if (!ready) {
                report.append("[GPU 실측] GPU 활성화 실패 · 준비 ").append(initMillis)
                        .append("ms · ").append(reason).append("\n");
            } else {
                callback.onProgress("GPU · 오프로드 확인 완료, 실제 답변 생성 중...");
                try {
                    Trial gpu = gpuTrial(model);
                    report.append("[GPU 실측] GPU 오프로드 로그 + HTTP 준비 확인 · 초기화 ")
                            .append(initMillis).append("ms\n")
                            .append(summary("Vulkan llama.cpp", gpu)).append("\n");
                    if (!gpu.hadResponse) report.append("GPU 추론 미확인: 정상 토큰 응답 없음\n");
                    else report.append("GPU 검증: 오프로드 및 실제 토큰 생성 확인\n");
                } catch (Exception e) {
                    report.append("[GPU 실측] GPU 시작 확인 후 토큰 생성 실패: ")
                            .append(e.getClass().getSimpleName()).append(" · ")
                            .append(e.getMessage()).append("\n");
                }
            }
            GpuRunner.stop();
        }
        report.append("\n[NPU 실측]\n")
                .append("미실행 · 현재 앱에 NPU LLM 추론 백엔드 없음.\n")
                .append("NNAPI 가속기 후보: ")
                .append(device.nnapiQueried ? String.valueOf(device.nnapiAccelerators) : "조회 불가")
                .append("개 · 후보 검출은 NPU 추론 성공 증거가 아닙니다.\n\n")
                .append("이 보고서는 기기에만 생성되며 자동 전송되지 않습니다.\n");
        callback.onProgress("CPU·GPU 검사가 종료됐습니다. 결과를 복사할 수 있습니다.");
        return report.toString();
    }
}
