package com.personalai.platform.writing.email;

import com.personalai.platform.writing.EmailComposition;
import java.time.Instant;
import java.util.List;

/** Immutable preview is the exact snapshot that a future approval must authorize. */
public record EmailPreview(String id, EmailAccount account, EmailComposition email, List<String> attachments,
        EmailDeliveryState state, boolean sendingAvailable, boolean mock, Instant createdAt) {
    public EmailPreview { attachments = List.copyOf(attachments); }
}
