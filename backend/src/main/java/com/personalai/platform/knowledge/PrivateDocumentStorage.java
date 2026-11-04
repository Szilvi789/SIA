package com.personalai.platform.knowledge;

import com.personalai.platform.configuration.RuntimeDataSettings;
import com.personalai.platform.configuration.RuntimeDataSettings.Category;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Opaque document bytes outside the repository. No upload HTTP endpoint is exposed. */
@Component
public class PrivateDocumentStorage implements DocumentStorage {
    private final RuntimeDataSettings settings;

    public PrivateDocumentStorage(RuntimeDataSettings settings) { this.settings = settings; }

    @Override
    public String store(DocumentUpload metadata, InputStream content) throws IOException {
        if (metadata == null || content == null) { throw new IOException("Upload metadata and content are required."); }
        if (metadata.byteSize() > settings.maximumUploadBytes()) { throw new IOException("The upload exceeds its size limit."); }
        var bytes = content.readNBytes(Math.toIntExact(settings.maximumUploadBytes() + 1));
        if (bytes.length > settings.maximumUploadBytes() || bytes.length != metadata.byteSize()) {
            throw new IOException("Upload content does not match its permitted size.");
        }
        inspectBytes(metadata.format(), bytes);
        var key = UUID.randomUUID().toString();
        var destination = settings.directory(Category.UPLOADS).resolve(key);
        settings.requireContained(destination);
        var created = false;
        try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            created = true;
            settings.restrictToOwner(destination, false);
            output.write(bytes);
        } catch (IOException | RuntimeException exception) {
            // Only this newly created incomplete upload is removed; existing user files are never selected.
            if (created) { Files.deleteIfExists(destination); }
            throw exception;
        }
        return key;
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        var key = requireKey(storageKey);
        var path = settings.directory(Category.UPLOADS).resolve(key);
        settings.requireContained(path);
        return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        var key = requireKey(storageKey);
        var path = settings.directory(Category.UPLOADS).resolve(key);
        settings.requireContained(path);
        Files.deleteIfExists(path);
    }

    private static String requireKey(String key) throws IOException {
        if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IOException("A storage key must be an opaque document identifier.");
        }
        return key;
    }

    private static void inspectBytes(KnowledgeFormat format, byte[] bytes) throws IOException {
        if (format == KnowledgeFormat.PDF) {
            if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new IOException("PDF content must have a PDF signature.");
            }
            return;
        }
        try {
            var text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.chars().anyMatch(character -> Character.isISOControl(character)
                    && character != '\n' && character != '\r' && character != '\t')) {
                throw new IOException("Text uploads must contain valid UTF-8 text.");
            }
            if (format == KnowledgeFormat.JSON) {
                var tree = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(text);
                if (tree == null || tree.isMissingNode()) { throw new IOException("A JSON upload requires a JSON value."); }
            }
        } catch (CharacterCodingException | tools.jackson.core.JacksonException exception) {
            throw new IOException("Document bytes do not match the declared text format.");
        }
    }
}
