package com.personalai.platform.providers;

/** Token counts supplied by the provider. Null means unreported. */
public record ProviderUsage(Long inputTokens, Long outputTokens, Long cachedTokens) {
    public ProviderUsage {
        if ((inputTokens != null && inputTokens < 0) || (outputTokens != null && outputTokens < 0)
                || (cachedTokens != null && cachedTokens < 0)
                || (cachedTokens != null && inputTokens != null && cachedTokens > inputTokens)) {
            throw new IllegalArgumentException("Provider usage counts must be nonnegative and consistent.");
        }
    }
}
