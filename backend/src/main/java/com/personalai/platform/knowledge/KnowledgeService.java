package com.personalai.platform.knowledge;

import com.personalai.platform.common.InvalidConfigurationException;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {
    private final KnowledgeRepository repository;

    public KnowledgeService(KnowledgeRepository repository) {
        this.repository = repository;
    }

    /** Resolves every reference and requires an explicit association to this agent. */
    public void validateAssignments(String agentId, List<String> sourceIds) {
        KnowledgeSource.requireIdentifier(agentId, "Agent ID");
        if (sourceIds == null) {
            throw new InvalidConfigurationException("Assigned knowledge sources must be a list.");
        }
        var seen = new HashSet<String>();
        for (String sourceId : sourceIds) {
            KnowledgeSource.requireIdentifier(sourceId, "Knowledge source ID");
            if (!seen.add(sourceId)) {
                throw new InvalidConfigurationException("Knowledge sources must not be assigned more than once.");
            }
            var source = repository.findById(sourceId)
                    .orElseThrow(() -> new InvalidConfigurationException("Assigned knowledge source was not found."));
            if (!source.isAssignedToAgent(agentId)) {
                throw new InvalidConfigurationException("Knowledge source is not associated with this agent.");
            }
        }
    }
}
