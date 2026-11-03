package com.personalai.platform.writing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalai.platform.agents.Agent;
import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.providers.MockAIProvider;
import com.personalai.platform.providers.ProviderRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class WritingPresetAndRulesTest {
    private final WritingFixture fixture = new WritingFixture();

    @Test
    void deterministicCompositionOrdersPolicyGlobalCoreAgentWorkflowStyleLanguageAndCustomPreferences() {
        var agent = new Agent("writing-agent", "Writing", "Writing", "UNIQUE AGENT INSTRUCTION", "mock", "mock-balanced",
                0.5, 2048, List.of(), List.of("email-style"), List.of());
        var preferences = new WritingPreferences("friendly-email", null, WritingPreferences.OutputLanguage.DE,
                null, null, null, null, "UNIQUE AUDIENCE", "UNIQUE PURPOSE", "UNIQUE CUSTOM PREFERENCE", List.of());
        var request = WritingFixture.request(WritingWorkflow.EMAIL, preferences);
        var result = fixture.composer.compose(agent, request);
        var prompt = result.systemPrompt();
        assertBefore(prompt, "# Application security policy", "# Shared instructions");
        assertBefore(prompt, "# Shared instructions", "# Writing safety");
        assertBefore(prompt, "# Writing safety", "UNIQUE AGENT INSTRUCTION");
        assertBefore(prompt, "UNIQUE AGENT INSTRUCTION", "## Workflow: writing-email");
        assertBefore(prompt, "## Workflow: writing-email", "## Preset style: writing-style-friendly");
        assertBefore(prompt, "## Preset style: writing-style-friendly", "## Output language: writing-language-de");
        assertBefore(prompt, "## Output language: writing-language-de", "UNIQUE CUSTOM PREFERENCE");
        assertThat(result.appliedRuleIds()).containsExactly("global-instructions", "writing-core", "email-style", "writing-email",
                "writing-style-friendly", "writing-user-friendly-email", "writing-style-professional", "writing-language-de");
        assertThat(fixture.composer.compose(agent, request)).isEqualTo(result);
    }
    @Test
    void editableMarkdownUsesExistingRuleStorageWhileCoreRemainsImmutable() {
        var created = fixture.presets.create(preset("plain-email", WritingWorkflow.EMAIL, true, List.of("writing-email"),
                "# My plain style\nDo not include a greeting or closing."));
        assertThat(fixture.ruleRepository.findById("writing-user-plain-email").orElseThrow().content())
                .isEqualTo(created.markdown());
        fixture.presets.update(created.id(), preset(created.id(), WritingWorkflow.EMAIL, true, List.of("writing-email"), "# Updated style\nUse short paragraphs."));
        assertThat(fixture.ruleRepository.findById("writing-user-plain-email").orElseThrow().content()).contains("Updated style");
        assertThat(fixture.ruleRepository.findById("writing-core").orElseThrow().content()).contains("Never invent qualifications");
    }
    @Test
    void rejectsDuplicateUnknownGlobalAndCrossPresetRuleReferences() {
        assertThatThrownBy(() -> fixture.presets.create(preset("professional", WritingWorkflow.GENERAL, true, List.of(), "# Custom")))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("already exists");
        for (String id : List.of("unknown", "global-instructions", "writing-user-professional")) {
            assertThatThrownBy(() -> fixture.presets.create(preset("invalid-" + id, WritingWorkflow.GENERAL, true, List.of(id), "# Custom")))
                    .isInstanceOf(InvalidConfigurationException.class);
        }
        assertThatThrownBy(() -> fixture.presets.create(preset("duplicate-rules", WritingWorkflow.GENERAL, true,
                List.of("email-style", "email-style"), "# Custom"))).isInstanceOf(InvalidConfigurationException.class);
    }
    @Test
    void disabledAndWrongWorkflowPresetsCannotBeApplied() {
        fixture.presets.create(preset("disabled-style", WritingWorkflow.EMAIL, false, List.of(), "# Disabled"));
        assertThatThrownBy(() -> fixture.presets.requireApplicable("disabled-style", WritingWorkflow.EMAIL))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("disabled");
        assertThatThrownBy(() -> fixture.presets.requireApplicable("friendly-email", WritingWorkflow.TRANSLATION))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("another workflow");
        assertThat(fixture.presets.requireApplicable("professional", WritingWorkflow.TRANSLATION).id()).isEqualTo("professional");
    }
    @Test
    void preferencesAndMarkdownAreBoundedAndPresetsCannotChangeIds() {
        assertThatThrownBy(() -> preset("../escape", WritingWorkflow.GENERAL, true, List.of(), "# Test"))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> preset("too-long", WritingWorkflow.GENERAL, true, List.of(), "x".repeat(16001)))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> new WritingPreferences(null, null, null, null, null, null, null, null, null, "x".repeat(8001), null))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> fixture.presets.update("professional", preset("new-id", WritingWorkflow.GENERAL, true, List.of(), "# Valid")))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("cannot change");
    }
    private WritingPreset preset(String id, WritingWorkflow workflow, boolean enabled, List<String> rules, String markdown) {
        return new WritingPreset(id, "My writing preset", "Test", workflow, enabled, rules, markdown, null);
    }
    private static void assertBefore(String prompt, String first, String second) {
        assertThat(prompt.indexOf(first)).isNotNegative().isLessThan(prompt.indexOf(second));
    }
}
