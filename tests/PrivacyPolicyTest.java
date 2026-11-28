package com.ollamapocket.app;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public final class PrivacyPolicyTest {
    private static int n;
    static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    public static void main(String[] args)throws Exception{
        ok(PrivacyPolicy.validApiKey("sk-test-123_456"));
        ok(PrivacyPolicy.validApiKey("x".repeat(4096)));
        for(String key:new String[]{null,"","a b","a\tb","a\nb","a\rb","a\u0000b","a\u007fb","한글","x".repeat(4097)})
            ok(!PrivacyPolicy.validApiKey(key));
        ok(PrivacyPolicy.validProviderScope("OPENAI"));ok(PrivacyPolicy.validProviderScope("OPENROUTER"));
        ok(!PrivacyPolicy.validProviderScope("PC_OLLAMA"));ok(!PrivacyPolicy.validProviderScope(null));
        ok(PrivacyPolicy.validModelId("qwen2.5:7b"));ok(PrivacyPolicy.validModelId("org/model:free"));
        ok(!PrivacyPolicy.validModelId("a\nb"));ok(!PrivacyPolicy.validModelId(" a"));
        ok(!PrivacyPolicy.validModelId("a\u202eb"));ok(!PrivacyPolicy.validModelId(null));
        ok(!PrivacyPolicy.validModelId("a\u00a0b"));ok(!PrivacyPolicy.validModelId("a\ud800"));
        ok(!PrivacyPolicy.validModelId("a\udb40\udc01"));ok(PrivacyPolicy.validModelId("a😀"));
        long now=System.currentTimeMillis(),old=now-PrivacyPolicy.PDF_STALE_MS-10000;
        ok(PrivacyPolicy.stalePdfName("pdf-excerpt-42.pdf",old,now));
        ok(!PrivacyPolicy.stalePdfName("pdf-excerpt-42.pdf",now,now));
        ok(!PrivacyPolicy.stalePdfName("pdf-excerpt-42.pdf",now+1000,now));
        ok(!PrivacyPolicy.stalePdfName("pdf-excerpt-important.pdf",old,now));
        Path root=Files.createTempDirectory("onhand-privacy-test");
        try{
            Path stale=Files.write(root.resolve("pdf-excerpt-100.pdf"),new byte[]{1});
            Path fresh=Files.write(root.resolve("pdf-excerpt-200.pdf"),new byte[]{2});
            Path keep=Files.write(root.resolve("other.pdf"),new byte[]{3});
            Path folder=Files.createDirectory(root.resolve("pdf-excerpt-300.pdf"));
            Path link=Files.createSymbolicLink(root.resolve("pdf-excerpt-400.pdf"),keep);
            Files.setLastModifiedTime(stale,FileTime.fromMillis(old));
            Files.setLastModifiedTime(keep,FileTime.fromMillis(old));
            Files.setLastModifiedTime(folder,FileTime.fromMillis(old));
            ok(PrivacyPolicy.cleanAbandonedPdf(root.toFile(),now)==1);
            ok(!Files.exists(stale));ok(Files.exists(fresh));ok(Files.exists(keep));
            ok(Files.isDirectory(folder));ok(Files.isSymbolicLink(link));
            ok(PrivacyPolicy.cleanAbandonedPdf(root.toFile(),now)==0);
            ok(PrivacyPolicy.cleanAbandonedPdf(root.resolve("missing").toFile(),now)==0);
            ok(Files.exists(keep)&&Files.isSymbolicLink(link));
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){
            for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toArray(Path[]::new))Files.deleteIfExists(path);
        }}
        System.out.println("PASS: "+n+" credential and private-cache policy checks");
    }
}
