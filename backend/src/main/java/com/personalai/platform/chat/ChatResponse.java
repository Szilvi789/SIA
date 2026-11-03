package com.personalai.platform.chat;

import java.time.Instant;

public record ChatResponse(String id, String agentId, String modelId, String content, Instant createdAt) { }
