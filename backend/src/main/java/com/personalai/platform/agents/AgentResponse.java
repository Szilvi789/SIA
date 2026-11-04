package com.personalai.platform.agents;

import com.personalai.platform.models.ModelSettings;
import java.util.List;

public record AgentResponse(
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
    public AgentResponse(String id, String name, String description, String systemPrompt, String providerId,
                         String modelId, double temperature, int maxOutputTokens, List<String> enabledTools,
                         List<String> ruleIds, List<String> knowledgeSourceIds) {
        this(id, name, description, systemPrompt, providerId, modelId, temperature, maxOutputTokens,
                enabledTools, ruleIds, knowledgeSourceIds, null);
    }
    public static AgentResponse from(Agent agent) {
        return new AgentResponse(agent.id(), agent.name(), agent.description(), agent.systemPrompt(),
                agent.providerId(), agent.modelId(), agent.temperature(), agent.maxOutputTokens(),
                agent.enabledTools(), agent.ruleIds(), agent.knowledgeSourceIds(), agent.modelSettings());
    }
}
