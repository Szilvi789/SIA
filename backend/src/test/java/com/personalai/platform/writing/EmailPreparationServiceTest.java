package com.personalai.platform.writing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.writing.email.EmailAccount;
import com.personalai.platform.writing.email.EmailConnector;
import com.personalai.platform.writing.email.EmailDeliveryResult;
import com.personalai.platform.writing.email.EmailDeliveryState;
import com.personalai.platform.writing.email.EmailPermission;
import com.personalai.platform.writing.email.EmailPreparationService;
import com.personalai.platform.writing.email.EmailPreview;
import com.personalai.platform.writing.email.SendEmailRequest;
import com.personalai.platform.writing.infrastructure.MockEmailConnector;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EmailPreparationServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC);
    private final MockEmailConnector connector = new MockEmailConnector();
    private final EmailPreparationService service = new EmailPreparationService(connector, clock);

    @Test
    void previewContainsExactSenderRecipientsSubjectBodyAndAnExplicitMockRestriction() {
        var message = composition("mock-personal", "recipient@example.invalid", "Reviewed subject", "Reviewed body\n  unchanged");
        var preview = service.preview(message);
        assertThat(preview.email()).isEqualTo(message);
        assertThat(preview.account().email()).isEqualTo("personal@example.invalid");
        assertThat(preview.mock()).isTrue();
        assertThat(preview.sendingAvailable()).isFalse();
        assertThat(preview.state()).isEqualTo(EmailDeliveryState.READY_TO_SEND);
        assertThat(preview.attachments()).isEmpty();
        assertThat(preview.createdAt()).isEqualTo(clock.instant());
    }
    @Test
    void explicitConfirmationIsRequiredAndMockSendingRemainsImpossibleEvenAfterConfirmation() {
        var preview = service.preview(validMessage());
        assertThatThrownBy(() -> service.send(new SendEmailRequest(preview.id(), false, "attempt-0001")))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("explicit confirmation");
        assertThatThrownBy(() -> service.send(new SendEmailRequest(preview.id(), true, "attempt-0001")))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("never sends email");
        assertThatThrownBy(() -> connector.send(preview, "attempt-0001"))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("unavailable");
        assertThat(preview.state()).isEqualTo(EmailDeliveryState.READY_TO_SEND);
    }
    @Test
    void validatesRecipientsHeaderInjectionRequiredFieldsAndAvailableAccounts() {
        for (String recipient : List.of("", "no-address", "person@example.invalid\r\nBcc:other@example.invalid", "a@example.invalid,")) {
            assertThatThrownBy(() -> service.preview(composition("mock-personal", recipient, "Subject", "Body")))
                    .isInstanceOf(InvalidConfigurationException.class);
        }
        assertThatThrownBy(() -> service.preview(composition("mock-personal", "to@example.invalid", "Injected\nSubject", "Body")))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("one line");
        assertThatThrownBy(() -> service.preview(composition("mock-personal", "to@example.invalid", "", "Body")))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> service.preview(composition("unknown", "to@example.invalid", "Subject", "Body")))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("available email account");
        var multiple = composition("mock-personal", "first@example.invalid; second@example.invalid", "Subject", "Body");
        assertThat(service.preview(multiple).email().to()).isEqualTo(multiple.to());
    }
    @Test
    void disconnectRemovesPermissionsAndBlocksNewPreviews() {
        service.disconnect("mock-personal");
        var account = service.accounts().stream().filter(candidate -> candidate.id().equals("mock-personal")).findFirst().orElseThrow();
        assertThat(account.connected()).isFalse();
        assertThat(account.permissions()).isEmpty();
        assertThatThrownBy(() -> service.preview(validMessage())).isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("disconnected");
    }
    @Test
    void futureDeliveryAdapterMustHaveSeparateSendPermissionAndCannotSendSameSnapshotTwice() {
        var future = new RecordingConnector(Set.of(EmailPermission.SEND_MESSAGES));
        var preparation = new EmailPreparationService(future, clock);
        var preview = preparation.preview(composition("future", "to@example.invalid", "Subject", "Body"));
        assertThat(preparation.send(new SendEmailRequest(preview.id(), true, "attempt-0001")).state()).isEqualTo(EmailDeliveryState.SENT);
        assertThatThrownBy(() -> preparation.send(new SendEmailRequest(preview.id(), true, "attempt-0001")))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("already attempted");
        assertThatThrownBy(() -> preparation.send(new SendEmailRequest(preview.id(), true, "attempt-0002")))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("already attempted");
        var secondPreview = preparation.preview(composition("future", "to@example.invalid", "Another subject", "Body"));
        assertThatThrownBy(() -> preparation.send(new SendEmailRequest(secondPreview.id(), true, "attempt-0001")))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("already attempted");
        assertThat(future.sends).isEqualTo(1);
        var readOnly = new EmailPreparationService(new RecordingConnector(Set.of(EmailPermission.READ_CONTACTS, EmailPermission.READ_MESSAGES)), clock);
        var blocked = readOnly.preview(composition("future", "to@example.invalid", "Subject", "Body"));
        assertThat(blocked.sendingAvailable()).isFalse();
        assertThatThrownBy(() -> readOnly.send(new SendEmailRequest(blocked.id(), true, "attempt-0003")))
                .isInstanceOf(InvalidConfigurationException.class);
    }
    private EmailComposition validMessage() { return composition("mock-personal", "recipient@example.invalid", "Subject", "Body"); }
    private EmailComposition composition(String account, String recipients, String subject, String body) {
        return new EmailComposition(account, "Recipient", recipients, "copy@example.invalid", "blind@example.invalid", subject, body, null);
    }
    private static class RecordingConnector implements EmailConnector {
        private final Set<EmailPermission> permissions;
        int sends;
        RecordingConnector(Set<EmailPermission> permissions) { this.permissions = permissions; }
        public List<EmailAccount> accounts() { return List.of(new EmailAccount("future", "Test adapter", "from@example.invalid", "TEST", true, permissions, false)); }
        public void disconnect(String id) { }
        public boolean supportsSending() { return true; }
        public EmailDeliveryResult send(EmailPreview preview, String key) { sends++; return new EmailDeliveryResult(preview.id(), EmailDeliveryState.SENT, "In-memory test only", false); }
    }
}
