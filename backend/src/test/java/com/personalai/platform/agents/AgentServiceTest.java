package com.personalai.platform.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.common.ResourceNotFoundException;
import com.personalai.platform.knowledge.InMemoryKnowledgeRepository;
import com.personalai.platform.knowledge.KnowledgeAssociation;
import com.personalai.platform.knowledge.KnowledgeFormat;
import com.personalai.platform.knowledge.KnowledgeService;
import com.personalai.platform.knowledge.KnowledgeSource;
import com.personalai.platform.models.ModelCatalog;
import com.personalai.platform.providers.MockAIProvider;
import com.personalai.platform.providers.ProviderRegistry;
import com.personalai.platform.rules.InMemoryRuleRepository;
import com.personalai.platform.rules.RuleService;
import com.personalai.platform.tools.AgentTool;
import com.personalai.platform.tools.ToolDefinition;
import com.personalai.platform.tools.ToolPermission;
import com.personalai.platform.tools.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AgentServiceTest {
    private AgentService agents;
    private InMemoryAgentRepository repository;
    private InMemoryKnowledgeRepository sources;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAgentRepository();
        sources = new InMemoryKnowledgeRepository();
        sources.save(new KnowledgeSource("coding-guide", "coding-guide.md", KnowledgeFormat.MARKDOWN,
                "stored-coding-guide", 120,
                List.of(new KnowledgeAssociation(KnowledgeAssociation.Scope.AGENT, "coding-agent"))));
        agents = serviceWith(new ToolRegistry(List.of()));
    }

    @Test
    void startsWithThreeCanonicalPresets() {
        assertThat(agents.findAll()).extracting(Agent::id).containsExactly(
                "coding-agent", "documentation-agent", "writing-agent");
        assertThat(agents.findAll()).allSatisfy(agent -> {
            assertThat(agent.providerId()).isEqualTo("mock");
            assertThat(agent.enabledTools()).isEmpty();
            assertThat(agent.ruleIds()).isEmpty();
            assertThat(agent.knowledgeSourceIds()).isEmpty();
        });
    }

    @Test
    void persistsConfigurationWithoutChangingIdentityOrOtherAgents() {
        var translationBefore = agents.findById("translation-agent");
        var request = request("mock", "mock-precise");

        var updated = agents.update("coding-agent", request);

        assertThat(updated.id()).isEqualTo("coding-agent");
        assertThat(updated.name()).isEqualTo("My code reviewer");
        assertThat(updated.systemPrompt()).isEqualTo("Review security and explain tradeoffs.");
        assertThat(updated.temperature()).isEqualTo(0.1);
        assertThat(updated.maxOutputTokens()).isEqualTo(1024);
        assertThat(agents.findById("coding-agent")).isEqualTo(updated);
        assertThat(agents.findById("translation-agent")).isEqualTo(translationBefore);
    }

    @Test
    void rejectsUnknownProviderWithoutSavingAnyChanges() {
        var original = agents.findById("coding-agent");

        assertThatThrownBy(() -> agents.update("coding-agent", request("openai", "mock-balanced")))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThat(agents.findById("coding-agent")).isEqualTo(original);
    }

    @Test
    void rejectsUnknownModelWithoutSavingAnyChanges() {
        var original = agents.findById("coding-agent");

        assertThatThrownBy(() -> agents.update("coding-agent", request("mock", "unknown-model")))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThat(agents.findById("coding-agent")).isEqualTo(original);
    }

    @Test
    void cannotCreateAgentsThroughTheUpdateEndpoint() {
        assertThatThrownBy(() -> agents.update("missing-agent", request("mock", "mock-balanced")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(agents.findAll()).hasSize(3);
    }

    @Test
    void makesToolConfigurationImmutable() {
        var tools = new ArrayList<String>();
        var agent = new Agent("test", "Test", "Test", "Test", "mock", "mock-balanced", 0.2, 1024, tools);
        tools.add("terminal");

        assertThat(agent.enabledTools()).isEmpty();
        assertThatThrownBy(() -> agent.enabledTools().add("filesystem"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void legacyUpdatePreservesExistingRuleAndKnowledgeAssignments() {
        agents.update("coding-agent", requestWithAssignments(List.of("coding-rules"), List.of("coding-guide")));

        var updated = agents.update("coding-agent", request("mock", "mock-precise"));

        assertThat(updated.ruleIds()).containsExactly("coding-rules");
        assertThat(updated.knowledgeSourceIds()).containsExactly("coding-guide");
        assertThat(updated.modelId()).isEqualTo("mock-precise");
        assertThat(agents.findById("coding-agent")).isEqualTo(updated);
    }

    @Test
    void explicitEmptyAssignmentsClearExistingRuleAndKnowledgeConfiguration() {
        agents.update("coding-agent", requestWithAssignments(List.of("coding-rules"), List.of("coding-guide")));

        var updated = agents.update("coding-agent", requestWithAssignments(List.of(), List.of()));

        assertThat(updated.ruleIds()).isEmpty();
        assertThat(updated.knowledgeSourceIds()).isEmpty();
        assertThat(agents.findById("coding-agent")).isEqualTo(updated);
    }

    @ParameterizedTest
    @CsvSource({"missing-rule,coding-guide", "coding-rules,missing-source"})
    void rejectsUnknownAssignmentsWithoutSavingChanges(String ruleId, String sourceId) {
        var original = agents.findById("coding-agent");

        assertThatThrownBy(() -> agents.update("coding-agent",
                requestWithAssignments(List.of(ruleId), List.of(sourceId))))
                .isInstanceOf(InvalidConfigurationException.class);

        assertThat(agents.findById("coding-agent")).isEqualTo(original);
    }

    @Test
    void rejectsAnUnknownToolWithoutSavingChanges() {
        var original = agents.findById("coding-agent");
        var request = new AgentUpdateRequest("Reviewer", "Reviews code", "Review code", "mock", "mock-balanced",
                0.1, 512, List.of("unknown-tool"), List.of(), List.of());

        assertThatThrownBy(() -> agents.update("coding-agent", request))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThat(agents.findById("coding-agent")).isEqualTo(original);
    }

    @Test
    void acceptsARegisteredToolAsAnEnabledCapability() {
        var tool = mock(AgentTool.class);
        when(tool.definition()).thenReturn(new ToolDefinition("read-project", "Read project", "Read approved files",
                Set.of(ToolPermission.READ_FILES)));
        agents = serviceWith(new ToolRegistry(List.of(tool)));
        var request = new AgentUpdateRequest("Reviewer", "Reviews code", "Review code", "mock", "mock-balanced",
                0.1, 512, List.of("read-project"), List.of("coding-rules"), List.of("coding-guide"));

        var updated = agents.update("coding-agent", request);

        assertThat(updated.enabledTools()).containsExactly("read-project");
        assertThat(updated.ruleIds()).containsExactly("coding-rules");
        assertThat(updated.knowledgeSourceIds()).containsExactly("coding-guide");
        assertThat(agents.findById("coding-agent")).isEqualTo(updated);
    }

    @Test
    void copiesAndProtectsNewAssignmentLists() {
        var ruleIds = new ArrayList<>(List.of("coding-rules"));
        var sourceIds = new ArrayList<>(List.of("coding-guide"));
        var agent = new Agent("test", "Test", "Test", "Test", "mock", "mock-balanced", 0.2, 1024,
                List.of(), ruleIds, sourceIds);

        ruleIds.clear();
        sourceIds.clear();

        assertThat(agent.ruleIds()).containsExactly("coding-rules");
        assertThat(agent.knowledgeSourceIds()).containsExactly("coding-guide");
        assertThatThrownBy(() -> agent.ruleIds().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> agent.knowledgeSourceIds().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private AgentService serviceWith(ToolRegistry tools) {
        return new AgentService(repository, new ModelCatalog(), new ProviderRegistry(List.of(new MockAIProvider())),
                new RuleService(new InMemoryRuleRepository()), new KnowledgeService(sources), tools);
    }

    private AgentUpdateRequest requestWithAssignments(List<String> ruleIds, List<String> sourceIds) {
        return new AgentUpdateRequest("My code reviewer", "Review small changes.",
                "Review security and explain tradeoffs.", "mock", "mock-balanced", 0.1, 1024,
                List.of(), ruleIds, sourceIds);
    }

    private AgentUpdateRequest request(String providerId, String modelId) {
        return new AgentUpdateRequest("  My code reviewer  ", "Review small changes.",
                "Review security and explain tradeoffs.", providerId, modelId, 0.1, 1024, List.of());
    }
}
