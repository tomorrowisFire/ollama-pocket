package com.ollamapocket.app;

/** Small JNI surface; native owns each context until its worker returns. */
final class NativeWhisper {
    private static final boolean AVAILABLE;
    static {
        boolean loaded = false;
        try { System.loadLibrary("onhand_whisper"); loaded = true; }
        catch (LinkageError | SecurityException ignored) { }
        AVAILABLE = loaded;
    }
    private NativeWhisper() {}
    static boolean available() { return AVAILABLE; }
    static native long create();
    static native byte[] transcribe(long job, String modelPath, short[] samples, int count,
                                    String language, int threads);
    static native void cancel(long job);
    static native void destroy(long job);
}
