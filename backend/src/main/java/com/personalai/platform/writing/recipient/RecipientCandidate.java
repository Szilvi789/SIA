package com.personalai.platform.writing.recipient;

import java.net.URI;
import java.time.Instant;

/** Provenance applies independently to address, name and role; mock examples are never verified contacts. */
public record RecipientCandidate(String email, String name, String role, Classification emailClassification,
        Classification nameClassification, Classification roleClassification, URI sourceUrl,
        String retrievalContext, Instant retrievedAt, boolean mock) {
    public enum Classification { VERIFIED, PREVIOUS_CORRESPONDENCE, PUBLICLY_PUBLISHED, UNVERIFIED, MOCK }
}
