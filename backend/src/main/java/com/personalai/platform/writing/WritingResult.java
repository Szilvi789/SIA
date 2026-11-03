package com.personalai.platform.writing;

import java.time.Instant;
import java.util.List;

public record WritingResult(String id, String title, String content, String subject, WritingWorkflow workflow,
        DetectedLanguage detectedLanguage, WritingPreferences.OutputLanguage outputLanguage,
        WritingPreferences.TranslationMode translationMode, boolean mock, List<String> appliedRuleIds, Instant createdAt) {
    public WritingResult { appliedRuleIds = List.copyOf(appliedRuleIds); }
}
