package com.personalai.platform.writing.recipient;

import java.util.List;

/** Optional explicit lookup only. Live adapters must check authorized sources and preserve provenance. */
public interface RecipientLookup {
    List<RecipientCandidate> suggest(String query, String accountId, boolean explicitlyRequested);
}
