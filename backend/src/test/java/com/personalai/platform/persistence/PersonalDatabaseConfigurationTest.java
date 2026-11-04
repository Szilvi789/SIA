package com.personalai.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalai.platform.agents.InMemoryAgentRepository;
import com.personalai.platform.knowledge.InMemoryKnowledgeRepository;
import com.personalai.platform.models.InMemoryModelRepository;
import com.personalai.platform.providers.InMemoryProviderConfigurationRepository;
import com.personalai.platform.rules.InMemoryRuleRepository;
import com.personalai.platform.usage.InMemoryUsageRepository;
import com.personalai.platform.writing.infrastructure.InMemoryDraftRepository;
import com.personalai.platform.writing.infrastructure.InMemoryWritingPresetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class PersonalDatabaseConfigurationTest {
    @Test
    void personalModeCannotSilentlyStartWithAnEphemeralOrEmbeddedDatabase() {
        assertThatThrownBy(() -> PersonalDatabaseConfiguration.validateConnectionSettings("", "", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No in-memory fallback");
        assertThatThrownBy(() -> PersonalDatabaseConfiguration.validateConnectionSettings("jdbc:h2:mem:test", "app", "private.txt"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PersonalDatabaseConfiguration.validateConnectionSettings("jdbc:postgresql://localhost/private", "app", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SAI_DB_PASSWORD_FILE");
    }

    @Test
    void databaseUrlCannotSmuggleCredentialsOrOverrideThePersonalSchema() {
        for (String query : new String[] {"password=sensitive", "user=other", "currentSchema=demo", "options=escape", "socketFactory=custom", "current%53chema=demo"}) {
            assertThatThrownBy(() -> PersonalDatabaseConfiguration.validateConnectionSettings(
                    "jdbc:postgresql://localhost/private?" + query, "app", "private.txt"))
                    .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("sensitive");
        }
        PersonalDatabaseConfiguration.validateConnectionSettings("jdbc:postgresql://localhost/private?sslmode=require", "app", "private.txt");
        assertThatThrownBy(() -> PersonalDatabaseConfiguration.validateConnectionSettings("jdbc:postgresql://user:sensitive@localhost/private", "app", "private.txt"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("sensitive");
    }

    @Test
    void everyInMemoryRuntimeRepositoryIsRestrictedToDemoMode() {
        for (Class<?> repository : new Class<?>[] {InMemoryAgentRepository.class, InMemoryModelRepository.class,
                InMemoryRuleRepository.class, InMemoryKnowledgeRepository.class, InMemoryProviderConfigurationRepository.class,
                InMemoryUsageRepository.class, InMemoryDraftRepository.class, InMemoryWritingPresetRepository.class}) {
            var condition = repository.getAnnotation(ConditionalOnProperty.class);
            assertThat(condition).as(repository.getSimpleName()).isNotNull();
            assertThat(condition.name()).containsExactly("sai.mode");
            assertThat(condition.havingValue()).isEqualTo("demo");
        }
    }

    @Test
    void documentNamespacesCannotBecomeSqlIdentifiersFromRequestData() {
        assertThat(PersistentDocumentStore.table("conversations")).isEqualTo("sai_conversations");
        assertThat(PersistentDocumentStore.table("provider-permissions")).isEqualTo("sai_provider_permissions");
        assertThatThrownBy(() -> PersistentDocumentStore.table("rules; DROP SCHEMA public"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
