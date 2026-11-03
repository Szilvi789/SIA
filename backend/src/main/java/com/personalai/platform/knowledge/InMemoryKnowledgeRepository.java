package com.personalai.platform.knowledge;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

/** Empty on startup; this repository stores metadata only, with no document ingestion. */
@Repository
public class InMemoryKnowledgeRepository implements KnowledgeRepository {
    private final ConcurrentMap<String, KnowledgeSource> sources = new ConcurrentHashMap<>();

    @Override
    public List<KnowledgeSource> findAll() {
        return sources.values().stream().sorted(Comparator.comparing(KnowledgeSource::id)).toList();
    }

    @Override
    public Optional<KnowledgeSource> findById(String id) {
        return Optional.ofNullable(sources.get(id));
    }

    @Override
    public KnowledgeSource save(KnowledgeSource source) {
        sources.put(source.id(), source);
        return source;
    }
}
