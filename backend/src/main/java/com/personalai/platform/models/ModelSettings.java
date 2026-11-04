package com.personalai.platform.models;

import java.math.BigDecimal;
import java.util.Map;

public record ModelSettings(String defaultTier, String fallbackModelId, Boolean allowFallback,
        Map<String, String> tierMappings, GenerationParameters parameters, BigDecimal maxEstimatedCost,
        String currency, Boolean allowUnknownCost) {
    public ModelSettings {
        tierMappings = normalizeMappings(tierMappings);
        defaultTier = defaultTier == null || defaultTier.isBlank() ? null : ModelCatalog.requireTier(defaultTier);
        parameters = parameters == null ? GenerationParameters.empty() : parameters;
        allowFallback = Boolean.TRUE.equals(allowFallback);
        allowUnknownCost = Boolean.TRUE.equals(allowUnknownCost);
    }
    public static ModelSettings defaults() { return new ModelSettings(null, null, false, Map.of(), null, null, null, true); }
    static Map<String, String> normalizeMappings(Map<String, String> mappings) {
        if (mappings == null) return Map.of();
        var normalized = new java.util.HashMap<String, String>();
        mappings.forEach((key, value) -> {
            if (normalized.putIfAbsent(ModelCatalog.requireTier(key), value) != null) {
                throw new com.personalai.platform.common.InvalidConfigurationException("tierMappings", "Do not define a tier more than once.");
            }
        });
        return Map.copyOf(normalized);
    }
}
