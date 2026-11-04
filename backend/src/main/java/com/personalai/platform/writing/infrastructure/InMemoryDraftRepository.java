package com.personalai.platform.writing.infrastructure;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.writing.DraftRepository;
import com.personalai.platform.writing.WritingDraft;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Repository
@ConditionalOnProperty(name = "sai.mode", havingValue = "demo", matchIfMissing = true)
public class InMemoryDraftRepository implements DraftRepository {
    private final ConcurrentHashMap<String, WritingDraft> drafts = new ConcurrentHashMap<>();
    @Override public List<WritingDraft> findAll() {
        return drafts.values().stream().sorted(Comparator.comparing(WritingDraft::updatedAt).reversed()).toList();
    }
    @Override public Optional<WritingDraft> findById(String id) { return Optional.ofNullable(drafts.get(id)); }
    @Override public WritingDraft save(WritingDraft draft, Long expectedRevision) {
        return drafts.compute(draft.id(), (id, previous) -> {
            if (previous == null && expectedRevision != null || previous != null && !previous.revision().equals(expectedRevision)) {
                throw new InvalidConfigurationException("revision", "This draft changed. Reload it before saving again.");
            }
            return draft;
        });
    }
}
