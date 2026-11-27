package com.ollamapocket.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** One worker owns the native engine, model lease and audio lifetime for a playback sequence. */
final class NeuralTtsPlayer implements AutoCloseable {
    interface Callback {
        void onStart(String utteranceId);
        void onDone(String utteranceId);
        void onError(String utteranceId);
    }

    // Only one resident TTS engine and at most one replacement waiting for an
    // already-closing engine. No unbounded work queue or retained utterances.
    private static final Object COORDINATOR = new Object();
    private static NeuralTtsPlayer active;
    private static NeuralTtsPlayer waiting;

    private final Object stateLock = new Object();
    private final Object audioLock = new Object();
    private final VoiceModelStore store;
    private final AudioManager audioManager;
    private final String language;
    private final float rate;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private long generation;
    private boolean started;
    private boolean closed;
    private boolean broken;
    private Job pending;
    private AudioTrack track;

    private static final class Job {
        final long token;
        final String text;
        final String id;
        volatile boolean focusLost;
        Job(long token, String text, String id) { this.token = token; this.text = text; this.id = id; }
    }

    NeuralTtsPlayer(Context context, VoiceModelStore store, Locale locale, float rate, Callback callback) {
        if (Build.VERSION.SDK_INT < 27 || context == null || store == null || callback == null ||
                !supports(locale) || !NeuralTtsPolicy.validRate(rate)) {
            throw new IllegalArgumentException("Invalid local TTS configuration");
        }
        // Do not retain the Activity. Native/model work only starts inside the worker.
        this.store = store;
        this.audioManager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        this.language = NeuralTtsPolicy.language(locale);
        this.rate = rate;
        this.callback = callback;
    }

    static boolean supports(Locale locale) { return !NeuralTtsPolicy.language(locale).isEmpty(); }

    boolean speak(String text, String utteranceId) {
        if (!NeuralTtsPolicy.validChunk(text) || utteranceId == null ||
                utteranceId.isEmpty() || utteranceId.length() > 128) return false;
        boolean launch;
        Job next;
        synchronized (stateLock) {
            if (closed || broken) return false;
            next = new Job(++generation, text, utteranceId);
            pending = null;
            launch = !started;
            started = true;
        }
        // Stop the previous track before waking the worker for the replacement.
        stopAudio();
        synchronized (stateLock) {
            if (closed || generation != next.token) return false;
            pending = next;
            stateLock.notifyAll();
        }
        if (!launch) return true;
        synchronized (COORDINATOR) {
            if (active == null) {
                active = this;
                return launchWorker();
            }
            if (active == this) return true;
            if (active.isClosed() && (waiting == null || waiting.isClosed())) {
                waiting = this;
                return true;
            }
        }
        synchronized (stateLock) { started = false; pending = null; ++generation; }
        return false;
    }

    void stop() {
        synchronized (stateLock) {
            ++generation;
            pending = null;
            stateLock.notifyAll();
        }
        stopAudio();
    }

    void cancel() { stop(); }

    @Override public void close() {
        synchronized (stateLock) {
            if (closed) return;
            closed = true;
            ++generation;
            pending = null;
            stateLock.notifyAll();
        }
        stopAudio();
    }

    private boolean isClosed() { synchronized (stateLock) { return closed; } }
    private boolean current(Job job) {
        synchronized (stateLock) { return !closed && generation == job.token; }
    }

    private boolean launchWorker() {
        try {
        Thread worker = new Thread(() -> {
            try { runSession(); }
            finally {
                synchronized (COORDINATOR) {
                    if (active == this) {
                        active = waiting;
                        waiting = null;
                        if (active != null && active.isClosed()) active = null;
                        if (active != null) active.launchWorker();
                    }
                }
            }
        }, "OnHand-neural-tts");
        worker.setDaemon(true);
        worker.start();
        return true;
        } catch (RuntimeException | OutOfMemoryError failure) {
            Job failed;
            synchronized (stateLock) { broken = true; failed = pending; pending = null; }
            synchronized (COORDINATOR) { if (active == this) active = null; }
            if (failed != null) post(failed, 2);
            return false;
        }
    }

