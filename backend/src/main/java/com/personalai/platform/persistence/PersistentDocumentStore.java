package com.personalai.platform.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Typed JSONB aggregates, with allowlisted physical tables and parameterized values. */
@Repository
@ConditionalOnProperty(name = "sai.mode", havingValue = "personal")
public class PersistentDocumentStore {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, String> TABLES = Map.ofEntries(
            Map.entry("agents", "sai_agents"), Map.entry("models", "sai_models"),
            Map.entry("model-settings", "sai_model_settings"), Map.entry("rules", "sai_user_rules"),
            Map.entry("provider-configurations", "sai_provider_configurations"),
            Map.entry("knowledge", "sai_knowledge_sources"), Map.entry("drafts", "sai_writing_drafts"),
            Map.entry("writing-presets", "sai_writing_presets"), Map.entry("usage", "sai_usage"),
            Map.entry("conversations", "sai_conversations"), Map.entry("provider-permissions", "sai_provider_permissions"));
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PersistentDocumentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new JdbcTransactionManager(java.util.Objects.requireNonNull(jdbc.getDataSource())));
    }

    /** Serializes changes to one aggregate across local application instances, even when the row is new. */
    public <T> T mutate(String namespace, String id, Class<T> type, Function<Optional<T>, T> update) {
        table(namespace);
        return transactions.execute(status -> {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", (rs, row) -> 1, namespace + ":" + id);
            T value = java.util.Objects.requireNonNull(update.apply(find(namespace, id, type)));
            return save(namespace, id, value);
        });
    }

    public <T> Optional<T> find(String namespace, String id, Class<T> type) {
        if (id == null) return Optional.empty();
        return jdbc.query("SELECT payload::text FROM " + table(namespace) + " WHERE id = ?",
                (rs, row) -> decode(rs.getString(1), type), id).stream().findFirst();
    }

    public <T> List<T> all(String namespace, Class<T> type) {
        return jdbc.query("SELECT payload::text FROM " + table(namespace) + " ORDER BY id",
                (rs, row) -> decode(rs.getString(1), type));
    }

    public <T> T save(String namespace, String id, T value) {
        jdbc.update("INSERT INTO " + table(namespace) + " (id, payload, origin) VALUES (?, ?::jsonb, 'user') "
                + "ON CONFLICT (id) DO UPDATE SET payload = EXCLUDED.payload, origin = 'user', updated_at = CURRENT_TIMESTAMP",
                id, encode(value));
        return value;
    }

    public <T> boolean insertIfAbsent(String namespace, String id, T value) {
        return jdbc.update("INSERT INTO " + table(namespace) + " (id, payload, origin) VALUES (?, ?::jsonb, 'synthetic') "
                + "ON CONFLICT (id) DO NOTHING", id, encode(value)) == 1;
    }

    public <T> boolean replace(String namespace, String id, T value) {
        return jdbc.update("UPDATE " + table(namespace)
                + " SET payload = ?::jsonb, origin = 'user', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                encode(value), id) == 1;
    }

    /** The comparison happens inside PostgreSQL, including concurrent requests from other app processes. */
    public <T> boolean saveVersioned(String namespace, String id, T value, long nextRevision, Long expectedRevision) {
        if (expectedRevision == null) {
            return jdbc.update("INSERT INTO " + table(namespace)
                    + " (id, payload, revision) VALUES (?, ?::jsonb, ?) ON CONFLICT (id) DO NOTHING",
                    id, encode(value), nextRevision) == 1;
        }
        return jdbc.update("UPDATE " + table(namespace)
                + " SET payload = ?::jsonb, revision = ?, origin = 'user', updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND revision = ?", encode(value), nextRevision, id, expectedRevision) == 1;
    }

    static String table(String namespace) {
        var table = TABLES.get(namespace);
        if (table == null) throw new IllegalArgumentException("Unknown private document namespace.");
        return table;
    }

    private static String encode(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (RuntimeException exception) {
            throw new IllegalStateException("Cannot serialize a private application record.");
        }
    }

    private static <T> T decode(String payload, Class<T> type) {
        try { return JSON.readValue(payload, type); }
        catch (RuntimeException exception) {
            // Jackson diagnostics may otherwise contain the private record being decoded.
            throw new IllegalStateException("Cannot read a private application record; check database and application versions.");
        }
    }
}
