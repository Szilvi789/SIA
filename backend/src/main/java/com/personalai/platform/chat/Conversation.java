package com.personalai.platform.chat;

import com.personalai.platform.agents.AgentIds;
import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.providers.ExecutionMetadata;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** User-owned content: stored only in private PostgreSQL or temporary demo memory. */
public record Conversation(String id, String agentId, List<Message> messages, Instant updatedAt) {
    public Conversation { messages = List.copyOf(messages); }
    public record Message(String id, String role, String content, Instant createdAt, String modelId,
            String providerId, String source, ExecutionMetadata execution) { }

    public Conversation append(String message, ChatResponse response) {
        if (!agentId.equals(AgentIds.canonical(response.agentId()))) throw new InvalidConfigurationException("conversationId", "This conversation belongs to another agent.");
        var next = new ArrayList<>(messages);
        next.add(new Message(response.id() + "-user", "user", message, response.createdAt(), null, null, null, null));
        next.add(new Message(response.id(), "assistant", response.content(), response.createdAt(), response.modelId(), response.providerId(), response.source(), response.execution()));
        return new Conversation(id, agentId, next, response.createdAt());
    }
}
