package com.personalai.platform.agents;

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
        List<String> knowledgeSourceIds) {
    public Agent {
        enabledTools = List.copyOf(enabledTools);
        ruleIds = ruleIds == null ? List.of() : List.copyOf(ruleIds);
        knowledgeSourceIds = knowledgeSourceIds == null ? List.of() : List.copyOf(knowledgeSourceIds);
    }

    /** Existing agents start without optional rule or knowledge assignments. */
    public Agent(String id, String name, String description, String systemPrompt, String providerId,
                 String modelId, double temperature, int maxOutputTokens, List<String> enabledTools) {
        this(id, name, description, systemPrompt, providerId, modelId, temperature, maxOutputTokens,
                enabledTools, List.of(), List.of());
    }
}
