package com.ollamapocket.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground-only capture and bounded local Whisper inference. No audio file or network I/O. */
final class WhisperCapture {
    interface Callback {
        void onReady();
        void onProcessing();
        void onResult(String text);
        void onError();
        default void onNoSpeech() { onError(); }
    }

    // Reject overlapping work rather than accumulating captures or native model allocations.
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "OnHand-Whisper");
        thread.setDaemon(true);
        return thread;
    });
    private final Activity owner;
    private final VoiceModelStore models;
    private final String language;
    private final boolean continuous;
    private static final class NoSpeech extends IllegalStateException { }
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean cancelled, finishRequested, running;
    private long nativeJob;

    WhisperCapture(Activity owner, VoiceModelStore models, Locale locale, Callback callback) {
        this(owner,models,locale,false,callback);
    }

    WhisperCapture(Activity owner, VoiceModelStore models, Locale locale, boolean continuous, Callback callback) {
        this.owner = owner;
        this.continuous = continuous;
        this.models = models;
        this.language = WhisperCapturePolicy.language(locale);
        this.callback = callback;
    }

    boolean start() {
        if (!started.compareAndSet(false, true) || cancelled || language == null ||
                !foreground() ||
                owner.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                !BUSY.compareAndSet(false, true)) return false;
        running = true;
        try { WORKER.execute(this::run); return true; }
        catch (RuntimeException | OutOfMemoryError error) { running = false; BUSY.set(false); return false; }
    }

    boolean isRunning() { return running && !cancelled; }

    void finish() { finishRequested = true; }

    synchronized void cancel() {
        cancelled = true;
        // Never free an in-flight native context or AudioRecord from the UI thread.
        if (nativeJob != 0) NativeWhisper.cancel(nativeJob);
    }

    private boolean foreground() {
        // Dialogs in this activity own window focus during workspace dictation.
        // MainActivity/Workspace lifecycle owners cancel the session on disappearance.
        return !owner.isFinishing() && !owner.isDestroyed();
    }

    private void post(Runnable action) {
        main.post(() -> { if (!cancelled && foreground()) action.run(); });
    }

    private void run() {
        short[] pcm = null;
        String result = null;
        boolean failed = false, noSpeech = false;
        VoiceNativeWork.Lease work = null;
        try (VoiceModelStore.Lease model = models.acquire(VoiceModelCatalog.WHISPER, () -> cancelled)) {
            if (cancelled) return;
            // First class access loads the library here, on the worker, never in start().
            if (!NativeWhisper.available()) throw new IllegalStateException("Whisper unavailable");
            pcm = new short[WhisperCapturePolicy.MAX_SAMPLES];
            int count = record(pcm);
            if (cancelled) return;
            if (!WhisperCapturePolicy.validSamples(pcm.length, count)) throw new IllegalStateException("Recording bounds");
            post(callback::onProcessing);
            work = VoiceNativeWork.acquire(() -> cancelled);
            synchronized (this) {
                if (cancelled) return;
                nativeJob = NativeWhisper.create();
                if (nativeJob == 0) throw new IllegalStateException("Whisper busy");
            }
            byte[] utf8 = NativeWhisper.transcribe(nativeJob, model.file("ggml-tiny.bin").getAbsolutePath(),
                    pcm, count, language, WhisperCapturePolicy.threads(Runtime.getRuntime().availableProcessors()));
            if (cancelled) return;
            if (utf8 == null || utf8.length == 0 || utf8.length > WhisperCapturePolicy.MAX_OUTPUT_BYTES)
                throw new IllegalStateException("Whisper output");
            try {
                result = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(utf8)).toString();
            } finally { Arrays.fill(utf8, (byte) 0); }
            result = VoiceInteractionPolicy.finalTranscript(result, WhisperCapturePolicy.MAX_OUTPUT_CHARS);
        } catch (NoSpeech empty) {
            noSpeech = !cancelled;
        } catch (Exception | LinkageError | OutOfMemoryError error) {
            // Audio, model paths, and recognized text are never included in logs or errors.
            failed = !cancelled;
        } finally {
            synchronized (this) {
                if (nativeJob != 0) {
                    try { NativeWhisper.destroy(nativeJob); } catch (LinkageError ignored) { }
                    nativeJob = 0;
                }
            }
            if (work != null) work.close();
            if (pcm != null) Arrays.fill(pcm, (short) 0);
            running = false;
            BUSY.set(false);
        }
        if (cancelled) return;
        if (noSpeech) post(callback::onNoSpeech);
        else if (failed || result == null) post(callback::onError);
        else {
            final String text = result;
            post(() -> callback.onResult(text));
        }
    }

    private int record(short[] pcm) throws InterruptedException {
        int minimum = AudioRecord.getMinBufferSize(WhisperCapturePolicy.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0 || minimum > 1024 * 1024) throw new IllegalStateException("Microphone unavailable");
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                WhisperCapturePolicy.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, 4096));
        int count = 0;
        WhisperCapturePolicy.Endpoint endpoint = new WhisperCapturePolicy.Endpoint(continuous);
        try {
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED || cancelled)
                throw new IllegalStateException("Microphone unavailable");
            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                throw new IllegalStateException("Microphone unavailable");
            post(callback::onReady);
            final long startedAt = SystemClock.elapsedRealtime();
            final long deadline = startedAt + 30000L;
            while (!cancelled && !finishRequested && count < pcm.length &&
                    SystemClock.elapsedRealtime() < deadline) {
                if (continuous && !endpoint.hasSpeech() &&
                        SystemClock.elapsedRealtime() - startedAt >= WhisperCapturePolicy.CONTINUOUS_IDLE_MS) break;
                int wanted = Math.min(WhisperCapturePolicy.FRAME_SAMPLES, pcm.length - count);
                int read = recorder.read(pcm, count, wanted, AudioRecord.READ_NON_BLOCKING);
                if (read < 0) throw new IllegalStateException("Microphone read failed");
                if (read == 0) { Thread.sleep(10L); continue; }
                endpoint.accept(pcm, count, read);
                count += read;
                if (endpoint.ended()) break;
            }
        } finally {
            try { recorder.stop(); } catch (RuntimeException ignored) { }
            recorder.release();
        }
        if (!cancelled && !endpoint.hasSpeech()) throw new NoSpeech();
        return count;
    }
}
