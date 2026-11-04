package com.personalai.platform.chat;

import com.personalai.platform.agents.AgentIds;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "sai.mode", havingValue = "demo", matchIfMissing = true)
public class InMemoryConversationRepository implements ConversationRepository {
    private final ConcurrentHashMap<String, Conversation> conversations = new ConcurrentHashMap<>();
    public List<Conversation> findAll() { return List.copyOf(conversations.values()); }
    public Optional<Conversation> find(String id) { return Optional.ofNullable(conversations.get(id)); }
    public Conversation append(String id, String message, ChatResponse response) {
        return conversations.compute(id, (key, existing) -> (existing == null
                ? new Conversation(id, AgentIds.canonical(response.agentId()), List.of(), response.createdAt()) : existing).append(message, response));
    }
}
