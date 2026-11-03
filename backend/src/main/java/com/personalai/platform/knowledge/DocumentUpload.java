package com.personalai.platform.knowledge;

import com.personalai.platform.common.InvalidConfigurationException;

/** Future upload metadata, not proof of the actual bytes' type, safety, or integrity. */
public record DocumentUpload(String fileName, KnowledgeFormat format, String mediaType, long byteSize) {
    public DocumentUpload {
        if (format == null) {
            throw new InvalidConfigurationException("An upload requires a document format.");
        }
        format.validateFileName(fileName);
        if (!format.mediaType().equals(mediaType)) {
            throw new InvalidConfigurationException("Upload media type must match its declared format.");
        }
        if (byteSize < 0) {
            throw new InvalidConfigurationException("Upload size must not be negative.");
        }
    }
}
