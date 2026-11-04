package com.personalai.platform.usage;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
/** TEMPORARY: usage is process-local until PostgreSQL persistence is implemented. */
@Repository
@ConditionalOnProperty(name = "sai.mode", havingValue = "demo", matchIfMissing = true)
public class InMemoryUsageRepository implements UsageRepository {
    private final ConcurrentHashMap<String, UsageRecord> records = new ConcurrentHashMap<>();
    public UsageRecord save(UsageRecord record) { records.put(record.id(), record); return record; }
    public List<UsageRecord> findAll() { return records.values().stream().sorted(Comparator.comparing(UsageRecord::timestamp).reversed().thenComparing(UsageRecord::id)).toList(); }
}
