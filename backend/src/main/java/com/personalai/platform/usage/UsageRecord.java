package com.personalai.platform.usage;

import java.math.BigDecimal;
import java.time.Instant;

/** Usage stores no prompts or outputs. Prices are snapshots, not references to mutable model pricing. */
public record UsageRecord(String id, String providerId, String modelId, String agentId, String workflow,
        String conversationId, Instant timestamp, Long inputTokens, Long outputTokens, Long cachedTokens,
        BigDecimal estimatedCost, String currency, Instant pricingUpdatedAt, String status, boolean mock,
        String error, BigDecimal inputTokenPrice, BigDecimal outputTokenPrice, Long pricingUnit) { }
