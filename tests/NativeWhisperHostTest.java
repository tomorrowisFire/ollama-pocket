package com.ollamapocket.app;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the actual JNI boundary on a host; never represents an Android device test. */
public final class NativeWhisperHostTest {
    private static int checks;
    private static void require(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Whisper JNI check " + checks);
    }
    private static short[] fixture(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer wav = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int offset = 12; offset + 8 <= bytes.length;) {
            int size = wav.getInt(offset + 4);
            if (size < 0 || size > bytes.length - offset - 8) throw new IllegalArgumentException("fixture");
            if (new String(bytes, offset, 4, StandardCharsets.US_ASCII).equals("data")) {
                short[] pcm = new short[size / 2];
                wav.position(offset + 8);
                wav.asShortBuffer().get(pcm);
                return pcm;
            }
            offset += 8 + size + (size & 1);
        }
        throw new IllegalArgumentException("fixture");
    }
    private static boolean recognized(byte[] bytes) {
        if (bytes == null) return false;
        String text = new String(bytes, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
        return text.contains("ask not") && text.contains("country");
    }
    public static void main(String[] args) throws Exception {
        require(NativeWhisper.available());
        short[] pcm = fixture(Path.of(args[1]));
        long[] jobs = new long[4];
        for (int i = 0; i < jobs.length; i++) { jobs[i] = NativeWhisper.create(); require(jobs[i] > 0); }
        require(NativeWhisper.create() == 0);
        for (long job : jobs) NativeWhisper.destroy(job);
        NativeWhisper.cancel(0); NativeWhisper.destroy(Long.MAX_VALUE);
        require(NativeWhisper.transcribe(0, args[0], pcm, pcm.length, "en", 2) == null);
        long job = NativeWhisper.create();
        try {
            require(NativeWhisper.transcribe(job, args[0], null, pcm.length, "en", 2) == null);
            require(NativeWhisper.transcribe(job, null, pcm, pcm.length, "en", 2) == null);
            require(NativeWhisper.transcribe(job, args[0], pcm, 480001, "en", 2) == null);
            require(NativeWhisper.transcribe(job, args[0], pcm, 0, "en", 2) == null);
            require(NativeWhisper.transcribe(job, args[0], pcm, pcm.length, null, 2) == null);
            NativeWhisper.cancel(job);
            require(NativeWhisper.transcribe(job, args[0], pcm, pcm.length, "en", 2) == null);
        } finally { NativeWhisper.destroy(job); }
        job = NativeWhisper.create();
        try {
            require(recognized(NativeWhisper.transcribe(job, args[0], pcm, pcm.length, "en", 4)));
            require(NativeWhisper.transcribe(job, args[0], pcm, pcm.length, "en", 4) == null);
        } finally { NativeWhisper.destroy(job); }

        long racing = NativeWhisper.create();
        CountDownLatch starting = new CountDownLatch(1);
        AtomicReference<byte[]> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                starting.countDown();
                result.set(NativeWhisper.transcribe(racing, args[0], pcm, pcm.length, "en", 2));
            } catch (Throwable error) { failure.set(error); }
        });
        worker.start(); starting.await();
        Thread.sleep(25L);
        NativeWhisper.cancel(racing); NativeWhisper.destroy(racing);
        worker.join(130000L);
        require(!worker.isAlive() && failure.get() == null);
        require(result.get() == null || recognized(result.get()));
        require(NativeWhisper.transcribe(racing, args[0], pcm, pcm.length, "en", 2) == null);
        System.out.println("PASS: " + checks + " Whisper JNI boundary and real-model checks");
    }
}
