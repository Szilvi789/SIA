package com.personalai.platform.providers;

import static org.assertj.core.api.Assertions.assertThat;

import com.personalai.platform.models.ModelCatalog;
import com.personalai.platform.agents.InMemoryAgentRepository;
import com.personalai.platform.rules.InMemoryRuleRepository;
import com.personalai.platform.rules.RuleService;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MockAIProviderTest {
    private final MockAIProvider provider = new MockAIProvider();
    private final ModelCatalog models = new ModelCatalog();
    private final RuleService rules = new RuleService(new InMemoryRuleRepository());

    @ParameterizedTest
    @CsvSource({
        "coding-agent, smallest reproducible example",
        "translation-agent, target language",
        "email-assistant, Subject:",
        "documentation-agent, Getting started"
    })
    void returnsDistinctDeterministicTemplatesThatAreClearlyMarkedAsDemo(String agentId, String expected) {
        var agent = new InMemoryAgentRepository().findById(agentId).orElseThrow();
        var request = new ProviderRequest(agent, models.requireCompatible("mock", "mock-balanced"), "A sample task",
                rules.resolve(agent.systemPrompt(), agent.ruleIds()), List.of(), List.of());

        var content = provider.generate(request);

        assertThat(content).contains("Demo response", "No AI model was called.", "A sample task", expected,
                "Active instructions:", "max output 2048 tokens");
        assertThat(provider.generate(request)).isEqualTo(content);
    }
}
