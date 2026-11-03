package com.personalai.platform.chat;

import com.personalai.platform.agents.AgentService;
import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.knowledge.KnowledgeQuery;
import com.personalai.platform.knowledge.KnowledgeRetriever;
import com.personalai.platform.knowledge.KnowledgeService;
import com.personalai.platform.models.ModelCatalog;
import com.personalai.platform.providers.ProviderRegistry;
import com.personalai.platform.providers.ProviderRequest;
import com.personalai.platform.rules.RuleService;
import com.personalai.platform.tools.ToolRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultAgentExecutionService implements AgentExecutionService {
    private final AgentService agents;
    private final ModelCatalog models;
    private final ProviderRegistry providers;
    private final Clock clock;
    private final RuleService rules;
    private final KnowledgeService knowledge;
    private final KnowledgeRetriever retriever;
    private final ToolRegistry tools;

    public DefaultAgentExecutionService(AgentService agents, ModelCatalog models, ProviderRegistry providers, Clock clock,
                                        RuleService rules, KnowledgeService knowledge, KnowledgeRetriever retriever,
                                        ToolRegistry tools) {
        this.agents = agents;
        this.models = models;
        this.providers = providers;
        this.clock = clock;
        this.rules = rules;
        this.knowledge = knowledge;
        this.retriever = retriever;
        this.tools = tools;
    }

    @Override
    public ChatResponse execute(ChatRequest request) {
        var agent = agents.findById(request.agentId());
        var model = models.requireCompatible(agent.providerId(), request.modelId());
        var provider = providers.require(agent.providerId());
        var instructions = rules.resolve(agent.systemPrompt(), agent.ruleIds());
        knowledge.validateAssignments(agent.id(), agent.knowledgeSourceIds());
        var availableTools = tools.definitionsFor(agent.enabledTools());
        var message = request.message().strip();
        var context = retriever.retrieve(new KnowledgeQuery(agent.id(), agent.knowledgeSourceIds(), message));
        if (context.stream().anyMatch(excerpt -> !agent.knowledgeSourceIds().contains(excerpt.sourceId()))) {
            throw new InvalidConfigurationException("Knowledge retrieval returned an unassigned source.");
        }
        var content = provider.generate(new ProviderRequest(agent, model, message, instructions, context, availableTools));
        return new ChatResponse(UUID.randomUUID().toString(), agent.id(), model.id(), content, Instant.now(clock));
    }
}
