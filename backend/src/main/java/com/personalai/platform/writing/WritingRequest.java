package com.personalai.platform.writing;

import com.personalai.platform.models.GenerationParameters;
import com.personalai.platform.models.ModelSelection;

public record WritingRequest(WritingWorkflow workflow, String input, String modelId,
        WritingPreferences preferences, EmailComposition email,
        String tier, String overrideModelId, String overrideTier, GenerationParameters parameters,
        Boolean allowFallback, String conversationId) {
    public WritingRequest {
        workflow = workflow == null ? WritingWorkflow.GENERAL : workflow;
        input = WritingValidation.text(input, 12000, "input");
        modelId = optional(modelId, 80, "modelId");
        tier = optional(tier, 20, "tier");
        overrideModelId = optional(overrideModelId, 80, "overrideModelId");
        overrideTier = optional(overrideTier, 20, "overrideTier");
        conversationId = optional(conversationId, 120, "conversationId");
        preferences = preferences == null ? WritingPreferences.defaults() : preferences;
        email = email == null ? EmailComposition.empty() : email;
    }

    public WritingRequest(WritingWorkflow workflow, String input, String modelId,
                          WritingPreferences preferences, EmailComposition email) {
        this(workflow, input, modelId, preferences, email, null, null, null, null, null, null);
    }

    public ModelSelection selection() {
        return new ModelSelection(modelId, tier, overrideModelId, overrideTier, parameters, allowFallback, conversationId);
    }

    private static String optional(String value, int limit, String field) {
        return value == null || value.isBlank() ? null : WritingValidation.required(value, limit, field);
    }
}
