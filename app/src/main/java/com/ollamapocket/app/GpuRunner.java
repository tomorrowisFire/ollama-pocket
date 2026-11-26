package com.ollamapocket.app;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Optional Vulkan llama.cpp runtime. Ollama remains the model downloader/CPU runner.
 * The mere presence of Vulkan features is NOT proof of GPU execution: GPU is
 * reported as active only when the runner logged actual layer offload and its
 * HTTP health endpoint answers successfully.
 * This service only binds loopback, and is disabled when the binary is absent.
 */
public final class GpuRunner {
    private static final int PORT = 11436;
    private static volatile Process process;
    private static volatile String activeModel = "";
    private static volatile boolean offloadConfirmed;
    private static final StringBuilder logBuffer = new StringBuilder();
    private static volatile int generation;
    private GpuRunner() { }

    public static boolean packaged(Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir, "libllama-vulkan-server.so").isFile();
    }

    public static synchronized String logs() { return logBuffer.toString(); }
    private static synchronized void append(String msg) {
        Log.i("AiPocketGPU", msg);
        logBuffer.append(msg).append('\n');
        if (logBuffer.length() > 9000) logBuffer.delete(0, 4500);
        String lc = msg.toLowerCase(java.util.Locale.ROOT);
        // llama.cpp normally writes: "load_tensors: offloaded 25/25 layers to GPU"
        if (lc.matches(".*offloaded\\s+[1-9][0-9]*/[0-9]+\\s+layers?\\s+to\\s+gpu.*")
                || lc.matches(".*offloading\\s+[1-9][0-9]*\\s+layers?\\s+to\\s+gpu.*")) {
            offloadConfirmed = true;
        }
    }

    /** Resolve an Ollama-downloaded model from its manifest, never accepting arbitrary paths. */
    private static File modelFile(Context context, String model) throws Exception {
        // Names like qwen2.5:1.5b or username/custom:latest. Only default registry supported.
        if (model == null || !model.matches("[A-Za-z0-9._/-]{1,90}(:[A-Za-z0-9._-]{1,45})?"))
            throw new Exception("지원하지 않는 모델 이름");
        String[] parts = model.split(":", 2);
        String tag = parts.length > 1 ? parts[1] : "latest";
        String name = parts[0];
        File modelRoot = new File(context.getFilesDir(), "models");
        File manifests = new File(modelRoot, "manifests/registry.ollama.ai/library");
        File json = new File(manifests, name + "/" + tag);
        if (!json.isFile()) throw new Exception("Ollama 모델 매니페스트를 찾지 못했습니다. 일반 library GGUF 모델만 GPU 사용 가능");
        if (!json.getCanonicalPath().startsWith(manifests.getCanonicalPath() + File.separator))
            throw new Exception("잘못된 모델 경로");
        byte[] data = new byte[(int)Math.min(json.length(), 2_000_000L)];
        if (json.length() > data.length) throw new Exception("모델 매니페스트가 너무 큽니다");
        try (FileInputStream in = new FileInputStream(json)) {
            int count = 0, n;
            while (count < data.length && (n = in.read(data, count, data.length - count)) > 0) count += n;
            if (count != data.length) throw new Exception("모델 매니페스트 읽기 오류");
        }
        JSONArray layers = new JSONObject(new String(data, StandardCharsets.UTF_8)).getJSONArray("layers");
        for (int i = 0; i < layers.length(); i++) {
            JSONObject layer = layers.getJSONObject(i);
            if (!"application/vnd.ollama.image.model".equals(layer.optString("mediaType"))) continue;
            String digest = layer.optString("digest", "");
            if (!digest.matches("sha256:[0-9a-fA-F]{64}")) continue;
            File weights = new File(modelRoot, "blobs/sha256-" + digest.substring(7));
            if (weights.isFile() && weights.length() > 1048576) return weights;
        }
        throw new Exception("GGUF 가중치 파일을 찾을 수 없습니다");
    }

    private static boolean healthy() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL("http://127.0.0.1:" + PORT + "/health").openConnection();
            connection.setConnectTimeout(900);
            connection.setReadTimeout(900);
            int status = connection.getResponseCode();
            return status == 200;
        } catch (Exception ignore) { return false; }
        finally { if (connection != null) connection.disconnect(); }
    }

    /** Called on a worker thread. Returns true only after verified GPU offload. */
    public static boolean ensure(Context context, String model, StringBuilder reason) {
        InferenceSafetyPolicy.Reason risk=InferenceSafety.check(context);
        if(risk!=InferenceSafetyPolicy.Reason.NONE){reason.append(OnHandsLocale.wrap(context).getString(InferenceSafety.label(risk)));return false;}
        if (!packaged(context)) {
            reason.append("GPU 추론 실행 파일이 포함되지 않았습니다"); return false;
        }
        if (process != null && process.isAlive() && activeModel.equals(model)
                && offloadConfirmed && healthy()) return true;
        synchronized (GpuRunner.class) { stopLocked(); }
        File weights;
        try { weights = modelFile(context, model); }
        catch (Exception e) { reason.append(e.getMessage()); return false; }
        try {
            File executable = new File(context.getApplicationInfo().nativeLibraryDir, "libllama-vulkan-server.so");
            ProcessBuilder pb = new ProcessBuilder(executable.getAbsolutePath(),
                    "-m", weights.getAbsolutePath(), "--host", "127.0.0.1", "--port", String.valueOf(PORT),
                    "-ngl", "99", "-c", "2048", "--threads", "4");
            pb.redirectErrorStream(true);
            String libDir = context.getApplicationInfo().nativeLibraryDir;
            pb.environment().put("LD_LIBRARY_PATH", libDir);
            pb.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
            pb.environment().put("HOME", context.getFilesDir().getAbsolutePath());
            pb.directory(context.getFilesDir());
            Process current;
            int myGeneration;
            synchronized (GpuRunner.class) {
                if(InferenceSafety.blocked(context))throw new java.io.IOException("Unsafe inference resources");
                process = pb.start();
                activeModel = model;
                offloadConfirmed = false;
                myGeneration = ++generation;
                current = process;
            }
            new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        current.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (myGeneration == generation) append(line);
                    }
                } catch (Exception e) { append("GPU logs: " + e.getMessage()); }
            }, "gpu-llama-logs").start();
            long end = SystemClock.elapsedRealtime() + 35000;
            while (SystemClock.elapsedRealtime() < end) {
                if (process != current || !current.isAlive()) break;
                if(InferenceSafety.blocked(context)){reason.append("Unsafe inference resources");synchronized(GpuRunner.class){if(process==current)stopLocked();}return false;}
                if (healthy() && offloadConfirmed) return true;
                Thread.sleep(500);
            }
            if (!current.isAlive()) reason.append("GPU 실행 파일이 종료됐습니다. Vulkan 드라이버·모델 호환 확인 필요");
            else if (healthy()) reason.append("서버는 시작됐지만 GPU 레이어 오프로드는 확인되지 않았습니다");
            else reason.append("GPU 서버 시작 실패 또는 시간 초과");
            synchronized (GpuRunner.class) { stopLocked(); }
            return false;
        } catch (Exception e) {
            reason.append("GPU 실행 실패: ").append(e.getMessage());
            synchronized (GpuRunner.class) { stopLocked(); } return false;
        }
    }

    public static synchronized void stop() { stopLocked(); }
    private static void stopLocked() {
        generation++;
        Process old = process;
        process = null;
        activeModel = "";
        offloadConfirmed = false;
        if (old != null) old.destroy();
    }

    public static String url() { return "http://127.0.0.1:" + PORT; }
}
