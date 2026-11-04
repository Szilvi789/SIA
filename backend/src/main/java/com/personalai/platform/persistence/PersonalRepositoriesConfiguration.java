package com.personalai.platform.persistence;

import com.personalai.platform.agents.Agent;
import com.personalai.platform.agents.AgentIds;
import com.personalai.platform.agents.AgentRepository;
import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.common.ResourceNotFoundException;
import com.personalai.platform.knowledge.KnowledgeRepository;
import com.personalai.platform.knowledge.KnowledgeSource;
import com.personalai.platform.models.ApplicationModelSettings;
import com.personalai.platform.models.InMemoryModelRepository;
import com.personalai.platform.models.ModelDescriptor;
import com.personalai.platform.models.ModelRepository;
import com.personalai.platform.presets.AgentPresets;
import com.personalai.platform.providers.ProviderConfigurationRepository;
import com.personalai.platform.rules.InMemoryRuleRepository;
import com.personalai.platform.rules.RuleDocument;
import com.personalai.platform.rules.RuleRepository;
import com.personalai.platform.rules.RuleScope;
import com.personalai.platform.usage.UsageRecord;
import com.personalai.platform.usage.UsageRepository;
import com.personalai.platform.writing.DraftRepository;
import com.personalai.platform.writing.WritingDraft;
import com.personalai.platform.writing.WritingPreset;
import com.personalai.platform.writing.WritingPresetRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Private repositories persist aggregates only in PostgreSQL; no repository source files are written. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sai.mode", havingValue = "personal")
public class PersonalRepositoriesConfiguration {
    @Bean
    AgentRepository personalAgentRepository(PersistentDocumentStore store) {
        AgentPresets.defaults().forEach(agent -> store.insertIfAbsent("agents", agent.id(), agent));
        return new AgentRepository() {
            public List<Agent> findAll() { return store.all("agents", Agent.class); }
            public Optional<Agent> findById(String id) {
                return store.find("agents", AgentIds.canonical(id), Agent.class).map(agent -> agent.withId(id));
            }
            public Agent update(Agent agent) {
                var canonical = agent.withId(AgentIds.canonical(agent.id()));
                if (!store.replace("agents", canonical.id(), canonical)) throw new ResourceNotFoundException("Agent was not found.");
                return agent;
            }
        };
    }

    @Bean
    ModelRepository personalModelRepository(PersistentDocumentStore store) {
        var examples = new InMemoryModelRepository();
        examples.findAll().forEach(model -> store.insertIfAbsent("models", model.id(), model));
        store.insertIfAbsent("model-settings", "application", examples.settings());
        return new ModelRepository() {
            public List<ModelDescriptor> findAll() { return store.all("models", ModelDescriptor.class); }
            public Optional<ModelDescriptor> findById(String id) { return store.find("models", id, ModelDescriptor.class); }
            public ModelDescriptor save(ModelDescriptor model) { return store.save("models", model.id(), model); }
            public ApplicationModelSettings settings() {
                return store.find("model-settings", "application", ApplicationModelSettings.class)
                        .orElseThrow(() -> new IllegalStateException("Private model settings are missing."));
            }
            public ApplicationModelSettings saveSettings(ApplicationModelSettings value) { return store.save("model-settings", "application", value); }
        };
    }

    public record ProviderEnabledState(String id, boolean enabled) { }

    @Bean
    ProviderConfigurationRepository personalProviderConfigurationRepository(PersistentDocumentStore store) {
        return new ProviderConfigurationRepository() {
            public Map<String, Boolean> enabledStates() {
                return store.all("provider-configurations", ProviderEnabledState.class).stream()
                        .collect(Collectors.toUnmodifiableMap(ProviderEnabledState::id, ProviderEnabledState::enabled));
            }
            public void setEnabled(String id, boolean enabled) { store.save("provider-configurations", id, new ProviderEnabledState(id, enabled)); }
        };
    }

