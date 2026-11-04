package com.personalai.platform.chat;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository {
    List<Conversation> findAll();
    Optional<Conversation> find(String id);
    Conversation append(String id, String message, ChatResponse response);
}
