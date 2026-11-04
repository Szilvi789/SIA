package com.personalai.platform.chat;

import com.personalai.platform.providers.ExecutionMetadata;
import java.time.Instant;

public record ChatResponse(String id, String agentId, String modelId, String content, Instant createdAt,
        String providerId, String source, ExecutionMetadata execution, String conversationId) {
    public ChatResponse(String id, String agentId, String modelId, String content, Instant createdAt,
            String providerId, String source, ExecutionMetadata execution) {
        this(id, agentId, modelId, content, createdAt, providerId, source, execution, null);
    }
    public ChatResponse(String id, String agentId, String modelId, String content, Instant createdAt) {
        this(id, agentId, modelId, content, createdAt, null, null, null);
    }
}
