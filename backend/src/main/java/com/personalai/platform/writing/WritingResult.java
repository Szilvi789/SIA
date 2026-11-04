package com.personalai.platform.writing;

import com.personalai.platform.providers.ExecutionMetadata;
import java.time.Instant;
import java.util.List;

public record WritingResult(String id, String title, String content, String subject, WritingWorkflow workflow,
        DetectedLanguage detectedLanguage, WritingPreferences.OutputLanguage outputLanguage,
        WritingPreferences.TranslationMode translationMode, boolean mock, List<String> appliedRuleIds, Instant createdAt,
        String providerId, String modelId, String source, ExecutionMetadata execution) {
    public WritingResult { appliedRuleIds = List.copyOf(appliedRuleIds); }

    public WritingResult(String id, String title, String content, String subject, WritingWorkflow workflow,
            DetectedLanguage detectedLanguage, WritingPreferences.OutputLanguage outputLanguage,
            WritingPreferences.TranslationMode translationMode, boolean mock, List<String> appliedRuleIds, Instant createdAt) {
        this(id, title, content, subject, workflow, detectedLanguage, outputLanguage, translationMode,
                mock, appliedRuleIds, createdAt, null, null, null, null);
    }
}