    private void runSession() {
        VoiceModelStore.Lease model = null;
        VoiceNativeWork.Lease nativeWork = null;
        NativeNeuralTts engine = null;
        try {
            while (true) {
                Job job;
                synchronized (stateLock) {
                    while (!closed && pending == null) stateLock.wait();
                    if (closed) return;
                    job = pending;
                    pending = null;
                }
                try {
                    if (!current(job)) continue;
                    if (engine == null) {
                        if (nativeWork == null) nativeWork = VoiceNativeWork.acquire(() -> !current(job));
                        if (!current(job)) continue;
                        boolean melo = "zh".equals(language);
                        if (model == null) model = store.acquire(melo ? VoiceModelCatalog.MELO : VoiceModelCatalog.TTS,
                                () -> !current(job));
                        if (!current(job)) continue;
                        engine = melo ? NativeNeuralTts.openMelo(model.directory()) : new NativeNeuralTts(model.directory());
                    }
                    if (!current(job)) continue;
                    float[] samples = engine.generate(job.text, language, rate, () -> !current(job));
                    if (!current(job)) continue;
                    if (samples == null) throw new IOException("TTS returned no waveform");
                    play(samples, job);
                    post(job, 1);
                } catch (IOException | RuntimeException | LinkageError | OutOfMemoryError failure) {
                    if (!current(job)) continue;
                    synchronized (stateLock) { broken = true; pending = null; }
                    post(job, 2);
                    return;
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            synchronized (stateLock) { broken = true; pending = null; }
            // No callback can advance a cancelled or interrupted conversation.
        } finally {
            stopAudio();
            try { if (engine != null) engine.close(); }
            finally {
                try { if (model != null) model.close(); }
                finally { if (nativeWork != null) nativeWork.close(); }
            }
        }
    }

    private void post(Job job, int event) {
        main.post(() -> {
            if (!current(job)) return;
            if (event == 0) callback.onStart(job.id);
            else if (event == 1) callback.onDone(job.id);
            else callback.onError(job.id);
        });
    }

    private void stopAudio() {
        synchronized (audioLock) {
            if (track == null) return;
            try { track.pause(); } catch (IllegalStateException ignored) { }
            try { track.flush(); } catch (IllegalStateException ignored) { }
            try { track.stop(); } catch (IllegalStateException ignored) { }
        }
    }

    private void play(float[] samples, Job job) throws IOException, InterruptedException {
        if (!NeuralTtsPolicy.validPcm(samples, NeuralTtsPolicy.SAMPLE_RATE)) {
            throw new IOException("Invalid audio samples");
        }
        int minimum = AudioTrack.getMinBufferSize(NeuralTtsPolicy.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
        if (minimum <= 0 || minimum > 1024 * 1024) throw new IOException("Audio output unavailable");
        if (audioManager == null) throw new IOException("Audio focus unavailable");
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        AtomicBoolean focusOpen = new AtomicBoolean(true);
        AudioFocusRequest focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(false).setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> {
                    if (change <= AudioManager.AUDIOFOCUS_LOSS && focusOpen.get() && current(job)) {
                        job.focusLost = true;
                        stopAudio();
                    }
                }, main).build();
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusOpen.set(false);
            throw new IOException("Audio focus unavailable");
        }
        AudioTrack output = null;
        try {
            if (!current(job)) return;
            output = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(NeuralTtsPolicy.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(Math.max(minimum, 16 * 1024)).build();
            if (output.getState() != AudioTrack.STATE_INITIALIZED) throw new IOException("Audio output unavailable");
            synchronized (audioLock) {
                if (!current(job)) return;
                track = output;
                output.play();
            }
            post(job, 0);
            long deadline = SystemClock.elapsedRealtime() + NeuralTtsPolicy.drainBudgetMillis(samples.length);
            int written = 0;
            while (written < samples.length && current(job) && !job.focusLost) {
                // Nonblocking writes permit prompt stop and a bounded stalled-output timeout.
                int count = output.write(samples, written, Math.min(4096, samples.length - written),
                        AudioTrack.WRITE_NON_BLOCKING);
                if (count < 0) throw new IOException("Audio output failed");
                written += count;
                if (SystemClock.elapsedRealtime() > deadline) throw new IOException("Audio output timed out");
                if (count == 0) Thread.sleep(10L);
            }
            while (current(job) && !job.focusLost && Integer.toUnsignedLong(output.getPlaybackHeadPosition()) < samples.length) {
                if (SystemClock.elapsedRealtime() > deadline) throw new IOException("Audio output timed out");
                Thread.sleep(10L);
            }
            if (job.focusLost && current(job)) throw new IOException("Audio focus lost");
        } finally {
            focusOpen.set(false);
            try { audioManager.abandonAudioFocusRequest(focus); } catch (RuntimeException ignored) { }
            synchronized (audioLock) {
                if (output != null) {
                    if (track == output) track = null;
                    try { output.pause(); } catch (IllegalStateException ignored) { }
                    try { output.flush(); } catch (IllegalStateException ignored) { }
                    try { output.stop(); } catch (IllegalStateException ignored) { }
                    output.release();
                }
            }
        }
    }
}
