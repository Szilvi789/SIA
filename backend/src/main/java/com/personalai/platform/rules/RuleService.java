package com.personalai.platform.rules;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.common.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RuleService {
    private final RuleRepository repository;

    public RuleService(RuleRepository repository) {
        this.repository = repository;
    }

    public List<RuleDocument> findAll() {
        return repository.findAll().stream().sorted(Comparator.comparing(RuleDocument::id)).toList();
    }

    public RuleDocument findById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule was not found."));
    }

    public void validateAssignments(List<String> ruleIds) {
        assignedRules(ruleIds);
    }

    /**
     * Composes policy, sorted global rules, the existing agent prompt, and assigned
     * agent rules in assignment order. Earlier sections win conflicts. Knowledge
     * and user content are intentionally absent from this instruction-only API.
     */
    public ResolvedInstructions resolve(String agentSystemPrompt, List<String> ruleIds) {
        return resolveWithCore(agentSystemPrompt, ruleIds, List.of());
    }

    /** Application-selected immutable core documents precede agent-specific configuration. */
    public ResolvedInstructions resolveWithCore(String agentSystemPrompt, List<String> ruleIds, List<String> coreRuleIds) {
        if (agentSystemPrompt == null || agentSystemPrompt.isBlank()) {
            throw new InvalidConfigurationException("An agent system prompt is required.");
        }
        List<RuleDocument> assigned = assignedRules(ruleIds);
        var sections = new ArrayList<String>();
        var appliedRuleIds = new ArrayList<String>();
        sections.add(ApplicationSecurityPolicy.instructions());
        findAll().stream().filter(rule -> rule.scope() == RuleScope.GLOBAL).forEach(rule -> {
            sections.add("## Global rule: " + rule.id() + "\n\n" + rule.content().strip());
            appliedRuleIds.add(rule.id());
        });
        for (String id : coreRuleIds) {
            var core = findById(id);
            if (id.startsWith("writing-user-")) throw new InvalidConfigurationException("User rules cannot become core policy.");
            sections.add("## Immutable writing core: " + core.id() + "\n\n" + core.content().strip());
            appliedRuleIds.add(core.id());
        }
        sections.add("## Agent instructions\n\n" + agentSystemPrompt.strip());
        assigned.forEach(rule -> {
            sections.add("## Assigned agent rule: " + rule.id() + "\n\n" + rule.content().strip());
            appliedRuleIds.add(rule.id());
        });
        return new ResolvedInstructions(String.join("\n\n", sections), appliedRuleIds);
    }

    private List<RuleDocument> assignedRules(List<String> ruleIds) {
        if (ruleIds == null) {
            return List.of();
        }
        var seen = new HashSet<String>();
        var assigned = new ArrayList<RuleDocument>();
        for (String id : ruleIds) {
            if (id == null || id.isBlank()) {
                throw new InvalidConfigurationException("Assigned rule IDs cannot be blank.");
            }
            if (!seen.add(id)) {
                throw new InvalidConfigurationException("Duplicate assigned rule ID: " + id);
            }
            RuleDocument rule = repository.findById(id)
                    .orElseThrow(() -> new InvalidConfigurationException("Unknown assigned rule: " + id));
            if (rule.scope() != RuleScope.AGENT) {
                throw new InvalidConfigurationException("Global rules apply automatically and cannot be assigned: " + id);
            }
            assigned.add(rule);
        }
        return List.copyOf(assigned);
    }
}
