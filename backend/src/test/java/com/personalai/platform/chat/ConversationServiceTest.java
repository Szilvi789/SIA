package com.personalai.platform.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.common.ResourceNotFoundException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ConversationServiceTest {
    private final InMemoryConversationRepository repository = new InMemoryConversationRepository();
    private final ConversationService conversations = new ConversationService(repository);

    @Test
    void createsAnIdAndRetainsBothSidesOfTheConversationAcrossRequests() {
        var first = conversations.append(new ChatRequest("writing-agent", null, " First task "), response("reply-1", "writing-agent", "First reply", 1));
        assertThat(first.conversationId()).isNotBlank();
        var next = request("writing-agent", first.conversationId(), "Second task");
        conversations.validateOwner(next.conversationId(), next.agentId());
        conversations.append(next, response("reply-2", "writing-agent", "Second reply", 2));
        var saved = conversations.find(first.conversationId());
        assertThat(saved.messages()).extracting(Conversation.Message::content).containsExactly("First task", "First reply", "Second task", "Second reply");
        assertThat(saved.messages()).extracting(Conversation.Message::role).containsExactly("user", "assistant", "user", "assistant");
        assertThat(saved.updatedAt()).isEqualTo(Instant.parse("2026-11-04T10:00:02Z"));
        assertThat(new InMemoryConversationRepository().findAll()).isEmpty();
    }

    @Test
    void rejectsCrossAgentAccessBeforeGenerationAndAgainDuringAtomicAppend() {
        conversations.append(request("coding-agent", "synthetic-thread", "Synthetic task"), response("reply-1", "coding-agent", "Synthetic reply", 1));
        assertThatThrownBy(() -> conversations.validateOwner("synthetic-thread", "writing-agent"))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("another agent");
        assertThatThrownBy(() -> conversations.append(request("writing-agent", "synthetic-thread", "Different task"), response("reply-2", "writing-agent", "Other reply", 2)))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThat(conversations.find("synthetic-thread").messages()).hasSize(2);
    }

    @Test
    void recognizesLegacyAgentAliasesAndRejectsFilesystemLikeIdentifiers() {
        conversations.append(request("email-assistant", "synthetic-thread", "Synthetic task"), response("reply-1", "email-assistant", "Synthetic reply", 1));
        conversations.validateOwner("synthetic-thread", "translation-agent");
        assertThat(conversations.find("synthetic-thread").agentId()).isEqualTo("writing-agent");
        assertThatThrownBy(() -> conversations.find("../../private.md")).isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> conversations.find("unknown-thread")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void concurrentAppendsNeverOverwriteAnEarlierExchange() {
        var tasks = IntStream.range(0, 30).mapToObj(index -> CompletableFuture.runAsync(() ->
                conversations.append(request("coding-agent", "synthetic-thread", "Synthetic task " + index),
                        response("reply-" + index, "coding-agent", "Synthetic reply " + index, 1))))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(tasks).join();
        assertThat(conversations.find("synthetic-thread").messages()).hasSize(60);
        assertThat(conversations.find("synthetic-thread").messages()).extracting(Conversation.Message::id).doesNotHaveDuplicates();
    }

    private ChatRequest request(String agent, String id, String message) {
        return new ChatRequest(agent, null, message, null, null, null, null, null, id);
    }

    private ChatResponse response(String id, String agent, String content, int second) {
        return new ChatResponse(id, agent, "mock-balanced", content, Instant.parse("2026-11-04T10:00:0" + second + "Z"));
    }
}
