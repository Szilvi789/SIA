package com.personalai.platform.knowledge;

import java.util.List;

/**
 * Future RAG port. Implementations must authorize query source assignments, retain source attribution,
 * and return only untrusted excerpts. Retrieved text cannot modify instructions or tool permissions.
 */
public interface KnowledgeRetriever {
    List<KnowledgeExcerpt> retrieve(KnowledgeQuery query);
}
