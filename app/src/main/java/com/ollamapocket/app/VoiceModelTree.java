package com.ollamapocket.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** Exact, bounded installed-model membership check; never follows links or reads model bytes. */
final class VoiceModelTree {
    private VoiceModelTree() { }

    static boolean matches(Path root, Set<String> files, Set<String> directories, String marker)
            throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return false;
        Set<String> remainingFiles = new HashSet<>(files);
        remainingFiles.add(marker);
        Set<String> remainingDirectories = new HashSet<>(directories);
        boolean[] valid = {true};
        // Catalog paths have at most three components. Depth four also catches a directory
        // presented at the maximum supported depth, without descending into it.
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 4,
                new SimpleFileVisitor<Path>() {
            private String relative(Path path) {
                return root.relativize(path).toString().replace(File.separatorChar, '/');
            }
            private FileVisitResult reject() {
                valid[0] = false;
                return FileVisitResult.TERMINATE;
            }
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
                if (path.equals(root)) return FileVisitResult.CONTINUE;
                return remainingDirectories.remove(relative(path)) ? FileVisitResult.CONTINUE : reject();
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                return attrs.isRegularFile() && remainingFiles.remove(relative(path))
                        ? FileVisitResult.CONTINUE : reject();
            }
        });
        return valid[0] && remainingFiles.isEmpty() && remainingDirectories.isEmpty();
    }
}
