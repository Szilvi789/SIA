package com.personalai.platform.knowledge;

import static org.junit.jupiter.api.Assertions.*;

import com.personalai.platform.configuration.RuntimeDataSettings;
import com.personalai.platform.configuration.RuntimeDataSettings.Category;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrivateDocumentStorageTest {
    @TempDir Path temporary;

    @Test
    void storesOpaqueFilesOutsideRepositoryAndReopensAfterRestart() throws IOException {
        var settings = settings("personal");
        var first = new PrivateDocumentStorage(settings);
        var bytes = "Synthetic upload fixture".getBytes(StandardCharsets.UTF_8);
        var key = first.store(metadata("private-name.txt", bytes.length), new ByteArrayInputStream(bytes));
        assertTrue(key.matches("[0-9a-f-]{36}"));
        assertFalse(key.contains("private-name"));
        assertTrue(Files.isRegularFile(settings.directory(Category.UPLOADS).resolve(key)));
        var restarted = new PrivateDocumentStorage(settings("personal"));
        try (var input = restarted.open(key)) { assertArrayEquals(bytes, input.readAllBytes()); }
        assertThrows(IOException.class, () -> new PrivateDocumentStorage(settings("demo")).open(key));
        var file = settings.directory(Category.UPLOADS).resolve(key);
        if (Files.getFileAttributeView(file, PosixFileAttributeView.class) != null) {
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
        }
    }

    @Test
    void enforcesDeclaredSizeAndActualByteLimitsWithoutLeavingFiles() throws IOException {
        var settings = settings("personal");
        var storage = new PrivateDocumentStorage(settings);
        assertThrows(IOException.class, () -> storage.store(metadata("upload.txt", 2),
                new ByteArrayInputStream(new byte[3])));
        assertThrows(IOException.class, () -> storage.store(metadata("upload.txt", 4097),
                new ByteArrayInputStream(new byte[4097])));
        assertThrows(IOException.class, () -> storage.store(metadata("upload.txt", 1),
                new ByteArrayInputStream(new byte[4097])));
        assertFalse(Files.exists(settings.dataDirectory()));
    }

    @Test
    void neverAcceptsAClientFilenameOrTraversalAsAStorageKey() throws IOException {
        var storage = new PrivateDocumentStorage(settings("personal"));
        for (String key : new String[]{"../secret.txt", "C:\\secret.txt", "/tmp/secret", "secret.txt", "uploads/a"}) {
            assertThrows(IOException.class, () -> storage.open(key));
            assertThrows(IOException.class, () -> storage.delete(key));
        }
    }

    @Test
    void inspectsBytesInsteadOfTrustingTheExtensionAndMediaType() throws IOException {
        var storage = new PrivateDocumentStorage(settings("personal"));
        assertThrows(IOException.class, () -> storage.store(metadata("upload.txt", 1),
                new ByteArrayInputStream(new byte[]{0})));
        assertThrows(IOException.class, () -> storage.store(metadata("upload.txt", 1),
                new ByteArrayInputStream(new byte[]{(byte) 0xff})));
        var invalidPdf = "not a PDF".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> storage.store(new DocumentUpload("upload.pdf", KnowledgeFormat.PDF,
                KnowledgeFormat.PDF.mediaType(), invalidPdf.length), new ByteArrayInputStream(invalidPdf)));
        var invalidJson = "not JSON".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> storage.store(new DocumentUpload("upload.json", KnowledgeFormat.JSON,
                KnowledgeFormat.JSON.mediaType(), invalidJson.length), new ByteArrayInputStream(invalidJson)));
    }

    private RuntimeDataSettings settings(String mode) {
        return new RuntimeDataSettings(mode, temporary.resolve("private").toString(), "", 4096);
    }

    private static DocumentUpload metadata(String name, long size) {
        return new DocumentUpload(name, KnowledgeFormat.TXT, KnowledgeFormat.TXT.mediaType(), size);
    }
}
