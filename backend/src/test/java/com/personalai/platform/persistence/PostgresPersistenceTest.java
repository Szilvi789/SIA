package com.personalai.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.PersonalAiAgentPlatformApplication;
import com.personalai.platform.agents.AgentRepository;
import com.personalai.platform.knowledge.KnowledgeFormat;
import com.personalai.platform.knowledge.KnowledgeSource;
import com.personalai.platform.models.ApplicationModelSettings;
import com.personalai.platform.rules.RuleDocument;
import com.personalai.platform.rules.RuleScope;
import com.personalai.platform.writing.WritingDraft;
import com.personalai.platform.writing.WritingPreset;
import com.personalai.platform.writing.WritingWorkflow;
import com.personalai.platform.writing.DraftService;
import com.personalai.platform.writing.WritingPresetService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Opt-in real PostgreSQL restart check for a disposable test database containing only synthetic data.
 * The aggregate test uses a unique schema; full application startup verifies its fixed sai_personal schema.
 * Supply SAI_TEST_DB_URL, SAI_TEST_DB_USER and SAI_TEST_DB_PASSWORD_FILE for a disposable test database.
 * Leaves its uniquely named schema intact; no automatic database or schema deletion occurs.
 */
@EnabledIfEnvironmentVariable(named = "SAI_TEST_DB_URL", matches = ".+")
class PostgresPersistenceTest {
    public record SyntheticConversation(String id, String text) { }
    public record SyntheticPermission(String provider, boolean allow) { }

    @Test
    void actualPersonalApplicationStartsWithPostgresAndRetainsDraftsAndMarkdownOnRestart() throws Exception {
        Path privateBase = Files.createTempDirectory("sai-postgres-restart-");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String presetId = "synthetic-" + suffix;
        String savedDraft;
        try (var first = personalApplication(privateBase)) {
            assertThat(first.getBeansOfType(javax.sql.DataSource.class)).hasSize(1);
            assertThat(first.getBean(AgentRepository.class).getClass().getName()).doesNotContain("InMemory");
            var draft = new WritingDraft(null, "Synthetic restart draft", WritingWorkflow.GENERAL, "Synthetic input",
                    "Synthetic content " + suffix, "", null, null, "mock-balanced", null, null, null);
            savedDraft = first.getBean(DraftService.class).create(draft).id();
            first.getBean(WritingPresetService.class).create(new WritingPreset(presetId, "Synthetic private preset", "Restart test",
                    WritingWorkflow.GENERAL, true, List.of("writing-general"), "Synthetic private Markdown " + suffix, null));
        }
        try (var restarted = personalApplication(privateBase)) {
            assertThat(restarted.getBean(DraftService.class).findById(savedDraft).content()).isEqualTo("Synthetic content " + suffix);
            assertThat(restarted.getBean(WritingPresetService.class).findById(presetId).markdown()).isEqualTo("Synthetic private Markdown " + suffix);
            assertThat(restarted.getBean(com.personalai.platform.rules.RuleRepository.class).findById("writing-user-" + presetId).orElseThrow().content())
                    .isEqualTo("Synthetic private Markdown " + suffix);
        }
    }

    private ConfigurableApplicationContext personalApplication(Path privateBase) {
        return SpringApplication.run(PersonalAiAgentPlatformApplication.class,
                "--server.port=0", "--server.address=127.0.0.1", "--sai.mode=personal", "--sai.data-dir=" + privateBase,
                "--sai.database.url=" + System.getenv("SAI_TEST_DB_URL"), "--sai.database.username=" + System.getenv("SAI_TEST_DB_USER"),
                "--sai.database.password-file=" + System.getenv("SAI_TEST_DB_PASSWORD_FILE"), "--logging.level.root=WARN");
    }

