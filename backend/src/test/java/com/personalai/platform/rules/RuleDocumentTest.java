package com.personalai.platform.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuleDocumentTest {
    @Test
    void preservesMarkdownAsAuthored() {
        String markdown = "# Rules\n\n- Keep **Markdown** intact.\n";

        var rule = new RuleDocument("coding-rules", "coding-rules.md", RuleScope.AGENT, markdown);

        assertThat(rule.content()).isEqualTo(markdown);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../rules.md", "C:\\rules.md", "/rules.md", "rules.txt", "sub/rules.md", ""})
    void rejectsPathsAndNonMarkdownFilenames(String filename) {
        assertThatThrownBy(() -> new RuleDocument("rules", filename, RuleScope.AGENT, "Rules"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnboundedContentMissingScopeAndInvalidIds() {
        assertThatThrownBy(() -> new RuleDocument("rules", "rules.md", RuleScope.AGENT,
                "a".repeat(RuleDocument.MAX_CONTENT_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleDocument("rules", "rules.md", RuleScope.AGENT, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleDocument("rules", "rules.md", null, "Rules"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleDocument("../rules", "rules.md", RuleScope.AGENT, "Rules"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
