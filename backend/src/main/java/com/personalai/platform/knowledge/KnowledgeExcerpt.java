package com.personalai.platform.knowledge;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.personalai.platform.common.InvalidConfigurationException;

/** External text is reference data. Never merge it into global, system, or agent instructions. */
public record KnowledgeExcerpt(String sourceId, String text) {
    public static final int MAX_TEXT_CHARACTERS = 16_000;

    public enum Classification { UNTRUSTED }

    public KnowledgeExcerpt {
        KnowledgeSource.requireIdentifier(sourceId, "Knowledge source ID");
        if (text == null || text.isBlank() || text.length() > MAX_TEXT_CHARACTERS) {
            throw new InvalidConfigurationException("Knowledge excerpt text must contain 1 to 16000 characters.");
        }
    }

    /** Fixed by the application, with no constructor or setter accepting a trusted classification. */
    @JsonProperty("classification")
    public Classification classification() {
        return Classification.UNTRUSTED;
    }
}
