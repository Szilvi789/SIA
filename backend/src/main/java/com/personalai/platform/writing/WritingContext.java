package com.personalai.platform.writing;

/** Structured task data is untrusted content, separate from resolved system instructions. */
public record WritingContext(WritingWorkflow workflow, WritingPreferences preferences,
        EmailComposition email, DetectedLanguage detectedLanguage) { }
