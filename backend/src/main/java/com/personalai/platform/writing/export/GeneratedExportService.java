package com.personalai.platform.writing.export;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.configuration.RuntimeDataSettings;
import com.personalai.platform.configuration.RuntimeDataSettings.Category;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;

@Service
public class GeneratedExportService {
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final long MAX_UNCOMPRESSED_DOCX_BYTES = 64L * 1024 * 1024;
    private final RuntimeDataSettings settings;

    public GeneratedExportService(RuntimeDataSettings settings) { this.settings = settings; }

    public GeneratedExport save(ExportFormat format, long declaredSize, InputStream content) throws IOException {
        requirePersonal();
        if (format == null || content == null) { throw invalid("An export requires a format and file content."); }
        if (declaredSize < 0 || declaredSize > settings.maximumUploadBytes()) {
            throw invalid("The generated export exceeds its permitted size.");
        }
        var bytes = content.readNBytes(Math.toIntExact(settings.maximumUploadBytes() + 1));
        if (bytes.length > settings.maximumUploadBytes() || bytes.length != declaredSize) {
            throw invalid("The generated export does not match its permitted size.");
        }
        validateBytes(format, bytes);
        var reference = new GeneratedExport(UUID.randomUUID().toString(), format);
        var destination = path(reference.id(), format);
        boolean created = false;
        try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            created = true;
            settings.restrictToOwner(destination, false);
            output.write(bytes);
        } catch (IOException | RuntimeException exception) {
            if (created) { Files.deleteIfExists(destination); }
            throw exception;
        }
        return reference;
    }

    /** Used by private server-side workflows; no HTTP download or listing endpoint exposes these files. */
    public InputStream open(String id, ExportFormat format) throws IOException {
        requirePersonal();
        return Files.newInputStream(path(id, format), LinkOption.NOFOLLOW_LINKS);
    }

    private java.nio.file.Path path(String id, ExportFormat format) throws IOException {
        if (format == null || id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw invalid("An export reference must be an opaque document identifier and supported format.");
        }
        var path = settings.directory(Category.GENERATED).resolve(id + "." + format.extension());
        settings.requireContained(path);
        return path;
    }

    private void requirePersonal() {
        if (!settings.isPersonal()) { throw invalid("Private generated export storage requires personal mode."); }
    }

    private static void validateBytes(ExportFormat format, byte[] bytes) {
        try {
            switch (format) {
                case TXT -> {
                    var text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
                    if (text.chars().anyMatch(character -> Character.isISOControl(character)
                            && character != '\n' && character != '\r' && character != '\t')) {
                        throw invalid("Text exports must contain valid UTF-8 text.");
                    }
                }
                case PDF -> {
                    if (!startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                        throw invalid("The export content does not have a PDF signature.");
                    }
                }
                case PNG -> {
                    if (!startsWith(bytes, PNG_SIGNATURE)) { throw invalid("The export content does not have a PNG signature."); }
                }
                case DOCX -> validateDocx(bytes);
            }
        } catch (IOException exception) {
            throw invalid("The generated export content does not match its declared format.");
        }
    }

    private static void validateDocx(byte[] bytes) throws IOException {
        if (!startsWith(bytes, new byte[]{'P', 'K', 3, 4})) { throw invalid("DOCX exports must be valid ZIP document packages."); }
        boolean contentTypes = false;
        boolean document = false;
        int entries = 0;
        long uncompressed = 0;
        var buffer = new byte[8192];
        try (var archive = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (++entries > 256 || entry.getName().startsWith("/") || entry.getName().contains("..")
                        || entry.getName().contains("\\") || entry.getName().contains(":")) {
                    throw invalid("The DOCX export contains invalid package entries.");
                }
                contentTypes |= "[Content_Types].xml".equals(entry.getName()) && !entry.isDirectory();
                document |= "word/document.xml".equals(entry.getName()) && !entry.isDirectory();
                int count;
                while ((count = archive.read(buffer)) != -1) {
                    uncompressed += count;
                    if (uncompressed > MAX_UNCOMPRESSED_DOCX_BYTES) {
                        throw invalid("The DOCX export exceeds its uncompressed size limit.");
                    }
                }
            }
        }
        if (!contentTypes || !document) { throw invalid("The DOCX export requires its document package entries."); }
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) { return false; }
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) { return false; }
        }
        return true;
    }

    private static InvalidConfigurationException invalid(String message) {
        return new InvalidConfigurationException("file", message);
    }
}
