package com.personalai.platform.writing.infrastructure;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.writing.recipient.RecipientCandidate;
import com.personalai.platform.writing.recipient.RecipientCandidate.Classification;
import com.personalai.platform.writing.recipient.RecipientLookup;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Reserved invalid-domain fixtures only, never guessed addresses for real people or organizations. */
@Component
public class MockRecipientLookup implements RecipientLookup {
    @Override public List<RecipientCandidate> suggest(String query, String accountId, boolean explicitlyRequested) {
        if (!explicitlyRequested) throw new InvalidConfigurationException("Recipient lookup requires explicit user action.");
        if (query == null || query.isBlank()) return List.of();
        return List.of(new RecipientCandidate("recruitment@example.invalid", "Example recruitment team · mock", "Example role · mock",
                Classification.MOCK, Classification.MOCK, Classification.MOCK, null,
                "Static demo fixture. No authorized contacts, correspondence or website was searched.", Instant.EPOCH, true));
    }
}
