package com.personalai.platform.knowledge;

import java.util.List;
import java.util.Optional;

/** Stores document metadata independently of document bytes and AI providers. */
public interface KnowledgeRepository {
    List<KnowledgeSource> findAll();

    Optional<KnowledgeSource> findById(String id);

    KnowledgeSource save(KnowledgeSource source);
}
