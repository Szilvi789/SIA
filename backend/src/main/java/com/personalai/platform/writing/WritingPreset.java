package com.personalai.platform.writing;

import com.personalai.platform.common.InvalidConfigurationException;
import java.util.List;

public record WritingPreset(String id, String name, String description, WritingWorkflow workflow,
        boolean enabled, List<String> ruleIds, String markdown, WritingPreferences preferences) {
    public WritingPreset {
        id = WritingValidation.required(id, 60, "id");
        if (!id.matches("[a-z0-9][a-z0-9-]{0,59}")) throw new InvalidConfigurationException("id", "Use a simple lowercase preset ID.");
        name = WritingValidation.required(name, 100, "name");
        description = WritingValidation.text(description, 500, "description");
        workflow = workflow == null ? WritingWorkflow.GENERAL : workflow;
        ruleIds = WritingValidation.ids(ruleIds);
        markdown = WritingValidation.required(markdown, 16000, "markdown");
        preferences = preferences == null ? WritingPreferences.defaults() : preferences;
    }
}