    @Bean
    RuleRepository personalRuleRepository(PersistentDocumentStore store) {
        // Packaged generic examples are read-only defaults, never a target for user Markdown writes.
        var examples = new InMemoryRuleRepository();
        return new RuleRepository() {
            public List<RuleDocument> findAll() {
                var all = new TreeMap<String, RuleDocument>();
                examples.findAll().forEach(rule -> all.put(rule.id(), rule));
                store.all("rules", RuleDocument.class).forEach(rule -> all.put(rule.id(), rule));
                return List.copyOf(all.values());
            }
            public Optional<RuleDocument> findById(String id) {
                return store.find("rules", id, RuleDocument.class).or(() -> examples.findById(id));
            }
            public RuleDocument saveUserOwned(RuleDocument rule) {
                if (rule == null || !rule.id().matches("writing-user-[a-z0-9-]{1,60}") || rule.scope() != RuleScope.AGENT) {
                    throw new InvalidConfigurationException("Only user-owned writing preset rules can be edited.");
                }
                return store.save("rules", rule.id(), rule);
            }
            public RuleDocument saveDefaultIfAbsent(RuleDocument rule) {
                if (rule == null || !rule.id().matches("writing-user-[a-z0-9-]{1,60}") || rule.scope() != RuleScope.AGENT) {
                    throw new InvalidConfigurationException("Only user-owned writing preset rules can be edited.");
                }
                store.insertIfAbsent("rules", rule.id(), rule);
                return findById(rule.id()).orElseThrow();
            }
        };
    }

    @Bean
    KnowledgeRepository personalKnowledgeRepository(PersistentDocumentStore store) {
        return new KnowledgeRepository() {
            public List<KnowledgeSource> findAll() { return store.all("knowledge", KnowledgeSource.class); }
            public Optional<KnowledgeSource> findById(String id) { return store.find("knowledge", id, KnowledgeSource.class); }
            public KnowledgeSource save(KnowledgeSource source) { return store.save("knowledge", source.id(), source); }
        };
    }

    @Bean
    DraftRepository personalDraftRepository(PersistentDocumentStore store) {
        return new DraftRepository() {
            public List<WritingDraft> findAll() {
                return store.all("drafts", WritingDraft.class).stream().sorted(Comparator.comparing(WritingDraft::updatedAt).reversed()).toList();
            }
            public Optional<WritingDraft> findById(String id) { return store.find("drafts", id, WritingDraft.class); }
            public WritingDraft save(WritingDraft draft, Long expectedRevision) {
                if (draft.revision() == null || !store.saveVersioned("drafts", draft.id(), draft, draft.revision(), expectedRevision)) {
                    throw new InvalidConfigurationException("revision", "This draft changed. Reload it before saving again.");
                }
                return draft;
            }
        };
    }

    @Bean
    WritingPresetRepository personalWritingPresetRepository(PersistentDocumentStore store) {
        return new WritingPresetRepository() {
            public List<WritingPreset> findAll() {
                return store.all("writing-presets", WritingPreset.class).stream().sorted(Comparator.comparing(WritingPreset::name)).toList();
            }
            public Optional<WritingPreset> findById(String id) { return store.find("writing-presets", id, WritingPreset.class); }
            public WritingPreset save(WritingPreset preset) { return store.save("writing-presets", preset.id(), preset); }
            public WritingPreset saveDefaultIfAbsent(WritingPreset preset) {
                store.insertIfAbsent("writing-presets", preset.id(), preset);
                return findById(preset.id()).orElseThrow();
            }
        };
    }

    @Bean
    UsageRepository personalUsageRepository(PersistentDocumentStore store) {
        return new UsageRepository() {
            public UsageRecord save(UsageRecord value) { return store.save("usage", value.id(), value); }
            public List<UsageRecord> findAll() {
                return store.all("usage", UsageRecord.class).stream()
                        .sorted(Comparator.comparing(UsageRecord::timestamp).reversed().thenComparing(UsageRecord::id)).toList();
            }
        };
    }
}
