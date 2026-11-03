package com.personalai.platform.knowledge;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.agents.AgentIds;
import java.util.HashSet;
import java.util.List;

/** Provider-independent metadata. Storage keys are opaque identifiers, never file-system paths. */
public record KnowledgeSource(
        String id,
        String fileName,
        KnowledgeFormat format,
        String storageKey,
        long byteSize,
        List<KnowledgeAssociation> associations) {

    public KnowledgeSource {
        requireIdentifier(id, "Knowledge source ID");
        requireIdentifier(storageKey, "Storage key");
        if (format == null) {
            throw new InvalidConfigurationException("A knowledge source requires a format.");
        }
        format.validateFileName(fileName);
        if (byteSize < 0) {
            throw new InvalidConfigurationException("Document size must not be negative.");
        }
        if (associations == null || associations.stream().anyMatch(item -> item == null)
                || new HashSet<>(associations).size() != associations.size()) {
            throw new InvalidConfigurationException("Knowledge associations must be unique and non-null.");
        }
        associations = List.copyOf(associations);
    }

    public boolean isAssignedToAgent(String agentId) {
        return associations.stream().anyMatch(association -> association.scope() == KnowledgeAssociation.Scope.AGENT
                && AgentIds.canonical(association.targetId()).equals(AgentIds.canonical(agentId)));
    }

    static void requireIdentifier(String value, String label) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new InvalidConfigurationException(label + " must be an opaque identifier.");
        }
    }
}
