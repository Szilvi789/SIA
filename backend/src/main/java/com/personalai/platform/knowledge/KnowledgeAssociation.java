package com.personalai.platform.knowledge;

import com.personalai.platform.common.InvalidConfigurationException;

/** A project association does not imply membership or grant any agent access. */
public record KnowledgeAssociation(Scope scope, String targetId) {
    public enum Scope { AGENT, PROJECT }

    public KnowledgeAssociation {
        if (scope == null) {
            throw new InvalidConfigurationException("A knowledge association requires a scope.");
        }
        KnowledgeSource.requireIdentifier(targetId, "Association target ID");
    }
}
