package com.personalai.platform.writing;

import java.util.List;
import java.util.Optional;

public interface DraftRepository {
    List<WritingDraft> findAll();
    Optional<WritingDraft> findById(String id);
    WritingDraft save(WritingDraft draft, Long expectedRevision);
}
