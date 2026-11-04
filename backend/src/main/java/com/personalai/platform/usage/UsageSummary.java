package com.personalai.platform.usage;
import java.math.BigDecimal;
public record UsageSummary(String agentId, String modelId, String providerId, long requests,
        long successfulRequests, long failedRequests, long mockRequests, Long inputTokens,
        Long outputTokens, BigDecimal estimatedCost, String currency) { }
