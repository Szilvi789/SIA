package com.personalai.platform.agents;

import com.personalai.platform.common.ResourceNotFoundException;
import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.knowledge.KnowledgeService;
import com.personalai.platform.models.ModelResolver;
import com.personalai.platform.rules.RuleService;
import com.personalai.platform.tools.ToolRegistry;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AgentService {
    private final AgentRepository repository;
    private final ModelResolver resolver;
    private final RuleService rules;
    private final KnowledgeService knowledge;
    private final ToolRegistry tools;

    public AgentService(AgentRepository repository, ModelResolver resolver,
                        RuleService rules, KnowledgeService knowledge, ToolRegistry tools) {
        this.repository = repository;
        this.resolver = resolver;
        this.rules = rules;
        this.knowledge = knowledge;
        this.tools = tools;
    }

    public List<Agent> findAll() {
        return repository.findAll();
    }

    public Agent findById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent was not found."));
    }

    public Agent update(String id, AgentUpdateRequest request) {
        var updated = request.toAgent(findById(id));
        resolver.validateAgentSettings(updated);
        validateField("ruleIds", () -> rules.validateAssignments(updated.ruleIds()));
        validateField("knowledgeSourceIds", () -> knowledge.validateAssignments(id, updated.knowledgeSourceIds()));
        validateField("enabledTools", () -> tools.validateEnabledTools(updated.enabledTools()));
        return repository.update(updated);
    }

    private void validateField(String field, Runnable validation) {
        try {
            validation.run();
        } catch (InvalidConfigurationException exception) {
            throw new InvalidConfigurationException(field, exception.getMessage());
        }
    }
}
