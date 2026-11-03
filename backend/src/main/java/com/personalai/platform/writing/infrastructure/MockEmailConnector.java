package com.personalai.platform.writing.infrastructure;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.common.ResourceNotFoundException;
import com.personalai.platform.writing.email.EmailAccount;
import com.personalai.platform.writing.email.EmailConnector;
import com.personalai.platform.writing.email.EmailDeliveryResult;
import com.personalai.platform.writing.email.EmailPermission;
import com.personalai.platform.writing.email.EmailPreview;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class MockEmailConnector implements EmailConnector {
    private final ConcurrentHashMap<String, EmailAccount> accounts = new ConcurrentHashMap<>();
    public MockEmailConnector() {
        var permissions = Set.of(EmailPermission.READ_CONTACTS, EmailPermission.READ_MESSAGES, EmailPermission.MANAGE_DRAFTS);
        accounts.put("mock-personal", new EmailAccount("mock-personal", "Personal account · mock", "personal@example.invalid", "MOCK", true, permissions, true));
        accounts.put("mock-applications", new EmailAccount("mock-applications", "Applications account · mock", "applications@example.invalid", "MOCK", true, permissions, true));
    }
    @Override public List<EmailAccount> accounts() { return accounts.values().stream().sorted(Comparator.comparing(EmailAccount::id)).toList(); }
    @Override public void disconnect(String accountId) {
        if (!accounts.containsKey(accountId)) throw new ResourceNotFoundException("Email account was not found.");
        accounts.computeIfPresent(accountId, (id, previous) -> new EmailAccount(previous.id(), previous.name(), previous.email(), previous.provider(), false, Set.of(), true));
    }
    @Override public boolean supportsSending() { return false; }
    @Override public EmailDeliveryResult send(EmailPreview preview, String idempotencyKey) {
        throw new InvalidConfigurationException("Live sending is unavailable with the mock email connector.");
    }
}
