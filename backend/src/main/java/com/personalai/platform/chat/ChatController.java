package com.personalai.platform.chat;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {
    private final AgentExecutionService execution;

    public ChatController(AgentExecutionService execution) {
        this.execution = execution;
    }

    @PostMapping("/api/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return execution.execute(request);
    }
}
