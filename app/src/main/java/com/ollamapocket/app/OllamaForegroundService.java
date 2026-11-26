package com.ollamapocket.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Hosts the real Android ARM64 Ollama binary in a foreground service. */
public final class OllamaForegroundService extends Service {
    public static final String ACTION_START = "com.ollamapocket.START";
    public static final String ACTION_STOP = "com.ollamapocket.STOP";
    private static final String CHANNEL = "ollama_local_server";
    private static final int NOTIFICATION_ID = 50;
    private static volatile Process process;
    private static final StringBuilder recentLog = new StringBuilder();

    public static boolean isRunning() {
        Process p = process;
        return p != null && p.isAlive();
    }

    public static synchronized String logs() {
        return recentLog.toString();
    }

    private static synchronized void log(String line) {
        Log.i("AIinPocket", line);
        recentLog.append(line).append('\n');
        if (recentLog.length() > 6000) {
            recentLog.delete(0, recentLog.length() - 4000);
        }
    }

    public static File ollamaBinary(Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir, "libollama.so");
    }

    public static void configureEnvironment(Context context, ProcessBuilder pb) {
        String nativeDir = context.getApplicationInfo().nativeLibraryDir;
        File modelDir = new File(context.getFilesDir(), "models");
        if (!modelDir.exists() && !modelDir.mkdirs()) log("Model directory creation failed");
        Map<String, String> env = pb.environment();
        env.put("HOME", context.getFilesDir().getAbsolutePath());
        env.put("OLLAMA_MODELS", modelDir.getAbsolutePath());
        env.put("OLLAMA_HOST", "127.0.0.1:11434");
        env.put("OLLAMA_CONTEXT_LENGTH", "2048");
        env.put("OLLAMA_NUM_PARALLEL", "1");
        env.put("OLLAMA_MAX_LOADED_MODELS", "1");
        env.put("OLLAMA_VULKAN", "0");
        env.put("TMPDIR", context.getCacheDir().getAbsolutePath());
        env.put("XDG_CACHE_HOME", context.getCacheDir().getAbsolutePath());
        env.put("LD_LIBRARY_PATH", nativeDir);
        env.put("PATH", nativeDir + ":/system/bin:/system/xbin");
        pb.directory(context.getFilesDir());
        pb.redirectErrorStream(true);
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Ollama 로컬 실행", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("스마트폰에서 실행되는 Ollama 추론 서버");
            manager.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopNative();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        // Android requires foreground promotion promptly after startForegroundService.
        startForeground(NOTIFICATION_ID, notification());
        startNative();
        return START_NOT_STICKY;
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, OllamaForegroundService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("On Hands AI")
            .setContentText("이 기기에서 로컬 LLM 서버 실행 중 · 127.0.0.1")
            .setContentIntent(pi)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "중지", stopPi)
            .build();
    }

    private synchronized void startNative() {
        if (isRunning()) return;
        File bin = ollamaBinary(this);
        File runner = new File(getApplicationInfo().nativeLibraryDir, "libllama-server.so");
        if (!bin.isFile() || !runner.isFile()) {
            log("ERROR: ARM64 Ollama/runner payload missing in APK");
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath(), "serve");
            configureEnvironment(this, pb);
            process = pb.start();
            log("Started local Ollama process");
            final Process p = process;
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) log(line);
                } catch (Exception e) { log("Log reader: " + e); }
            }, "ollama-stdout").start();
            new Thread(() -> {
                try { log("Ollama process exited: " + p.waitFor()); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                if (process == p) {
                    process = null;
                    stopSelf();
                }
            }, "ollama-exit").start();
        } catch (Exception e) {
            log("ERROR starting Ollama: " + e);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private synchronized void stopNative() {
        GpuRunner.stop();
        Process p = process;
        process = null;
        if (p != null) {
            p.destroy();
            log("Stop requested");
        }
    }

    @Override public void onDestroy() {
        stopNative();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
