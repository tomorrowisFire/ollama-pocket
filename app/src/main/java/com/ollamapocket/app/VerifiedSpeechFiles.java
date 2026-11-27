package com.ollamapocket.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Integrity boundary for app-private model packs. Expected pins must originate in trusted app code,
 * never a model-provided manifest. Caller owns the pack exclusively for verification and inference. */
final class VerifiedSpeechFiles {
    static final long MAX_FILE = 256L * 1024 * 1024;
    static final long MAX_PACK = 512L * 1024 * 1024;
    static final int MAX_ENTRIES = 24;
    static final class Pin {
        final String name, sha256;
        final long bytes;
        Pin(String name, long bytes, String sha256) {
            if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,95}") || name.contains("..") ||
                bytes <= 0 || bytes > MAX_FILE || sha256 == null || !sha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Model pin");
            this.name=name; this.bytes=bytes; this.sha256=sha256;
        }
    }
    private final Path root;
    private final Map<String,Pin> pins;
    private VerifiedSpeechFiles(Path root, Map<String,Pin> pins) {
        this.root=root; this.pins=Collections.unmodifiableMap(new LinkedHashMap<>(pins));
    }
    static VerifiedSpeechFiles verify(Path privateRoot, List<Pin> expected, BooleanSupplier cancelled) throws IOException {
        NeuralVoicePolicy.checkCancelled(cancelled);
        if (privateRoot == null || expected == null || expected.isEmpty() || expected.size() > MAX_ENTRIES)
            throw new IllegalArgumentException("Model manifest");
        if (!Files.isDirectory(privateRoot, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing private model directory");
        Path root=privateRoot.toRealPath();
        long total=0;
        Map<String,Pin> pins=new LinkedHashMap<>();
        for (Pin p:expected) {
            if (p == null || pins.put(p.name,p)!=null) throw new IllegalArgumentException("Duplicate model pin");
            total += p.bytes;
            if (total > MAX_PACK) throw new IllegalArgumentException("Model pack limit");
        }
        VerifiedSpeechFiles pack=new VerifiedSpeechFiles(root,pins);
        pack.recheck(cancelled);
        return pack;
    }
    void recheck(BooleanSupplier cancelled) throws IOException {
        NeuralVoicePolicy.checkCancelled(cancelled);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.equals(root.toRealPath()))
            throw new IOException("Model directory changed");
        for (Pin p:pins.values()) {
            NeuralVoicePolicy.checkCancelled(cancelled);
            Path file=root.resolve(p.name);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file)!=p.bytes || !file.equals(file.toRealPath()))
                throw new IOException("Model file missing or changed: "+p.name);
            MessageDigest digest=sha256();
            long total=0;
            byte[] buffer=new byte[65536];
            try (InputStream in=Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                for (int n;(n=in.read(buffer))!=-1;) {
                    NeuralVoicePolicy.checkCancelled(cancelled);
                    total+=n;
                    if (total>p.bytes) throw new IOException("Model file exceeds pin");
                    digest.update(buffer,0,n);
                }
            }
            if (total!=p.bytes || !hex(digest.digest()).equals(p.sha256) || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)!=p.bytes)
                throw new IOException("Model integrity mismatch: "+p.name);
        }
        NeuralVoicePolicy.checkCancelled(cancelled);
    }
    String path(String name) {
        if (!pins.containsKey(name)) throw new IllegalArgumentException("Unverified model file");
        return root.resolve(name).toString();
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder s=new StringBuilder(bytes.length*2);
        for (byte b:bytes) { int v=b & 255; s.append(Character.forDigit(v>>>4,16)).append(Character.forDigit(v&15,16)); }
        return s.toString();
    }
}
