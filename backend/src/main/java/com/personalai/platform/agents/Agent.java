package com.personalai.platform.agents;

import com.personalai.platform.models.ModelSettings;
import java.util.List;

public record Agent(
        String id,
        String name,
        String description,
        String systemPrompt,
        String providerId,
        String modelId,
        double temperature,
        int maxOutputTokens,
        List<String> enabledTools,
        List<String> ruleIds,
        List<String> knowledgeSourceIds,
        ModelSettings modelSettings) {
    public Agent {
        enabledTools = List.copyOf(enabledTools);
        ruleIds = ruleIds == null ? List.of() : List.copyOf(ruleIds);
        knowledgeSourceIds = knowledgeSourceIds == null ? List.of() : List.copyOf(knowledgeSourceIds);
    }

    public Agent(String id, String name, String description, String systemPrompt, String providerId,
                 String modelId, double temperature, int maxOutputTokens, List<String> enabledTools,
                 List<String> ruleIds, List<String> knowledgeSourceIds) {
        this(id, name, description, systemPrompt, providerId, modelId, temperature, maxOutputTokens,
                enabledTools, ruleIds, knowledgeSourceIds, null);
    }

    /** Existing agents start without optional rule or knowledge assignments. */
    public Agent(String id, String name, String description, String systemPrompt, String providerId,
                 String modelId, double temperature, int maxOutputTokens, List<String> enabledTools) {
        this(id, name, description, systemPrompt, providerId, modelId, temperature, maxOutputTokens,
                enabledTools, List.of(), List.of());
    }

    public Agent withId(String requestedId) {
        return new Agent(requestedId, name, description, systemPrompt, providerId, modelId, temperature,
                maxOutputTokens, enabledTools, ruleIds, knowledgeSourceIds, modelSettings);
    }
}
