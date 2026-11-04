package com.personalai.platform.models;

import static org.assertj.core.api.Assertions.*;

import com.personalai.platform.agents.Agent;
import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.providers.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelManagementTest {
    private final ProviderRegistry providers = new ProviderRegistry(List.of(new MockAIProvider()));
    private final ModelCatalog models = new ModelCatalog(new InMemoryModelRepository(), providers);
    private final ModelResolver resolver = new ModelResolver(models, providers);

    @Test void createsUpdatesAndDisablesConfigurationsWithoutInventingUnknownMetadata() {
        var custom = new ModelDescriptor("user-model", "User model", "openai", "User verified metadata", "exact-user-api-id", true,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        models.save(custom.id(), custom);
        assertThat(models.findById(custom.id()).apiModelId()).isEqualTo("exact-user-api-id");
        assertThat(models.findById(custom.id()).capabilities()).isNull();
        assertThat(models.findById(custom.id()).inputTokenPrice()).isNull();
        assertThat(models.findById(custom.id()).supportedParameters()).isNull();
        var disabled = new ModelDescriptor(custom.id(), "Updated", "openai", null, custom.apiModelId(), false,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        models.save(custom.id(), disabled);
        assertThat(models.findAll()).hasSize(4);
        assertThatThrownBy(() -> models.requireEnabled(custom.id())).hasMessageContaining("disabled");
    }
    @Test void pricingRequiresCurrencyUnitAndTimestampAndRetainsDecimalPrecision() {
        var incomplete = model("priced", "openai", Set.of("TEXT"), Set.of(), new BigDecimal("0.000000012345"), null, null);
        assertThatThrownBy(() -> models.save("priced", incomplete)).isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("timestamp");
        var complete = model("priced", "openai", Set.of("TEXT"), Set.of(), new BigDecimal("0.000000012345"), new BigDecimal("0.1"), Instant.parse("2026-11-04T00:00:00Z"));
        assertThat(models.save("priced", complete).inputTokenPrice()).isEqualByComparingTo("0.000000012345");
        assertThatThrownBy(() -> models.save("wrong-id", complete)).hasMessageContaining("match");
    }
    @Test void tierNamesNormalizeAndCaseCollisionsAreRejected() {
        var settings = new ApplicationModelSettings("mock-balanced", Map.of("economy", "mock-precise"));
        assertThat(models.saveSettings(settings).tierMappings()).containsEntry("ECONOMY", "mock-precise");
        assertThatThrownBy(() -> new ApplicationModelSettings(null, Map.of("ECONOMY", "mock-balanced", "economy", "mock-precise"))).hasMessageContaining("more than once");
        assertThatThrownBy(() -> models.saveSettings(new ApplicationModelSettings(null, Map.of("advanced", "missing")))).hasMessageContaining("available model");
    }
    @Test void resolvesRequestThenTaskThenAgentThenApplicationDefaults() {
        var agent = agent("mock", "mock-balanced", null);
        assertThat(resolver.resolve(agent, selection("mock-precise", null, "mock-creative", null, null), Set.of("TEXT")).model().id()).isEqualTo("mock-precise");
        assertThat(resolver.resolve(agent, selection(null, null, "mock-creative", null, null), Set.of("TEXT")).source()).isEqualTo("TASK_MODEL");
        assertThat(resolver.resolve(agent, null, Set.of("TEXT")).model().id()).isEqualTo("mock-balanced");
        models.saveSettings(new ApplicationModelSettings("mock-creative", Map.of()));
        assertThat(resolver.resolve(agent("mock", null, null), null, Set.of("TEXT")).model().id()).isEqualTo("mock-creative");
    }
    @Test void perAgentTierMappingWinsAndMissingMappingsRequireConfiguration() {
        var settings = new ModelSettings("advanced", null, false, Map.of("advanced", "mock-precise"), null, null, null, false);
        assertThat(resolver.resolve(agent("mock", "mock-balanced", settings), null, Set.of("TEXT")).model().id()).isEqualTo("mock-precise");
        assertThat(resolver.resolve(agent("mock", "mock-balanced", settings), selection(null, "BALANCED", null, null, null), Set.of("TEXT")).model().id()).isEqualTo("mock-balanced");
        models.saveSettings(new ApplicationModelSettings("mock-balanced", Map.of()));
        assertThatThrownBy(() -> resolver.resolve(agent("mock", "mock-balanced", null), selection(null, "economy", null, null, null), Set.of("TEXT"))).hasMessageContaining("Configure a model");
    }
    @Test void rejectsInvalidIdentifiersUnsupportedParametersAndWorkflowCapabilities() {
        assertThatThrownBy(() -> resolver.resolve(agent("mock", "missing", null), null, Set.of("TEXT"))).hasMessageContaining("available model");
        assertThatThrownBy(() -> resolver.resolve(agent("mock", "mock-balanced", null), selection(null, null, null, null,
                new GenerationParameters(null, null, 0.9, null, null, null)), Set.of("TEXT"))).hasMessageContaining("topP");
        var plain = model("plain", "mock", Set.of("TEXT"), Set.of(), null, null, null);
        models.save("plain", plain);
        assertThat(resolver.resolve(agent("mock", "plain", null), null, Set.of("TEXT")).parameters().temperature()).isNull();
        assertThatThrownBy(() -> resolver.resolve(agent("mock", "plain", null), null, Set.of("TEXT", "STRUCTURED_OUTPUT"))).hasMessageContaining("capabilities");
        assertThatThrownBy(() -> resolver.resolve(agent("mock", "plain", null), selection(null, null, null, null,
                new GenerationParameters(null, 0.5, null, null, null, null)), Set.of("TEXT"))).hasMessageContaining("temperature");
    }
    @Test void savesDisconnectedProviderDefaultsButRequiresExplicitFallbackPermission() {
        models.save("remote", model("remote", "openai", Set.of("TEXT"), Set.of(), null, null, null));
        var plain = agent("openai", "remote", null);
        resolver.validateAgentSettings(plain);
        assertThatThrownBy(() -> resolver.resolve(plain, null, Set.of("TEXT"))).hasMessageContaining("disabled");
        var configured = agent("openai", "remote", new ModelSettings(null, "mock-precise", true, Map.of(), null, null, null, false));
        assertThat(resolver.resolve(configured, null, Set.of("TEXT")).fallbackUsed()).isTrue();
        assertThatThrownBy(() -> resolver.resolve(configured, new ModelSelection(null, null, null, null, null, false, null), Set.of("TEXT"))).hasMessageContaining("disabled");
        assertThat(resolver.selectionContext(plain, selection(null, null, "remote", null, null)).providerId()).isEqualTo("openai");
    }
    @Test void cannotSaveDormantInvalidModelBehindDefaultTierOrEscalateTools() {
        var settings = new ModelSettings("balanced", null, false, Map.of(), null, null, null, false);
        assertThatThrownBy(() -> resolver.validateAgentSettings(agent("mock", "missing", settings))).hasMessageContaining("available model");
        assertThatThrownBy(() -> resolver.resolve(agent("mock", "mock-balanced", null), selection(null, null, null, null,
                new GenerationParameters(null, null, null, null, null, List.of("terminal"))), Set.of("TEXT"))).hasMessageContaining("cannot exceed");
    }
    @Test void providerEnablementDoesNotPretendAnAdapterOrCredentialExists() {
        var enabled = providers.setEnabled("openai", true);
        assertThat(enabled.adapterAvailable()).isFalse(); assertThat(enabled.configured()).isFalse();
        assertThatThrownBy(() -> providers.require("openai")).hasMessageContaining("no installed execution adapter");
        providers.setEnabled("mock", false);
        assertThatThrownBy(() -> providers.require("mock")).hasMessageContaining("disabled");
    }
    static Agent agent(String providerId, String modelId, ModelSettings settings) {
        return new Agent("coding-agent", "Code", "Code", "Help", providerId, modelId, 0.3, 512, List.of(), List.of(), List.of(), settings);
    }
    static ModelSelection selection(String model, String tier, String override, String overrideTier, GenerationParameters parameters) {
        return new ModelSelection(model, tier, override, overrideTier, parameters, null, "conversation-one");
    }
    static ModelDescriptor model(String id, String provider, Set<String> capabilities, Set<String> parameters,
            BigDecimal inputPrice, BigDecimal outputPrice, Instant updatedAt) {
        return new ModelDescriptor(id, id, provider, "Configured by user", id + "-api", true, capabilities, null, null,
                inputPrice, outputPrice, inputPrice == null && outputPrice == null ? null : "EUR",
                inputPrice == null && outputPrice == null ? null : 1_000_000L, updatedAt, parameters, Set.of(), false,
                capabilities.contains("STRUCTURED_OUTPUT"), false, false);
    }
}
