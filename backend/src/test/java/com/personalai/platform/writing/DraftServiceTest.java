package com.personalai.platform.writing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.writing.infrastructure.InMemoryDraftRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DraftServiceTest {
    private final InMemoryDraftRepository repository = new InMemoryDraftRepository();
    private final DraftService drafts = new DraftService(repository, Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void storesLatestManualContentSubjectAndOriginalTaskConfiguration() {
        var initial = drafts.create(snapshot("Original output", "Original subject", null));
        var updated = drafts.update(initial.id(), snapshot("User edited\n  exact whitespace", "User subject", initial.revision()));
        assertThat(updated.content()).isEqualTo("User edited\n  exact whitespace");
        assertThat(updated.subject()).isEqualTo("User subject");
        assertThat(updated.input()).isEqualTo("Original task");
        assertThat(updated.email().to()).isEqualTo("recipient@example.invalid");
        assertThat(updated.preferences().outputLanguage()).isEqualTo(WritingPreferences.OutputLanguage.DE);
        assertThat(updated.modelId()).isEqualTo("mock-precise");
        assertThat(updated.revision()).isEqualTo(2L);
        assertThat(updated.status()).isEqualTo(WritingDraft.Status.DRAFT);
        assertThat(drafts.findById(initial.id())).isEqualTo(updated);
    }
    @Test
    void rejectsMissingAndStaleRevisionWithoutLosingAnotherEditorsChanges() {
        var initial = drafts.create(snapshot("First", "Subject", null));
        drafts.update(initial.id(), snapshot("Second editor", "Subject", initial.revision()));
        assertThatThrownBy(() -> drafts.update(initial.id(), snapshot("Stale change", "Subject", initial.revision())))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("Reload");
        assertThatThrownBy(() -> drafts.update(initial.id(), snapshot("Missing revision", "Subject", null)))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThat(drafts.findById(initial.id()).content()).isEqualTo("Second editor");
    }
    @Test
    void generatingNewOutputNeverMutatesASavedDraft() {
        var saved = drafts.create(snapshot("My reviewed draft", "My subject", null));
        new WritingFixture().service().generate(WritingFixture.request(WritingWorkflow.GENERAL, null));
        assertThat(drafts.findById(saved.id())).isEqualTo(saved);
        assertThat(new InMemoryDraftRepository().findAll()).isEmpty();
    }
    private WritingDraft snapshot(String content, String subject, Long revision) {
        var preferences = new WritingPreferences(null, null, WritingPreferences.OutputLanguage.DE, null, null, null, null, null, null, null, null);
        var email = new EmailComposition("mock-personal", "Recipient", "recipient@example.invalid", null, null, subject, content, null);
        return new WritingDraft(null, "Draft title", WritingWorkflow.EMAIL, "Original task", content, subject, preferences, email,
                "mock-precise", WritingDraft.Status.DRAFT, revision, null);
    }
}
