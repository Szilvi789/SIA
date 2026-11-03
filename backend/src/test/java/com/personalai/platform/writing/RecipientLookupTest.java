package com.personalai.platform.writing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.writing.infrastructure.MockRecipientLookup;
import com.personalai.platform.writing.recipient.RecipientCandidate.Classification;
import org.junit.jupiter.api.Test;

class RecipientLookupTest {
    @Test
    void requiresExplicitActionAndNeverLabelsDemoIdentitiesAsVerified() {
        var lookup = new MockRecipientLookup();
        assertThatThrownBy(() -> lookup.suggest("Recruitment", "mock-personal", false))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("explicit user action");
        assertThat(lookup.suggest("", "mock-personal", true)).isEmpty();
        var candidates = lookup.suggest("A real company", "mock-personal", true);
        assertThat(candidates).hasSize(1);
        var candidate = candidates.getFirst();
        assertThat(candidate.email()).endsWith("@example.invalid");
        assertThat(candidate.emailClassification()).isEqualTo(Classification.MOCK);
        assertThat(candidate.nameClassification()).isEqualTo(Classification.MOCK);
        assertThat(candidate.roleClassification()).isEqualTo(Classification.MOCK);
        assertThat(candidate.mock()).isTrue();
        assertThat(candidate.sourceUrl()).isNull();
        assertThat(candidate.retrievalContext()).contains("No authorized contacts, correspondence or website was searched");
        assertThat(lookup.suggest("Unrelated company", "mock-personal", true)).isEqualTo(candidates);
    }
}
