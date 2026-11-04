package com.personalai.platform.providers;

import static org.assertj.core.api.Assertions.*;

import com.personalai.platform.agents.Agent;
import com.personalai.platform.models.*;
import com.personalai.platform.rules.ResolvedInstructions;
import com.personalai.platform.usage.UsageService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ProviderExecutionServiceTest {
    private final Adapter primary = new Adapter("openai");
    private final Adapter fallback = new Adapter("local");
    private final ProviderRegistry providers = new ProviderRegistry(List.of(primary, fallback, new MockAIProvider()));
    private final ModelCatalog models = new ModelCatalog(new InMemoryModelRepository(), providers);
    private final UsageService usage = new UsageService(Clock.systemUTC());
    private final ProviderExecutionService execution = new ProviderExecutionService(new ModelResolver(models, providers), providers, usage);

    ProviderExecutionServiceTest() {
        providers.setEnabled("openai", true);
        providers.setEnabled("local", true);
        models.save("primary", priced("primary", "openai", "2", "4"));
        models.save("fallback", priced("fallback", "local", "1", "2"));
    }

    @Test void safePreDispatchFailureUsesOnlyPermittedCheaperFallbackAndRecordsBothAttempts() {
        primary.response = () -> { throw new ProviderUnavailableException(); };
        var result = execute(agent(true, null, true), ModelSelection.defaults());
        assertThat(result.metadata().modelId()).isEqualTo("fallback");
        assertThat(result.metadata().providerId()).isEqualTo("local");
        assertThat(result.metadata().fallbackUsed()).isTrue();
        assertThat(primary.calls).isEqualTo(1);
        assertThat(fallback.calls).isEqualTo(1);
        assertThat(usage.findAll()).extracting(record -> record.status()).containsExactlyInAnyOrder("SUCCESS", "FAILED");
        assertThat(usage.findAll().stream().filter(record -> "SUCCESS".equals(record.status())).findFirst().orElseThrow().estimatedCost()).isEqualByComparingTo("0.00003");
    }

    @Test void requestCanForbidSavedFallbackAndCannotGrantUnsavedPermission() {
        primary.response = () -> { throw new ProviderUnavailableException(); };
        assertThatThrownBy(() -> execute(agent(true, null, true), new ModelSelection(null, null, null, null, null, false, null)))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThatThrownBy(() -> execute(agent(false, null, true), new ModelSelection(null, null, null, null, null, true, null)))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThat(fallback.calls).isZero();
    }

    @Test void ambiguousProviderFailureIsSanitizedAndNeverRetried() {
        primary.response = () -> { throw new IllegalStateException("provider-internal-marker"); };
        assertThatThrownBy(() -> execute(agent(true, null, true), null))
                .hasMessageContaining("could not complete generation")
                .hasMessageNotContaining("provider-internal-marker");
        assertThat(fallback.calls).isZero();
        assertThat(usage.findAll()).hasSize(1);
        assertThat(usage.findAll().getFirst().error()).doesNotContain("provider-internal-marker");
    }

    @Test void fallbackRejectsHigherOrUnknownPricesEvenWithPermission() {
        primary.response = () -> { throw new ProviderUnavailableException(); };
        models.save("fallback", priced("fallback", "local", "5", "5"));
        assertThatThrownBy(() -> execute(agent(true, null, true), null)).hasMessageContaining("unknown or higher");
        models.save("fallback", new ModelDescriptor("fallback", "Unpriced fallback", "local", "User configuration",
                "fallback-api", true, Set.of("TEXT"), null, null, null, null, null, null, null,
                Set.of(), Set.of(), false, false, false, false));
        assertThatThrownBy(() -> execute(agent(true, null, true), null)).hasMessageContaining("unknown or higher");
        assertThat(fallback.calls).isZero();
    }

    @Test void costGuardrailBlocksUnknownPreflightCostUnlessExplicitlyAllowed() {
        assertThatThrownBy(() -> execute(agent(false, new BigDecimal("0.01"), false), null)).hasMessageContaining("pre-request cost");
        assertThat(primary.calls).isZero();
        assertThat(execute(agent(false, new BigDecimal("0.01"), true), null).metadata().modelId()).isEqualTo("primary");
        assertThat(primary.calls).isEqualTo(1);
    }

    @Test void rejectedOverrideIsAttributedToItsProviderAndNeverInventsUsage() {
        providers.setEnabled("openai", false);
        var mockAgent = new Agent("coding-agent", "Code", "Code", "Help", "mock", "mock-balanced", 0.3, 512, List.of());
        var selection = new ModelSelection(null, null, "primary", null, null, false, "conversation");
        assertThatThrownBy(() -> execute(mockAgent, selection)).hasMessageContaining("disabled");
        var rejected = usage.findAll().getFirst();
        assertThat(rejected.providerId()).isEqualTo("openai");
        assertThat(rejected.modelId()).isEqualTo("primary");
        assertThat(rejected.mock()).isFalse();
        assertThat(rejected.inputTokens()).isNull();
        assertThat(rejected.estimatedCost()).isNull();
    }

    private ExecutionResult execute(Agent agent, ModelSelection selection) {
        return execution.execute(agent, selection, Set.of("TEXT"), "CHAT", resolved -> new ProviderRequest(agent,
                resolved.model(), "Task", new ResolvedInstructions("Trusted instructions", List.of()), List.of(), List.of()));
    }
    private Agent agent(boolean allowFallback, BigDecimal limit, boolean allowUnknownCost) {
        return new Agent("coding-agent", "Code", "Code", "Help", "openai", "primary", 0.3, 512,
                List.of(), List.of(), List.of(), new ModelSettings(null, "fallback", allowFallback, Map.of(), null, limit, "EUR", allowUnknownCost));
    }
    private ModelDescriptor priced(String id, String provider, String input, String output) {
        return new ModelDescriptor(id, id, provider, "User configuration", id + "-api", true, Set.of("TEXT"), null, null,
                new BigDecimal(input), new BigDecimal(output), "EUR", 1_000_000L, Instant.parse("2026-11-04T00:00:00Z"),
                Set.of(), Set.of(), false, false, false, false);
    }
    private static class Adapter implements AIProvider {
        private final String id;
        int calls;
        Supplier<String> response = () -> "Test provider response";
        Adapter(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public boolean configured() { return true; }
        @Override public com.personalai.platform.privacy.DataLocality dataLocality() {
            return com.personalai.platform.privacy.DataLocality.LOCAL;
        }
        @Override public String generate(ProviderRequest request) { calls++; return response.get(); }
        @Override public ProviderResult generateResult(ProviderRequest request) {
            return new ProviderResult(generate(request), new ProviderUsage(10L, 10L, 0L));
        }
    }
}
