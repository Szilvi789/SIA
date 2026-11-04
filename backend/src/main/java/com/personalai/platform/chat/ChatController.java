package com.personalai.platform.chat;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {
    private final AgentExecutionService execution;
    private final ConversationService conversations;

    public ChatController(AgentExecutionService execution, ConversationService conversations) {
        this.execution = execution; this.conversations = conversations;
    }

    @PostMapping("/api/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        conversations.validateOwner(request.conversationId(), request.agentId());
        return conversations.append(request, execution.execute(request));
    }
}
