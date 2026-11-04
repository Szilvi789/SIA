package com.personalai.platform.chat;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class ConversationController {
    private final ConversationService conversations;
    public ConversationController(ConversationService conversations) { this.conversations = conversations; }
    @GetMapping("/api/conversations") public List<Conversation> findAll() { return conversations.findAll(); }
    @GetMapping("/api/conversations/{id}") public Conversation find(@PathVariable String id) { return conversations.find(id); }
}
