package com.personalai.platform.knowledge;

import java.util.List;
import org.springframework.stereotype.Component;

/** Retrieval remains disabled: no document reads, parsing, network calls, embeddings, or vectors. */
@Component
public class NoOpKnowledgeRetriever implements KnowledgeRetriever {
    @Override
    public List<KnowledgeExcerpt> retrieve(KnowledgeQuery query) {
        return List.of();
    }
}
