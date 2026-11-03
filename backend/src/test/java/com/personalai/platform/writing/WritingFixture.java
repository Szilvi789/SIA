package com.personalai.platform.writing;

import com.personalai.platform.agents.AgentService;
import com.personalai.platform.agents.InMemoryAgentRepository;
import com.personalai.platform.knowledge.InMemoryKnowledgeRepository;
import com.personalai.platform.knowledge.KnowledgeRetriever;
import com.personalai.platform.knowledge.KnowledgeService;
import com.personalai.platform.knowledge.NoOpKnowledgeRetriever;
import com.personalai.platform.models.ModelCatalog;
import com.personalai.platform.providers.AIProvider;
import com.personalai.platform.providers.MockAIProvider;
import com.personalai.platform.providers.ProviderRegistry;
import com.personalai.platform.rules.InMemoryRuleRepository;
import com.personalai.platform.rules.RuleService;
import com.personalai.platform.tools.ToolRegistry;
import com.personalai.platform.writing.infrastructure.DemoLanguageDetector;
import com.personalai.platform.writing.infrastructure.InMemoryWritingPresetRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

final class WritingFixture {
    final Clock clock = Clock.fixed(Instant.parse("2026-11-03T10:15:00Z"), ZoneOffset.UTC);
    final InMemoryRuleRepository ruleRepository = new InMemoryRuleRepository();
    final RuleService rules = new RuleService(ruleRepository);
    final WritingPresetService presets = new WritingPresetService(new InMemoryWritingPresetRepository(), ruleRepository, rules);
    final WritingInstructionComposer composer = new WritingInstructionComposer(rules, presets);
    final InMemoryAgentRepository agentRepository = new InMemoryAgentRepository();
    final InMemoryKnowledgeRepository knowledgeRepository = new InMemoryKnowledgeRepository();
    final KnowledgeService knowledge = new KnowledgeService(knowledgeRepository);
    final ModelCatalog models = new ModelCatalog();
    final ToolRegistry tools = new ToolRegistry(List.of());

    AgentService agents(ProviderRegistry providers) {
        return new AgentService(agentRepository, models, providers, rules, knowledge, tools);
    }
    WritingService service() { return service(new MockAIProvider(), new NoOpKnowledgeRetriever()); }
    WritingService service(AIProvider provider, KnowledgeRetriever retriever) {
        var providers = new ProviderRegistry(List.of(provider));
        return new WritingService(agents(providers), models, providers, composer, new DemoLanguageDetector(), knowledge, retriever, tools, clock);
    }
    static WritingRequest request(WritingWorkflow workflow, WritingPreferences preferences) {
        return new WritingRequest(workflow, "Please write a useful draft.", "mock-balanced", preferences, null);
    }
}
