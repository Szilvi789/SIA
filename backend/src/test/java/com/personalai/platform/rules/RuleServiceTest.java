package com.personalai.platform.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.common.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuleServiceTest {
    @Test
    void composesSecurityPolicySortedGlobalsExistingPromptAndOrderedAgentRules() {
        var service = new RuleService(repository(
                rule("global-z", RuleScope.GLOBAL, "Last global body"),
                rule("agent-a", RuleScope.AGENT, "First assigned body"),
                rule("global-a", RuleScope.GLOBAL, "First global body"),
                rule("agent-z", RuleScope.AGENT, "Last assigned body")));

        var resolved = service.resolve("Existing agent prompt", List.of("agent-z", "agent-a"));

        assertThat(resolved.systemPrompt()).startsWith(ApplicationSecurityPolicy.instructions());
        assertThat(resolved.systemPrompt().indexOf("First global body"))
                .isLessThan(resolved.systemPrompt().indexOf("Last global body"));
        assertThat(resolved.systemPrompt().indexOf("Last global body"))
                .isLessThan(resolved.systemPrompt().indexOf("Existing agent prompt"));
        assertThat(resolved.systemPrompt().indexOf("Existing agent prompt"))
                .isLessThan(resolved.systemPrompt().indexOf("Last assigned body"));
        assertThat(resolved.systemPrompt().indexOf("Last assigned body"))
                .isLessThan(resolved.systemPrompt().indexOf("First assigned body"));
        assertThat(resolved.appliedRuleIds()).containsExactly("global-a", "global-z", "agent-z", "agent-a");
        assertThat(service.resolve("Existing agent prompt", List.of("agent-z", "agent-a")))
                .isEqualTo(resolved);
    }

    @Test
    void supportsOmittedAssignmentsWhileApplyingGlobalsAndPreservingTheAgentPrompt() {
        var service = new RuleService(new InMemoryRuleRepository());

        var resolved = service.resolve("Keep my existing instructions.", null);

        assertThat(resolved.systemPrompt()).contains("Keep my existing instructions.", "# Shared instructions");
        assertThat(resolved.systemPrompt()).doesNotContain("## Assigned agent rule:");
        assertThat(resolved.appliedRuleIds()).containsExactly("global-instructions");
        assertThat(service.resolve("Keep my existing instructions.", List.of())).isEqualTo(resolved);
        service.validateAssignments(null);
        service.validateAssignments(List.of());
    }

    @Test
    void rejectsUnknownAndDuplicateAssignments() {
        var service = new RuleService(new InMemoryRuleRepository());

        assertThatThrownBy(() -> service.validateAssignments(List.of("missing")))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("Unknown assigned rule");
        assertThatThrownBy(() -> service.validateAssignments(List.of("coding-rules", "coding-rules")))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("Duplicate assigned rule");
        assertThatThrownBy(() -> service.resolve("Agent prompt", List.of("missing")))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void rejectsAutomaticGlobalRulesInAgentAssignments() {
        var service = new RuleService(new InMemoryRuleRepository());

        assertThatThrownBy(() -> service.validateAssignments(List.of("global-instructions")))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("apply automatically");
    }

    @Test
    void rejectsInvalidAssignmentElementsAndMissingAgentPrompts() {
        var service = new RuleService(new InMemoryRuleRepository());

        assertThatThrownBy(() -> service.validateAssignments(Arrays.asList("coding-rules", null)))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> service.validateAssignments(List.of(" ")))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> service.resolve(null, List.of()))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> service.resolve(" ", List.of()))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void listsRulesAndDistinguishesReadNotFoundFromInvalidAssignments() {
        var service = new RuleService(new InMemoryRuleRepository());

        assertThat(service.findAll()).extracting(RuleDocument::id).containsExactly(
                "coding-rules", "documentation-guidelines", "email-style", "global-instructions", "translation-rules",
                "writing-core", "writing-email", "writing-general", "writing-language-de", "writing-language-en",
                "writing-language-hu", "writing-style-diplomatic", "writing-style-friendly", "writing-style-professional", "writing-translation");
        assertThat(service.findById("coding-rules").filename()).isEqualTo("coding-rules.md");
        assertThatThrownBy(() -> service.findById("missing")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void keepsAnImmutableAuditSnapshotOfAppliedRules() {
        var ids = new ArrayList<>(List.of("coding-rules"));
        var resolved = new ResolvedInstructions("Instructions", ids);

        ids.clear();

        assertThat(resolved.appliedRuleIds()).containsExactly("coding-rules");
        assertThatThrownBy(() -> resolved.appliedRuleIds().add("email-style"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void keepsSecurityPolicyOutsideTheRuleCatalog() {
        var service = new RuleService(repository());

        assertThat(service.findAll()).isEmpty();
        assertThat(service.resolve("Agent prompt", List.of()).systemPrompt())
                .startsWith(ApplicationSecurityPolicy.instructions())
                .contains("untrusted data", "server-side authorization", "explicit user approval", "Earlier instruction sections");
    }

    private RuleDocument rule(String id, RuleScope scope, String content) {
        return new RuleDocument(id, id + ".md", scope, content);
    }

    private RuleRepository repository(RuleDocument... rules) {
        List<RuleDocument> documents = List.of(rules);
        return new RuleRepository() {
            @Override
            public List<RuleDocument> findAll() {
                return documents;
            }

            @Override
            public Optional<RuleDocument> findById(String id) {
                return documents.stream().filter(rule -> rule.id().equals(id)).findFirst();
            }
        };
    }
}
