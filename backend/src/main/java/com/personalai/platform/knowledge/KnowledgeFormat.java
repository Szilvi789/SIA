package com.personalai.platform.knowledge;

import com.personalai.platform.common.InvalidConfigurationException;
import java.util.Arrays;
import java.util.Locale;

/** Supported document formats; declaring a format does not parse or trust its contents. */
public enum KnowledgeFormat {
    MARKDOWN(".md", "text/markdown"),
    PDF(".pdf", "application/pdf"),
    TXT(".txt", "text/plain"),
    JSON(".json", "application/json"),
    CSV(".csv", "text/csv");

    private final String extension;
    private final String mediaType;

    KnowledgeFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public static KnowledgeFormat fromFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.length() > 255
                || fileName.contains("/") || fileName.contains("\\") || fileName.contains(":")
                || fileName.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidConfigurationException("A document requires a plain file name.");
        }
        var normalized = fileName.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(format -> normalized.endsWith(format.extension)
                        && fileName.length() > format.extension.length())
                .findFirst()
                .orElseThrow(() -> new InvalidConfigurationException("Unsupported document format."));
    }

    public void validateFileName(String fileName) {
        if (fromFileName(fileName) != this) {
            throw new InvalidConfigurationException("Document format must match its file extension.");
        }
    }
}
