package com.personalai.platform.knowledge;

import com.personalai.platform.common.InvalidConfigurationException;
import java.util.HashSet;
import java.util.List;

/** A retrieval request; source IDs must additionally pass assignment authorization before retrieval. */
public record KnowledgeQuery(String agentId, List<String> sourceIds, String query) {
    public static final int MAX_QUERY_CHARACTERS = 12_000;
    public static final int MAX_SOURCE_REFERENCES = 50;

    public KnowledgeQuery {
        KnowledgeSource.requireIdentifier(agentId, "Agent ID");
        if (sourceIds == null || sourceIds.size() > MAX_SOURCE_REFERENCES
                || sourceIds.stream().anyMatch(item -> item == null)
                || new HashSet<>(sourceIds).size() != sourceIds.size()) {
            throw new InvalidConfigurationException("Retrieval source IDs must be unique and non-null, with at most 50 references.");
        }
        sourceIds.forEach(id -> KnowledgeSource.requireIdentifier(id, "Knowledge source ID"));
        sourceIds = List.copyOf(sourceIds);
        if (query == null || query.isBlank() || query.length() > MAX_QUERY_CHARACTERS) {
            throw new InvalidConfigurationException("A retrieval query must contain 1 to 12000 characters.");
        }
    }
}
