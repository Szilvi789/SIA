package com.personalai.platform.agents;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AgentUpdateRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 500) String description,
        @NotBlank @Size(max = 8_000) String systemPrompt,
        @NotBlank @Size(max = 80) String providerId,
        @NotBlank @Size(max = 80) String modelId,
        @NotNull @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        @NotNull @Min(64) @Max(8_192) Integer maxOutputTokens,
        @NotNull @Size(max = 50) List<@NotBlank @Size(max = 80) String> enabledTools,
        @Size(max = 50) List<@NotBlank @Size(max = 80) String> ruleIds,
        @Size(max = 50) List<@NotBlank @Size(max = 80) String> knowledgeSourceIds) {

    public AgentUpdateRequest {
        enabledTools = enabledTools == null ? null : List.copyOf(enabledTools);
        ruleIds = ruleIds == null ? null : List.copyOf(ruleIds);
        knowledgeSourceIds = knowledgeSourceIds == null ? null : List.copyOf(knowledgeSourceIds);
    }

    /** Legacy request shape intentionally leaves new assignments unspecified. */
    public AgentUpdateRequest(String name, String description, String systemPrompt, String providerId,
                              String modelId, Double temperature, Integer maxOutputTokens, List<String> enabledTools) {
        this(name, description, systemPrompt, providerId, modelId, temperature, maxOutputTokens,
                enabledTools, null, null);
    }

    Agent toAgent(Agent current) {
        return new Agent(current.id(), name.strip(), description.strip(), systemPrompt.strip(), providerId,
                modelId, temperature, maxOutputTokens, enabledTools,
                ruleIds == null ? current.ruleIds() : ruleIds,
                knowledgeSourceIds == null ? current.knowledgeSourceIds() : knowledgeSourceIds);
    }
}
