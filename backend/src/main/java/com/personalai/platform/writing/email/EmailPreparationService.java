package com.personalai.platform.writing.email;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.common.ResourceNotFoundException;
import com.personalai.platform.writing.EmailComposition;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Only explicit preview snapshots can be approved; generation has no access to delivery. */
@Service
public class EmailPreparationService {
    private final EmailConnector connector;
    private final Clock clock;
    private final ConcurrentHashMap<String, EmailPreview> previews = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> attempted = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> attemptedPreviews = new ConcurrentHashMap<>();
    public EmailPreparationService(EmailConnector connector, Clock clock) { this.connector = connector; this.clock = clock; }
    public List<EmailAccount> accounts() { return connector.accounts(); }
    public void disconnect(String id) { connector.disconnect(id); }
    public boolean sendingAvailable() { return connector.supportsSending(); }

    public EmailPreview preview(EmailComposition email) {
        var account = requireAccount(email.accountId());
        if (!account.connected()) throw new InvalidConfigurationException("accountId", "This account is disconnected.");
        validateRecipients(email.to(), true);
        validateRecipients(email.cc(), false);
        validateRecipients(email.bcc(), false);
        if (email.subject().isBlank() || email.body().isBlank()) throw new InvalidConfigurationException("Review a subject and body before previewing the email.");
        if (email.subject().contains("\r") || email.subject().contains("\n")) throw new InvalidConfigurationException("subject", "Email subjects must fit on one line.");
        boolean available = connector.supportsSending() && !account.mock() && account.permissions().contains(EmailPermission.SEND_MESSAGES);
        var preview = new EmailPreview(UUID.randomUUID().toString(), account, email, List.of(), EmailDeliveryState.READY_TO_SEND,
                available, account.mock(), Instant.now(clock));
        previews.put(preview.id(), preview);
        return preview;
    }
    public synchronized EmailDeliveryResult send(SendEmailRequest request) {
        if (!request.confirmed()) throw new InvalidConfigurationException("Sending requires explicit confirmation of the exact preview.");
        if (request.idempotencyKey() == null || !request.idempotencyKey().matches("[A-Za-z0-9-]{8,80}")) {
            throw new InvalidConfigurationException("A valid idempotency key is required.");
        }
        if (request.previewId() == null || request.previewId().isBlank()) throw new InvalidConfigurationException("An email preview is required.");
        var preview = previews.get(request.previewId());
        if (preview == null) throw new ResourceNotFoundException("Review an existing email preview before sending.");
        var account = requireAccount(preview.account().id());
        if (!account.connected() || !account.permissions().contains(EmailPermission.SEND_MESSAGES)
                || account.mock() || !connector.supportsSending() || !preview.sendingAvailable()) {
            throw new InvalidConfigurationException("Live sending is unavailable. This mock connector never sends email.");
        }
        if (attempted.containsKey(request.idempotencyKey()) || attemptedPreviews.containsKey(preview.id())) {
            throw new InvalidConfigurationException("This delivery was already attempted. Check its result before retrying.");
        }
        attempted.put(request.idempotencyKey(), preview.id());
        attemptedPreviews.put(preview.id(), true);
        return connector.send(preview, request.idempotencyKey());
    }
    private EmailAccount requireAccount(String id) {
        return connector.accounts().stream().filter(account -> account.id().equals(id)).findFirst()
                .orElseThrow(() -> new InvalidConfigurationException("accountId", "Select an available email account."));
    }
    private void validateRecipients(String recipients, boolean required) {
        if (recipients.isBlank()) {
            if (required) throw new InvalidConfigurationException("to", "Enter at least one recipient.");
            return;
        }
        var addresses = recipients.split("[,;]", -1);
        if (addresses.length > 50) throw new InvalidConfigurationException("Use at most 50 recipients per field.");
        for (var address : addresses) {
            if (!address.strip().matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,}")) {
                throw new InvalidConfigurationException("Enter valid email addresses separated by commas or semicolons.");
            }
        }
    }
}
