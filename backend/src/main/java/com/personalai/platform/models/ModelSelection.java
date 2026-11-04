package com.personalai.platform.models;

/** Request choice first, then the explicitly supplied task/conversation override. */
public record ModelSelection(String modelId, String tier, String overrideModelId, String overrideTier,
        GenerationParameters parameters, Boolean allowFallback, String conversationId,
        com.personalai.platform.privacy.DataSharingConsent dataSharingConsent) {
    public ModelSelection(String modelId, String tier, String overrideModelId, String overrideTier,
            GenerationParameters parameters, Boolean allowFallback, String conversationId) {
        this(modelId, tier, overrideModelId, overrideTier, parameters, allowFallback, conversationId, null);
    }
    public static ModelSelection defaults() { return new ModelSelection(null, null, null, null, null, null, null); }
}
