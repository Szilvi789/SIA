package com.personalai.platform.writing.email;

import java.util.List;

/** Future adapters use official OAuth flows and encrypted token storage, never browser or chat credentials. */
public interface EmailConnector {
    List<EmailAccount> accounts();
    void disconnect(String accountId);
    boolean supportsSending();
    EmailDeliveryResult send(EmailPreview preview, String idempotencyKey);
}
