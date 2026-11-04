package com.personalai.platform.chat;

import com.personalai.platform.models.GenerationParameters;
import com.personalai.platform.models.ModelSelection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank @Size(max = 80) String agentId,
        @Size(max = 80) String modelId,
        @NotBlank @Size(max = 12_000) String message,
        @Size(max = 20) String tier,
        @Size(max = 80) String overrideModelId,
        @Size(max = 20) String overrideTier,
        @Valid GenerationParameters parameters,
        Boolean allowFallback,
        @Size(max = 120) String conversationId,
        @Valid com.personalai.platform.privacy.DataSharingConsent dataSharingConsent) {
    public ChatRequest(String agentId, String modelId, String message, String tier, String overrideModelId,
            String overrideTier, GenerationParameters parameters, Boolean allowFallback, String conversationId) {
        this(agentId, modelId, message, tier, overrideModelId, overrideTier, parameters, allowFallback, conversationId, null);
    }
    public ChatRequest(String agentId, String modelId, String message) {
        this(agentId, modelId, message, null, null, null, null, null, null);
    }

    public ModelSelection selection() {
        return new ModelSelection(modelId, tier, overrideModelId, overrideTier, parameters, allowFallback, conversationId, dataSharingConsent);
    }
}
