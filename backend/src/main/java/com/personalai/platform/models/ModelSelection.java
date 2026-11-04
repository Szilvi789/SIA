package com.personalai.platform.models;

/** Request choice first, then the explicitly supplied task/conversation override. */
public record ModelSelection(String modelId, String tier, String overrideModelId, String overrideTier,
        GenerationParameters parameters, Boolean allowFallback, String conversationId) {
    public static ModelSelection defaults() { return new ModelSelection(null, null, null, null, null, null, null); }
}
