package com.personalai.platform.usage;

import com.personalai.platform.models.ModelDescriptor;
import com.personalai.platform.providers.ProviderUsage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UsageService {
    private final UsageRepository repository;
    private final Clock clock;
    public UsageService(Clock clock) { this(new InMemoryUsageRepository(), clock); }
    @Autowired public UsageService(UsageRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }
    public List<UsageRecord> findAll() { return repository.findAll(); }
    public UsageRecord record(String agentId, String workflow, String conversationId, ModelDescriptor model,
            boolean mock, ProviderUsage usage, String status, String error) {
        return repository.save(new UsageRecord(UUID.randomUUID().toString(), model.providerId(), model.id(), agentId,
                workflow, conversationId, Instant.now(clock), usage == null ? null : usage.inputTokens(),
                usage == null ? null : usage.outputTokens(), usage == null ? null : usage.cachedTokens(),
                estimate(model, usage, mock), model.currency(), model.pricingUpdatedAt(), status, mock, error,
                model.inputTokenPrice(), model.outputTokenPrice(), model.pricingUnit()));
    }
    public UsageRecord recordRejected(String agentId, String workflow, String conversationId,
            String providerId, String modelId, boolean mock, String error) {
        return repository.save(new UsageRecord(UUID.randomUUID().toString(), providerId, modelId, agentId, workflow,
                conversationId, Instant.now(clock), null, null, null, null, null, null, "FAILED", mock, error, null, null, null));
    }
    /** No prompt-length token guesses. Cached input has unknown price unless the adapter reports a cost later. */
    public BigDecimal estimate(ModelDescriptor model, ProviderUsage usage, boolean mock) {
        if (mock || usage == null || usage.inputTokens() == null || usage.outputTokens() == null
                || (usage.cachedTokens() != null && usage.cachedTokens() > 0)
                || model.inputTokenPrice() == null || model.outputTokenPrice() == null
                || model.pricingUnit() == null || model.pricingUnit() <= 0 || model.currency() == null
                || model.pricingUpdatedAt() == null) return null;
        return model.inputTokenPrice().multiply(BigDecimal.valueOf(usage.inputTokens()))
                .add(model.outputTokenPrice().multiply(BigDecimal.valueOf(usage.outputTokens())))
                .divide(BigDecimal.valueOf(model.pricingUnit()), 12, RoundingMode.HALF_UP);
    }
    public List<UsageSummary> summary() {
        Map<Group, List<UsageRecord>> groups = new LinkedHashMap<>();
        for (var record : repository.findAll()) {
            var key = new Group(record.agentId(), record.modelId(), record.providerId(), record.currency());
            groups.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(record);
        }
        return groups.entrySet().stream().map(entry -> {
            var key = entry.getKey(); var records = entry.getValue();
            return new UsageSummary(key.agentId(), key.modelId(), key.providerId(), records.size(),
                    records.stream().filter(r -> "SUCCESS".equals(r.status())).count(),
                    records.stream().filter(r -> "FAILED".equals(r.status())).count(),
                    records.stream().filter(UsageRecord::mock).count(), sumTokens(records, true), sumTokens(records, false),
                    records.stream().anyMatch(r -> r.estimatedCost() == null) ? null : records.stream().map(UsageRecord::estimatedCost).reduce(BigDecimal.ZERO, BigDecimal::add), key.currency());
        }).toList();
    }
    private static Long sumTokens(List<UsageRecord> records, boolean input) {
        if (records.stream().anyMatch(record -> (input ? record.inputTokens() : record.outputTokens()) == null)) return null;
        return records.stream().mapToLong(record -> input ? record.inputTokens() : record.outputTokens()).sum();
    }
    private record Group(String agentId, String modelId, String providerId, String currency) { }
}
