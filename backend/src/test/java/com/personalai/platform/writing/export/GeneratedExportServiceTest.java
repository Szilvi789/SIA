package com.personalai.platform.writing.export;

import static org.junit.jupiter.api.Assertions.*;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.configuration.RuntimeDataSettings;
import com.personalai.platform.configuration.RuntimeDataSettings.Category;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratedExportServiceTest {
    @TempDir Path temporary;

    @Test
    void everySupportedExportPersistsUnderAnOpaqueNameAndSurvivesRestart() throws IOException {
        var settings = settings("personal");
        var exports = new GeneratedExportService(settings);
        for (var format : ExportFormat.values()) {
            var bytes = validBytes(format);
            var reference = exports.save(format, bytes.length, new ByteArrayInputStream(bytes));
            assertTrue(reference.id().matches("[0-9a-f-]{36}"));
            assertEquals(format, reference.format());
            var file = settings.directory(Category.GENERATED).resolve(reference.id() + "." + format.extension());
            assertTrue(Files.isRegularFile(file));
            var restarted = new GeneratedExportService(settings("personal"));
            try (var input = restarted.open(reference.id(), reference.format())) {
                assertArrayEquals(bytes, input.readAllBytes());
            }
        }
    }

    @Test
    void demoModeCannotPersistGeneratedExportsOrReadPrivateReferences() {
        var settings = settings("demo");
        var exports = new GeneratedExportService(settings);
        assertThrows(InvalidConfigurationException.class,
                () -> exports.save(ExportFormat.TXT, 0, new ByteArrayInputStream(new byte[0])));
        assertThrows(InvalidConfigurationException.class,
                () -> exports.open("00000000-0000-0000-0000-000000000000", ExportFormat.TXT));
        assertFalse(Files.exists(settings.dataDirectory()));
    }

    @Test
    void enforcesDeclaredAndActualByteLimitsBeforeCreatingAFile() {
        var settings = settings("personal");
        var exports = new GeneratedExportService(settings);
        assertThrows(InvalidConfigurationException.class,
                () -> exports.save(ExportFormat.TXT, 4097, new ByteArrayInputStream(new byte[4097])));
        assertThrows(InvalidConfigurationException.class,
                () -> exports.save(ExportFormat.TXT, 1, new ByteArrayInputStream(new byte[4097])));
        assertThrows(InvalidConfigurationException.class,
                () -> exports.save(ExportFormat.TXT, 2, new ByteArrayInputStream(new byte[1])));
        assertThrows(InvalidConfigurationException.class,
                () -> exports.save(null, 0, new ByteArrayInputStream(new byte[0])));
        assertFalse(Files.exists(settings.dataDirectory()));
    }

    @Test
    void rejectsDisguisedPdfPngDocxAndInvalidUtf8Text() {
        var settings = settings("personal");
        var exports = new GeneratedExportService(settings);
        for (var format : new ExportFormat[]{ExportFormat.PDF, ExportFormat.PNG, ExportFormat.DOCX}) {
            var bytes = "Synthetic plain text".getBytes(StandardCharsets.UTF_8);
            assertThrows(InvalidConfigurationException.class,
                    () -> exports.save(format, bytes.length, new ByteArrayInputStream(bytes)));
        }
        for (var bytes : new byte[][]{{0}, {(byte) 0xff}}) {
            assertThrows(InvalidConfigurationException.class,
                    () -> exports.save(ExportFormat.TXT, bytes.length, new ByteArrayInputStream(bytes)));
        }
        assertFalse(Files.exists(settings.dataDirectory()));
    }

    @Test
    void genericZipArchivesAreNotAcceptedAsDocxAndPackageTraversalIsRejected() throws IOException {
        var exports = new GeneratedExportService(settings("personal"));
        for (var bytes : new byte[][]{zip("ordinary.txt"), zip("../word/document.xml", "[Content_Types].xml")}) {
            assertThrows(InvalidConfigurationException.class,
                    () -> exports.save(ExportFormat.DOCX, bytes.length, new ByteArrayInputStream(bytes)));
        }
    }

    @Test
    void exportReferencesNeverAcceptAnAbsolutePathOrTraversal() {
        var exports = new GeneratedExportService(settings("personal"));
        for (var id : new String[]{"../private.txt", "C:\\private.txt", "/tmp/private.txt", "00000000-0000-0000-0000-000000000000/.."}) {
            assertThrows(InvalidConfigurationException.class, () -> exports.open(id, ExportFormat.TXT));
        }
    }

    @Test
    void refusesGeneratedDirectoriesRedirectedByASymlink() throws IOException {
        var settings = settings("personal");
        Files.createDirectories(settings.dataDirectory());
        var outside = Files.createDirectories(temporary.resolve("outside"));
        symlinkOrSkip(settings.dataDirectory().resolve("generated"), outside);
        var exports = new GeneratedExportService(settings);
        var bytes = validBytes(ExportFormat.TXT);
        assertThrows(IOException.class, () -> exports.save(ExportFormat.TXT, bytes.length, new ByteArrayInputStream(bytes)));
        try (var files = Files.list(outside)) { assertEquals(0, files.count()); }
    }

    @Test
    void existingExportFilesCannotBeSymlinkedToOutsideContent() throws IOException {
        var settings = settings("personal");
        var generated = settings.directory(Category.GENERATED);
        var outside = Files.writeString(temporary.resolve("outside.txt"), "Synthetic outside content");
        var id = "00000000-0000-0000-0000-000000000000";
        symlinkOrSkip(generated.resolve(id + ".txt"), outside);
        assertThrows(IOException.class, () -> new GeneratedExportService(settings).open(id, ExportFormat.TXT));
    }

    private RuntimeDataSettings settings(String mode) {
        return new RuntimeDataSettings(mode, temporary.resolve("private").toString(), "", 4096);
    }

    static byte[] validBytes(ExportFormat format) throws IOException {
        return switch (format) {
            case TXT -> "Synthetic draft text\n".getBytes(StandardCharsets.UTF_8);
            case PDF -> "%PDF-1.7\nSynthetic fixture\n%%EOF".getBytes(StandardCharsets.US_ASCII);
            case PNG -> new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10};
            case DOCX -> zip("[Content_Types].xml", "word/document.xml");
        };
    }

    private static byte[] zip(String... entries) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var archive = new ZipOutputStream(bytes)) {
            for (var entry : entries) {
                archive.putNextEntry(new ZipEntry(entry));
                archive.write("<synthetic/>".getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void symlinkOrSkip(Path link, Path target) throws IOException {
        try { Files.createSymbolicLink(link, target); }
        catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable for the current OS/account.");
        }
    }
}
