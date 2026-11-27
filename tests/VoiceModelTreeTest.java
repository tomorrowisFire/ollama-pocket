package com.ollamapocket.app;

import java.nio.file.*;
import java.util.Comparator;
import java.util.Set;

/** Real filesystem fixtures for exact membership, independent of Android and archive libraries. */
public final class VoiceModelTreeTest {
    private static int checks;
    private static void ok(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        ++checks;
    }
    private static boolean matches(Path root) throws Exception {
        return VoiceModelTree.matches(root, Set.of("model.bin", "dict/sub/words.txt"),
                Set.of("dict", "dict/sub"), ".installed");
    }
    public static void main(String[] args) throws Exception {
        Path sandbox = Files.createTempDirectory("voice-tree-test-");
        try {
            Path root = sandbox.resolve("model");
            ok(!matches(root), "Missing root accepted");
            Files.createDirectories(root.resolve("dict/sub"));
            Path model = root.resolve("model.bin"), words = root.resolve("dict/sub/words.txt");
            Path marker = root.resolve(".installed"), extra = root.resolve("dict/unknown.txt");
            Files.writeString(model, "model");
            Files.writeString(words, "words");
            ok(!matches(root), "Missing marker accepted");
            Files.writeString(marker, "marker");
            ok(matches(root), "Exact nested tree rejected");
            Files.writeString(extra, "extra");
            ok(!matches(root), "Extra dictionary file accepted");
            Files.delete(extra);
            Files.createDirectory(extra);
            ok(!matches(root), "Extra dictionary directory accepted");
            Files.delete(extra);
            Files.createSymbolicLink(extra, words);
            ok(!matches(root), "Extra link accepted");
            Files.delete(extra);
            Files.delete(words);
            ok(!matches(root), "Missing declared file accepted");
            Files.createDirectory(words);
            ok(!matches(root), "Declared file replaced by directory accepted");
            Files.delete(words);
            Files.createSymbolicLink(words, model);
            ok(!matches(root), "Declared file replaced by link accepted");
            Files.delete(words);
            Files.writeString(words, "words");
            ok(matches(root), "Recovery rejected");
            Path linked = sandbox.resolve("link");
            Files.createSymbolicLink(linked, root);
            ok(!matches(linked), "Linked root accepted");
            Path flat = Files.createDirectory(sandbox.resolve("flat"));
            Files.writeString(flat.resolve("model.bin"), "model");
            Files.writeString(flat.resolve(".installed"), "marker");
            ok(VoiceModelTree.matches(flat, Set.of("model.bin"), Set.of(), ".installed"), "Flat tree rejected");
            Files.writeString(flat.resolve("unexpected"), "extra");
            ok(!VoiceModelTree.matches(flat, Set.of("model.bin"), Set.of(), ".installed"), "Flat extra accepted");
        } finally {
            try (var paths = Files.walk(sandbox)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) Files.delete(path);
            }
        }
        System.out.println("PASS: " + checks + " exact installed voice model tree checks");
    }
}
