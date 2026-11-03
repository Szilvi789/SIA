package com.personalai.platform.rules;

/**
 * An application-owned instruction document. Future management endpoints must
 * authorize rule authors separately from people uploading untrusted knowledge.
 * A Markdown extension validates the representation, not the author's authority.
 */
public record RuleDocument(String id, String filename, RuleScope scope, String content) {
    public static final int MAX_CONTENT_LENGTH = 65_536;

    public RuleDocument {
        if (id == null || !id.matches("[a-z0-9][a-z0-9-]{0,79}")) {
            throw new IllegalArgumentException("A rule ID must be a simple logical identifier.");
        }
        if (filename == null || !filename.matches("[a-z0-9][a-z0-9-]{0,79}\\.md")) {
            throw new IllegalArgumentException("A rule filename must be a plain Markdown filename.");
        }
        if (scope == null) {
            throw new IllegalArgumentException("A rule must declare its scope.");
        }
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Rule content must contain 1 to 65536 characters.");
        }
    }
}
