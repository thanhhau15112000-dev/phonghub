package com.phonghub.adapter.out.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalMediaStorageAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void createsUploadDirectoryOnlyWhenAFileIsStored() throws IOException {
        Path uploadDir = tempDir.resolve("uploads").resolve("chat-media");
        LocalMediaStorageAdapter adapter = new LocalMediaStorageAdapter(uploadDir);

        assertFalse(Files.exists(uploadDir));

        adapter.store("photo.png", "image/png", new byte[] {1, 2, 3});

        assertTrue(Files.isDirectory(uploadDir));
        try (Stream<Path> files = Files.list(uploadDir)) {
            assertEquals(1, files.count());
        }
    }
}
