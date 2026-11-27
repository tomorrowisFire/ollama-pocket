package com.ollamapocket.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Candidate metadata, not a claim that an installed Android backend supports vision. */
final class VisionModelProfile {
    static final String ID = "smolvlm-256m-q8-b9e43796";
    static final String ALIAS = "onhand-smolvlm-256m-q8";
    static final String REVISION = "b9e4379657e1450d04d02eec8e345667265b0a00";
    static final String SOURCE = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/" + REVISION + "/";
    static final String LICENSE = "Apache-2.0";
    static final String BACKEND_COMMIT = "7aca0d2ea8d92b56911233b822db01f9fbb1ae50";
    static final Asset WEIGHTS = new Asset("SmolVLM-256M-Instruct-Q8_0.gguf",175054528L,
            "2a31195d3769c0b0fd0a4906201666108834848db768af11de1d2cef7cd35e65");
    static final Asset PROJECTOR = new Asset("mmproj-SmolVLM-256M-Instruct-Q8_0.gguf",103769856L,
            "7e943f7c53f0382a6fc41b6ee0c2def63ba4fded9ab8ed039cc9e2ab905e0edd");
    static final long TOTAL_BYTES = WEIGHTS.bytes + PROJECTOR.bytes;

    private VisionModelProfile() { }

    static final class Asset {
        final String name, sha256;
        final long bytes;
        Asset(String name, long bytes, String sha256) {
            if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,100}\\.gguf") ||
                    bytes < 4 || bytes > 1024L*1024*1024 ||
                    sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Vision asset identity");
            }
            this.name=name; this.bytes=bytes; this.sha256=sha256;
        }

        /** Full worker-side verification. The caller must own the private, immutable model directory. */
        File verify(File directory, BooleanSupplier cancelled) throws IOException {
            VisionImagePolicy.check(cancelled);
            if (directory == null || !directory.isDirectory() ||
                    !directory.getAbsoluteFile().equals(directory.getCanonicalFile()))
                throw new IOException("Invalid vision model directory");
            File file=new File(directory.getAbsoluteFile(),name);
            if (!file.equals(file.getCanonicalFile()) ||
                    !Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS) ||
                    file.length()!=bytes) throw new IOException("Missing or mismatched vision asset");
            final MessageDigest digest;
            try { digest=MessageDigest.getInstance("SHA-256"); }
            catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
            byte[] header={'G','G','U','F'}, buffer=new byte[65536];
            long count=0;
            try (InputStream in=Files.newInputStream(file.toPath(),StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
                for (;;) {
                    VisionImagePolicy.check(cancelled);
                    int n=in.read(buffer,0,(int)Math.min(buffer.length,bytes-count+1));
                    if (n<0) break;
                    if (n==0 || n>bytes-count) throw new IOException("Vision asset size changed");
                    for (int i=0;i<n && count+i<4;i++)
                        if (buffer[i]!=header[(int)(count+i)]) throw new IOException("Not a GGUF asset");
                    digest.update(buffer,0,n); count+=n;
                }
            }
            VisionImagePolicy.check(cancelled);
            StringBuilder hex=new StringBuilder(64);
            for(byte value:digest.digest()) hex.append(String.format(Locale.ROOT,"%02x",value&255));
            if (count!=bytes || !sha256.equals(hex.toString()) || file.length()!=bytes ||
                    !file.equals(file.getCanonicalFile()) ||
                    !Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Vision asset integrity mismatch");
            return file;
        }
    }

    static boolean matches(String revision, String weightsSha, long weightsBytes,
                           String projectorSha, long projectorBytes) {
        return REVISION.equals(revision) && WEIGHTS.sha256.equals(weightsSha) && WEIGHTS.bytes==weightsBytes &&
                PROJECTOR.sha256.equals(projectorSha) && PROJECTOR.bytes==projectorBytes;
    }
}
