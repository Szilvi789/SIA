package com.personalai.platform.providers;
public record ExecutionMetadata(String providerId, String modelId, String apiModelId, String source,
        boolean fallbackUsed, boolean mock, String usageId) { }
