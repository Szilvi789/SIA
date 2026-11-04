package com.personalai.platform.usage;

import static org.assertj.core.api.Assertions.*;
import com.personalai.platform.models.ModelDescriptor;
import com.personalai.platform.providers.ProviderUsage;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UsageServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
    private final UsageService usage = new UsageService(clock);
    private ModelDescriptor priced(String currency) {
        return new ModelDescriptor("real-config", "Configured model", "local", "User rates", "user-api", true, Set.of("TEXT"),
                null, null, new BigDecimal("2"), new BigDecimal("8"), currency, 1_000_000L,
                clock.instant(), Set.of(), Set.of(), false, false, false, false);
    }
    @Test void estimatesOnlyProviderReportedCountsWithCompleteSnapshotPricing() {
        var record = usage.record("coding-agent", "CHAT", "conversation", priced("EUR"), false, new ProviderUsage(1000L, 100L, 0L), "SUCCESS", null);
        assertThat(record.estimatedCost()).isEqualByComparingTo("0.0028");
        assertThat(record.inputTokenPrice()).isEqualByComparingTo("2");
        assertThat(record.timestamp()).isEqualTo(clock.instant());
        assertThat(usage.estimate(priced("EUR"), new ProviderUsage(1000L, 100L, 10L), false)).isNull();
        assertThat(usage.estimate(priced("EUR"), null, false)).isNull();
        assertThat(usage.estimate(priced("EUR"), new ProviderUsage(1000L, 100L, null), true)).isNull();
    }
    @Test void summarySeparatesCurrenciesAndNeverTurnsUnknownTokensIntoZero() {
        usage.record("coding-agent", "CHAT", "one", priced("EUR"), false, new ProviderUsage(1000L, 100L, null), "SUCCESS", null);
        usage.record("coding-agent", "CHAT", "two", priced("USD"), false, new ProviderUsage(2000L, 200L, null), "SUCCESS", null);
        usage.record("coding-agent", "CHAT", "three", priced("EUR"), false, null, "FAILED", "Provider unavailable");
        var summaries = usage.summary();
        assertThat(summaries).hasSize(2);
        var euro = summaries.stream().filter(s -> "EUR".equals(s.currency())).findFirst().orElseThrow();
        assertThat(euro.requests()).isEqualTo(2); assertThat(euro.failedRequests()).isEqualTo(1);
        assertThat(euro.inputTokens()).isNull(); assertThat(euro.estimatedCost()).isNull();
        assertThat(summaries.stream().filter(s -> "USD".equals(s.currency())).findFirst().orElseThrow().inputTokens()).isEqualTo(2000L);
    }
    @Test void mockRecordsAreClearlyClassifiedAndContainNoInventedUsage() {
        var record = usage.record("writing-agent", "GENERAL", null, new ModelDescriptor("mock-balanced", "Mock", "mock", "Demo"), true, null, "SUCCESS", null);
        assertThat(record.mock()).isTrue(); assertThat(record.inputTokens()).isNull(); assertThat(record.outputTokens()).isNull(); assertThat(record.estimatedCost()).isNull();
        assertThat(usage.summary().getFirst().mockRequests()).isEqualTo(1);
    }
}
