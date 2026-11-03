package com.personalai.platform.writing;

public record WritingRequest(WritingWorkflow workflow, String input, String modelId,
        WritingPreferences preferences, EmailComposition email) {
    public WritingRequest {
        workflow = workflow == null ? WritingWorkflow.GENERAL : workflow;
        input = WritingValidation.text(input, 12000, "input");
        modelId = modelId == null || modelId.isBlank() ? "mock-balanced" : WritingValidation.required(modelId, 80, "modelId");
        preferences = preferences == null ? WritingPreferences.defaults() : preferences;
        email = email == null ? EmailComposition.empty() : email;
    }
}
