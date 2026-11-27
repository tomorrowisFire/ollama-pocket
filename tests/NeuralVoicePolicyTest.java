package com.ollamapocket.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public final class NeuralVoicePolicyTest {
    private static int checks;
    private static final BooleanSupplier LIVE=()->false;
    interface Checked { void run() throws Exception; }
    static void ok(boolean value) { if (!value) throw new AssertionError("check "+checks); checks++; }
    static void rejects(Class<? extends Throwable> type, Checked f) throws Exception {
        try { f.run(); } catch (Throwable t) { if(!type.isInstance(t)) throw new AssertionError(t); checks++; return; }
        throw new AssertionError("Expected "+type.getName());
    }
    static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    public static void main(String[] args) throws Exception {
        String[] tags={"ko-KR","en-US","zh-Hans","zh-Hant","ja-JP","fr-FR","und"};
        String[] expected={"ko","en","zh","zh","ja",null,null};
        for (int i=0;i<tags.length;i++) {
            Locale l=Locale.forLanguageTag(tags[i]);
            if(expected[i]==null) rejects(IllegalArgumentException.class,()->NeuralVoicePolicy.whisperLanguage(l));
            else ok(expected[i].equals(NeuralVoicePolicy.whisperLanguage(l)));
            if(expected[i]==null || "zh".equals(expected[i])) rejects(IllegalArgumentException.class,()->NeuralVoicePolicy.supertonicLanguage(l));
            else ok(expected[i].equals(NeuralVoicePolicy.supertonicLanguage(l)));
        }
        rejects(IllegalArgumentException.class,()->NeuralVoicePolicy.whisperLanguage(null));
        for (int i=-32;i<=64;i++) ok(NeuralVoicePolicy.threads(i)==Math.max(1,Math.min(4,i)));
        short[] full=new short[NeuralVoicePolicy.MAX_CAPTURE];
        for (int i=0;i<65536;i++) full[i]=(short)(i-32768);
        float[] converted=NeuralVoicePolicy.capture(full,full.length,LIVE);
        for(int i=0;i<65536;i++) ok(converted[i]==full[i]/32768f);
        short[] round=NeuralVoicePolicy.output(converted,16000,LIVE);
        for(int i=0;i<65536;i++) ok(Math.abs(round[i]-full[i])<=1);
        for(int n:new int[]{-1,0,3999,480001,Integer.MAX_VALUE}) rejects(IllegalArgumentException.class,()->NeuralVoicePolicy.capture(full,n,LIVE));
        rejects(IllegalArgumentException.class,()->NeuralVoicePolicy.capture(new short[10],4000,LIVE));
        rejects(CancellationException.class,()->NeuralVoicePolicy.capture(full,4000,()->true));
        rejects(IllegalArgumentException.class,()->NeuralVoicePolicy.capture(full,4000,null));
        for(float v:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) rejects(IllegalArgumentException.class,()->NeuralVoicePolicy.output(new float[]{v},24000,LIVE));
        ok(Arrays.equals(NeuralVoicePolicy.output(new float[]{-2,-1,0,1,2},24000,LIVE),new short[]{-32768,-32768,0,32767,32767}));
        for(int rate:new int[]{0,7999,48001,Integer.MAX_VALUE}) rejects(IllegalArgumentException.class,()->NeuralVoicePolicy.output(new float[]{0},rate,LIVE));
        rejects(IllegalArgumentException.class,()->NeuralVoicePolicy.output(new float[480001],8000,LIVE));
        rejects(CancellationException.class,()->NeuralVoicePolicy.output(new float[]{0},24000,()->true));
        for(float v:new float[]{Float.NaN,Float.POSITIVE_INFINITY,0.79f,1.41f}) rejects(IllegalArgumentException.class,()->NeuralVoicePolicy.speed(v));
        ok(NeuralVoicePolicy.speed(0.8f)==0.8f && NeuralVoicePolicy.speed(1.4f)==1.4f);
        for(String s:new String[]{null,"","\u200B","\u00A0","\u0000","\uD800","x".repeat(241)}) rejects(IllegalArgumentException.class,()->NeuralVoicePolicy.speechText(s));
        for(String s:new String[]{"안녕하세요","Hello","日本語","繁體中文","x".repeat(240),"😀"}) ok(s.equals(NeuralVoicePolicy.speechText(s)));
        NeuralVoicePolicy.Lease first=NeuralVoicePolicy.acquire(LIVE);
        rejects(IllegalStateException.class,()->NeuralVoicePolicy.acquire(LIVE));
        first.close();
        NeuralVoicePolicy.Lease second=NeuralVoicePolicy.acquire(LIVE);
        first.close();
        rejects(IllegalStateException.class,()->NeuralVoicePolicy.acquire(LIVE));
        second.close();
        rejects(CancellationException.class,()->NeuralVoicePolicy.acquire(()->true));
        NeuralVoicePolicy.acquire(LIVE).close();
        Path root=Files.createTempDirectory("voice-fixture");
        try {
            byte[] bytes={1,2,3,4};
            Path file=root.resolve("model.onnx"); Files.write(file,bytes);
            VerifiedSpeechFiles.Pin pin=new VerifiedSpeechFiles.Pin("model.onnx",bytes.length,hash(bytes));
            VerifiedSpeechFiles pack=VerifiedSpeechFiles.verify(root,List.of(pin),LIVE);
            ok(pack.path("model.onnx").equals(file.toRealPath().toString()));
            rejects(IllegalArgumentException.class,()->pack.path("other.onnx"));
            rejects(IllegalArgumentException.class,()->VerifiedSpeechFiles.verify(root,List.of(pin,pin),LIVE));
            for(String n:new String[]{"../model.onnx","/model.onnx","..",".hidden","a/b","a\\b","a..b"}) rejects(IllegalArgumentException.class,()->new VerifiedSpeechFiles.Pin(n,4,pin.sha256));
            rejects(IllegalArgumentException.class,()->new VerifiedSpeechFiles.Pin("a",VerifiedSpeechFiles.MAX_FILE+1,pin.sha256));
            rejects(IllegalArgumentException.class,()->new VerifiedSpeechFiles.Pin("a",4,"wrong"));
            rejects(IllegalArgumentException.class,()->VerifiedSpeechFiles.verify(root,List.of(new VerifiedSpeechFiles.Pin("a",VerifiedSpeechFiles.MAX_FILE,pin.sha256),new VerifiedSpeechFiles.Pin("b",VerifiedSpeechFiles.MAX_FILE,pin.sha256),pin),LIVE));
            rejects(CancellationException.class,()->pack.recheck(()->true));
            Files.write(file,new byte[]{4,3,2,1}); rejects(IOException.class,()->pack.recheck(LIVE));
            Files.write(file,new byte[]{1}); rejects(IOException.class,()->pack.recheck(LIVE));
            Files.write(file,bytes); pack.recheck(LIVE); checks++;
            Path target=root.resolve("target");Files.write(target,bytes);Files.delete(file);Files.createSymbolicLink(file,target);
            rejects(IOException.class,()->pack.recheck(LIVE));
            Files.delete(file);Files.delete(target);
            rejects(IOException.class,()->pack.recheck(LIVE));
        } finally {
            try(var stream=Files.list(root)) { for(Path p:stream.toArray(Path[]::new)) Files.deleteIfExists(p); }
            Files.deleteIfExists(root);
        }
        System.out.println("PASS: "+checks+" bounded neural voice policy and integrity assertions");
    }
}
