package com.personalai.platform.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/** Null metadata is explicitly unknown, never inferred from the display name. */
public record ModelDescriptor(String id, String name, String providerId, String description,
        String apiModelId, boolean enabled, Set<String> capabilities, Long contextWindow,
        Integer maxOutputTokens, BigDecimal inputTokenPrice, BigDecimal outputTokenPrice,
        String currency, Long pricingUnit, Instant pricingUpdatedAt, Set<String> supportedParameters,
        Set<String> reasoningEfforts, Boolean toolCalling, Boolean structuredOutput, Boolean vision,
        Boolean embedding) {
    public ModelDescriptor {
        capabilities = capabilities == null ? null : Set.copyOf(capabilities);
        supportedParameters = supportedParameters == null ? null : Set.copyOf(supportedParameters);
        reasoningEfforts = reasoningEfforts == null ? null : Set.copyOf(reasoningEfforts);
    }
    public ModelDescriptor(String id, String name, String providerId, String description) {
        this(id, name, providerId, description, id, true,
                "mock".equals(providerId) ? Set.of("TEXT", "STRUCTURED_OUTPUT") : null, null,
                null, null, null, null, null, null,
                "mock".equals(providerId) ? Set.of("maxOutputTokens", "temperature", "timeoutMs", "tools") : null,
                "mock".equals(providerId) ? Set.of() : null,
                "mock".equals(providerId) ? false : null, "mock".equals(providerId) ? true : null,
                "mock".equals(providerId) ? false : null, "mock".equals(providerId) ? false : null);
    }
    public Set<String> declaredCapabilities() { return capabilities == null ? Set.of() : capabilities; }
    public Set<String> declaredParameters() { return supportedParameters == null ? Set.of() : supportedParameters; }
    public Set<String> declaredReasoningEfforts() { return reasoningEfforts == null ? Set.of() : reasoningEfforts; }
}
