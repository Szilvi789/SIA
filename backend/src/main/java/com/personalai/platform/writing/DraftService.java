package com.personalai.platform.writing;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.common.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DraftService {
    private final DraftRepository repository;
    private final Clock clock;
    public DraftService(DraftRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }
    public List<WritingDraft> findAll() { return repository.findAll(); }
    public WritingDraft findById(String id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Writing draft was not found."));
    }
    public WritingDraft create(WritingDraft request) { return repository.save(snapshot(UUID.randomUUID().toString(), 1L, request), null); }
    public WritingDraft update(String id, WritingDraft request) {
        var existing = findById(id);
        if (request.revision() == null || !request.revision().equals(existing.revision())) {
            throw new InvalidConfigurationException("revision", "This draft changed. Reload it before saving again.");
        }
        return repository.save(snapshot(id, existing.revision() + 1, request), request.revision());
    }
    private WritingDraft snapshot(String id, Long revision, WritingDraft source) {
        return new WritingDraft(id, source.title(), source.workflow(), source.input(), source.content(), source.subject(),
                source.preferences(), source.email(), source.modelId(), WritingDraft.Status.DRAFT, revision, Instant.now(clock));
    }
}
