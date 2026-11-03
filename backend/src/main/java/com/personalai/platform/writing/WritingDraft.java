package com.personalai.platform.writing;

import java.time.Instant;

/** Editable snapshot, independent from generated output and containing no authentication secrets. */
public record WritingDraft(String id, String title, WritingWorkflow workflow, String input, String content,
        String subject, WritingPreferences preferences, EmailComposition email, String modelId,
        Status status, Long revision, Instant updatedAt) {
    public enum Status { DRAFT }
    public WritingDraft {
        id = WritingValidation.text(id, 80, "id");
        title = WritingValidation.required(title, 200, "title");
        workflow = workflow == null ? WritingWorkflow.GENERAL : workflow;
        input = WritingValidation.text(input, 12000, "input");
        content = WritingValidation.text(content, 65000, "content");
        subject = WritingValidation.text(subject, 500, "subject");
        preferences = preferences == null ? WritingPreferences.defaults() : preferences;
        email = email == null ? EmailComposition.empty() : email;
        modelId = modelId == null || modelId.isBlank() ? "mock-balanced" : WritingValidation.required(modelId, 80, "modelId");
        status = Status.DRAFT;
    }
}
