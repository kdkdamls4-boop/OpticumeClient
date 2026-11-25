package dev.opticumeclient.client.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Atomic UTF-8 file persistence helper writing via a sibling temporary file before replacing
 * the destination so failed or interrupted saves never truncate existing data.
 */
public final class AtomicFileWriter {

    @FunctionalInterface
    public interface WriterCallback {
        void write(BufferedWriter writer) throws Exception;
    }

    private AtomicFileWriter() {
    }

    public static void writeUtf8(File targetFile, WriterCallback callback) throws Exception {
        if (targetFile == null) {
            throw new IOException("Target file is null");
        }
        Path targetPath = targetFile.toPath();
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = Files.createTempFile(parent != null ? parent : Path.of("."), targetPath.getFileName().toString(), ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                callback.write(writer);
                writer.flush();
            }
            Files.move(tmp, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
