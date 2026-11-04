package com.personalai.platform.chat;

import com.personalai.platform.agents.AgentIds;
import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.common.ResourceNotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {
    private final ConversationRepository repository;
    public ConversationService(ConversationRepository repository) { this.repository = repository; }
    public List<Conversation> findAll() { return repository.findAll().stream().sorted(Comparator.comparing(Conversation::updatedAt).reversed()).toList(); }
    public Conversation find(String id) { return repository.find(validateId(id)).orElseThrow(() -> new ResourceNotFoundException("Conversation was not found.")); }
    public void validateOwner(String id, String agentId) {
        if (id == null || id.isBlank()) return;
        repository.find(validateId(id)).ifPresent(conversation -> {
            if (!conversation.agentId().equals(AgentIds.canonical(agentId))) throw new InvalidConfigurationException("conversationId", "This conversation belongs to another agent.");
        });
    }
    public ChatResponse append(ChatRequest request, ChatResponse response) {
        var id = request.conversationId() == null || request.conversationId().isBlank() ? UUID.randomUUID().toString() : validateId(request.conversationId());
        repository.append(id, request.message().strip(), response);
        return new ChatResponse(response.id(), response.agentId(), response.modelId(), response.content(), response.createdAt(),
                response.providerId(), response.source(), response.execution(), id);
    }
    private static String validateId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")) throw new InvalidConfigurationException("conversationId", "Use 1-120 letters, numbers, dots, underscores, or hyphens for the conversation ID.");
        return id;
    }
}