    @Test
    void privateRecordsAndEditedDefaultsSurviveACompletePoolAndRepositoryRestart() throws Exception {
        String schema = "sai_test_" + UUID.randomUUID().toString().replace("-", "");
        var configuration = new PersonalRepositoriesConfiguration();
        String agentId;
        var now = Instant.now();
        var draft = new WritingDraft("synthetic-draft", "Synthetic draft", WritingWorkflow.GENERAL,
                "Synthetic input", "Synthetic saved content", "", null, null, "mock-balanced", null, 1L, now);
        var rule = new RuleDocument("writing-user-synthetic", "writing-user-synthetic.md", RuleScope.AGENT, "Synthetic personal rule.");
        var preset = new WritingPreset("synthetic", "Synthetic preset", "For restart verification only", WritingWorkflow.GENERAL,
                true, List.of("writing-general"), "Synthetic preset text", null);
        try (var first = dataSource(schema)) {
            var store = migratedStore(first, schema);
            var agents = configuration.personalAgentRepository(store);
            var agent = agents.findAll().getFirst();
            agentId = agent.id();
            var edited = new com.personalai.platform.agents.Agent(agent.id(), "Synthetic edited name", agent.description(),
                    agent.systemPrompt(), agent.providerId(), agent.modelId(), agent.temperature(), agent.maxOutputTokens(),
                    agent.enabledTools(), agent.ruleIds(), agent.knowledgeSourceIds(), agent.modelSettings());
            agents.update(edited);
            configuration.personalRuleRepository(store).saveUserOwned(rule);
            configuration.personalDraftRepository(store).save(draft, null);
            configuration.personalWritingPresetRepository(store).save(preset);
            configuration.personalModelRepository(store).saveSettings(new ApplicationModelSettings("mock-precise", Map.of("BALANCED", "mock-precise")));
            configuration.personalProviderConfigurationRepository(store).setEnabled("mock", false);
            configuration.personalKnowledgeRepository(store).save(new KnowledgeSource("synthetic-source", "synthetic.md", KnowledgeFormat.MARKDOWN,
                    "synthetic-key", 10, List.of()));
            store.save("conversations", "synthetic-conversation", new SyntheticConversation("synthetic-conversation", "Synthetic conversation text"));
            store.save("provider-permissions", "synthetic-provider", new SyntheticPermission("synthetic-provider", true));
            assertThat(new JdbcTemplate(first).queryForObject("SELECT origin FROM sai_agents WHERE id = ?", String.class, agentId)).isEqualTo("user");
        }
        try (var restarted = dataSource(schema)) {
            var store = migratedStore(restarted, schema);
            assertThat(configuration.personalAgentRepository(store).findById(agentId).orElseThrow().name()).isEqualTo("Synthetic edited name");
            assertThat(configuration.personalRuleRepository(store).findById(rule.id())).contains(rule);
            assertThat(configuration.personalWritingPresetRepository(store).findById(preset.id())).contains(preset);
            assertThat(configuration.personalModelRepository(store).settings().defaultModelId()).isEqualTo("mock-precise");
            assertThat(configuration.personalProviderConfigurationRepository(store).enabledStates()).containsEntry("mock", false);
            assertThat(configuration.personalKnowledgeRepository(store).findById("synthetic-source")).isPresent();
            assertThat(configuration.personalDraftRepository(store).findById(draft.id())).contains(draft);
            assertThat(store.find("conversations", "synthetic-conversation", SyntheticConversation.class).orElseThrow().text()).contains("Synthetic");
            assertThat(store.find("provider-permissions", "synthetic-provider", SyntheticPermission.class).orElseThrow().allow()).isTrue();
            var changed = new WritingDraft(draft.id(), draft.title(), draft.workflow(), draft.input(), "Synthetic updated content", draft.subject(),
                    draft.preferences(), draft.email(), draft.modelId(), null, 2L, now.plusSeconds(1));
            configuration.personalDraftRepository(store).save(changed, 1L);
            assertThatThrownBy(() -> configuration.personalDraftRepository(store).save(changed, 1L)).isInstanceOf(InvalidConfigurationException.class);
            store.mutate("conversations", "synthetic-conversation", SyntheticConversation.class,
                    current -> new SyntheticConversation(current.orElseThrow().id(), "Synthetic appended content"));
            assertThat(store.find("conversations", "synthetic-conversation", SyntheticConversation.class).orElseThrow().text()).contains("appended");
        }
    }

    private HikariDataSource dataSource(String schema) throws Exception {
        var config = new HikariConfig();
        config.setJdbcUrl(System.getenv("SAI_TEST_DB_URL"));
        config.setUsername(System.getenv("SAI_TEST_DB_USER"));
        config.setPassword(Files.readString(Path.of(System.getenv("SAI_TEST_DB_PASSWORD_FILE"))).stripTrailing());
        config.setConnectionInitSql("SET search_path TO " + schema);
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }

    private PersistentDocumentStore migratedStore(HikariDataSource source, String schema) {
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .cleanDisabled(true).locations("classpath:db/migration").load().migrate();
        return new PersistentDocumentStore(new JdbcTemplate(source));
    }
}
