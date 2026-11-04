package com.personalai.platform.chat;

import com.personalai.platform.agents.AgentIds;
import com.personalai.platform.persistence.PersistentDocumentStore;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "sai.mode", havingValue = "personal")
public class PostgresConversationRepository implements ConversationRepository {
    private final PersistentDocumentStore store;
    public PostgresConversationRepository(PersistentDocumentStore store) { this.store = store; }
    public List<Conversation> findAll() { return store.all("conversations", Conversation.class); }
    public Optional<Conversation> find(String id) { return store.find("conversations", id, Conversation.class); }
    public Conversation append(String id, String message, ChatResponse response) {
        return store.mutate("conversations", id, Conversation.class, previous -> previous.orElseGet(() ->
                new Conversation(id, AgentIds.canonical(response.agentId()), List.of(), response.createdAt())).append(message, response));
    }
}
