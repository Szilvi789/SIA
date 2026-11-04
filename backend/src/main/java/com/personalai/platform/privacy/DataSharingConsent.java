package com.personalai.platform.privacy;

import java.util.Set;

/** Per-request consent bound to the effective provider and explicitly authorized knowledge sources. */
public record DataSharingConsent(String providerId, Boolean taskContent, Boolean personalInstructions,
        Set<String> knowledgeSourceIds) {
    public DataSharingConsent {
        knowledgeSourceIds = knowledgeSourceIds == null ? Set.of() : Set.copyOf(knowledgeSourceIds);
    }
}
