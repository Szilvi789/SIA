package com.personalai.platform.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InMemoryRuleRepositoryTest {
    @Test
    void readsTheAllowlistedMarkdownFilesIncludingProtectedWritingRulesFromTheClasspath() throws Exception {
        var repository = new InMemoryRuleRepository();

        assertThat(repository.findAll()).hasSize(15);
        assertThat(repository.findAll()).filteredOn(rule -> rule.scope() == RuleScope.GLOBAL)
                .extracting(RuleDocument::id).containsExactly("global-instructions");
        for (RuleDocument rule : repository.findAll()) {
            try (var input = getClass().getResourceAsStream("/rules/" + rule.filename())) {
                assertThat(input).isNotNull();
                assertThat(rule.content()).isEqualTo(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(repository.findById("coding-rules").orElseThrow().content()).contains("# Coding rules");
    }

    @Test
    void cannotLoadFilesThroughCallerSuppliedIdsOrModifyTheCatalog() {
        var repository = new InMemoryRuleRepository();

        assertThat(repository.findById("../application.properties")).isEmpty();
        assertThat(repository.findById("coding-rules.md")).isEmpty();
        assertThat(repository.findById(null)).isEmpty();
        assertThatThrownBy(() -> repository.findAll().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void editsOnlyUserOwnedMarkdownWithoutOverwritingPackagedPolicy() {
        var repository = new InMemoryRuleRepository();
        var original = repository.findById("writing-core").orElseThrow();
        var userRule = new RuleDocument("writing-user-my-style", "writing-user-my-style.md", RuleScope.AGENT, "# Be concise");
        assertThat(repository.saveUserOwned(userRule)).isEqualTo(userRule);
        assertThat(repository.findById(userRule.id())).contains(userRule);
        assertThatThrownBy(() -> repository.saveUserOwned(new RuleDocument("writing-core", "writing-core.md", RuleScope.AGENT, "Override")))
                .isInstanceOf(com.personalai.platform.common.InvalidConfigurationException.class);
        assertThatThrownBy(() -> repository.saveUserOwned(new RuleDocument("writing-user-global", "writing-user-global.md", RuleScope.GLOBAL, "Override")))
                .isInstanceOf(com.personalai.platform.common.InvalidConfigurationException.class);
        assertThat(repository.findById("writing-core")).contains(original);
    }
}
