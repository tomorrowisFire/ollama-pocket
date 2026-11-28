package com.ollamapocket.app;

import java.util.Arrays;
import java.util.Locale;

public final class WhisperCapturePolicyTest {
    private static int checks;
    private static void require(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Whisper capture check " + checks);
    }
    public static void main(String[] args) {
        require("ko".equals(WhisperCapturePolicy.language(Locale.KOREA)));
        require("en".equals(WhisperCapturePolicy.language(Locale.US)));
        require("ja".equals(WhisperCapturePolicy.language(Locale.JAPAN)));
        require("zh".equals(WhisperCapturePolicy.language(Locale.SIMPLIFIED_CHINESE)));
        require("zh".equals(WhisperCapturePolicy.language(Locale.TRADITIONAL_CHINESE)));
        require(WhisperCapturePolicy.language(Locale.FRENCH) == null);
        require(WhisperCapturePolicy.language(null) == null);
        for (int i = -10; i < 100; i++) require(WhisperCapturePolicy.threads(i) == Math.max(1, Math.min(4, i)));
        require(!WhisperCapturePolicy.validSamples(480000, 15999));
        require(WhisperCapturePolicy.validSamples(480000, 16000));
        require(WhisperCapturePolicy.validSamples(480000, 480000));
        require(!WhisperCapturePolicy.validSamples(480001, 480000));
        require(!WhisperCapturePolicy.validSamples(479999, 480000));
        require(!WhisperCapturePolicy.validSamples(480000, 480001));
        short[] quiet = new short[320], loud = new short[320];
        Arrays.fill(loud, (short) 300);
        require(!WhisperCapturePolicy.voiceFrame(quiet, 0, 320));
        require(WhisperCapturePolicy.voiceFrame(loud, 0, 320));
        Arrays.fill(loud, Short.MIN_VALUE);
        require(WhisperCapturePolicy.voiceFrame(loud, 0, 320));
        require(!WhisperCapturePolicy.voiceFrame(loud, -1, 1));
        require(!WhisperCapturePolicy.voiceFrame(loud, 0, 321));
        require(!WhisperCapturePolicy.voiceFrame(loud, 320, 1));
        require(!WhisperCapturePolicy.voiceFrame(null, 0, 1));
        WhisperCapturePolicy.Endpoint endpoint = new WhisperCapturePolicy.Endpoint();
        for (int i = 0; i < 9; i++) { endpoint.accept(loud, 0, 320); require(!endpoint.hasSpeech()); }
        endpoint.accept(loud, 0, 320);
        require(endpoint.hasSpeech());
        for (int i = 0; i < 59; i++) { endpoint.accept(quiet, 0, 320); require(!endpoint.ended()); }
        endpoint.accept(quiet, 0, 320); require(endpoint.ended());
        WhisperCapturePolicy.Endpoint noSpeech = new WhisperCapturePolicy.Endpoint();
        for (int i = 0; i < 1499; i++) { noSpeech.accept(quiet, 0, 320); require(!noSpeech.ended()); }
        noSpeech.accept(quiet, 0, 320);
        require(noSpeech.ended() && !noSpeech.hasSpeech());
        try { noSpeech.accept(quiet, 0, 1); throw new AssertionError("cap"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { new WhisperCapturePolicy.Endpoint().accept(quiet, 0, 0); throw new AssertionError("empty"); }
        catch (IllegalArgumentException expected) { checks++; }
        WhisperCapturePolicy.Endpoint continuous = new WhisperCapturePolicy.Endpoint(true);
        int idleFrames=(int)(WhisperCapturePolicy.CONTINUOUS_IDLE_MS*WhisperCapturePolicy.SAMPLE_RATE/1000/WhisperCapturePolicy.FRAME_SAMPLES);
        for(int i=0;i<idleFrames-1;i++){continuous.accept(quiet,0,320);require(!continuous.ended());}
        continuous.accept(quiet,0,320);require(continuous.ended()&&continuous.idleExpired()&&!continuous.hasSpeech());
        WhisperCapturePolicy.Endpoint lateSpeech = new WhisperCapturePolicy.Endpoint(true);
        for(int i=0;i<idleFrames-10;i++)lateSpeech.accept(quiet,0,320);
        for(int i=0;i<10;i++)lateSpeech.accept(loud,0,320);
        require(lateSpeech.hasSpeech()&&!lateSpeech.idleExpired()&&!lateSpeech.ended());
        for(int i=0;i<59;i++){lateSpeech.accept(quiet,0,320);require(!lateSpeech.ended());}
        lateSpeech.accept(quiet,0,320);require(lateSpeech.ended()&&!lateSpeech.idleExpired());
        System.out.println("PASS: " + checks + " Whisper capture policy checks");
    }
}
