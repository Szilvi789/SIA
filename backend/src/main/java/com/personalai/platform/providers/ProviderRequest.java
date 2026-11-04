package com.personalai.platform.providers;

import com.personalai.platform.agents.Agent;
import com.personalai.platform.knowledge.KnowledgeExcerpt;
import com.personalai.platform.models.ModelDescriptor;
import com.personalai.platform.models.GenerationParameters;
import com.personalai.platform.rules.ResolvedInstructions;
import com.personalai.platform.tools.ToolDefinition;
import java.util.List;
import java.util.Objects;
import com.personalai.platform.writing.WritingContext;

/**
 * Trusted instructions, user input, and untrusted reference data are separate channels.
 * Future adapters must preserve that boundary. Tool definitions advertise capabilities,
 * never permission to execute; all calls must pass through the tool authorization service.
 */
public record ProviderRequest(
        Agent agent,
        ModelDescriptor model,
        String message,
        ResolvedInstructions instructions,
        List<KnowledgeExcerpt> knowledgeContext,
        List<ToolDefinition> availableTools,
        WritingContext writingContext,
        GenerationParameters parameters) {
    public ProviderRequest {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(instructions, "instructions");
        knowledgeContext = List.copyOf(knowledgeContext);
        availableTools = List.copyOf(availableTools);
        parameters = parameters == null ? GenerationParameters.empty() : parameters;
    }

    public ProviderRequest(Agent agent, ModelDescriptor model, String message, ResolvedInstructions instructions,
            List<KnowledgeExcerpt> knowledgeContext, List<ToolDefinition> availableTools, WritingContext writingContext) {
        this(agent, model, message, instructions, knowledgeContext, availableTools, writingContext,
                new GenerationParameters(agent.maxOutputTokens(), agent.temperature(), null, null, null, agent.enabledTools()));
    }

    public ProviderRequest(Agent agent, ModelDescriptor model, String message, ResolvedInstructions instructions,
            List<KnowledgeExcerpt> knowledgeContext, List<ToolDefinition> availableTools) {
        this(agent, model, message, instructions, knowledgeContext, availableTools, null,
                new GenerationParameters(agent.maxOutputTokens(), agent.temperature(), null, null, null, agent.enabledTools()));
    }
}
