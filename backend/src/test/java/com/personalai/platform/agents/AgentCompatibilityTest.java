package com.personalai.platform.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.personalai.platform.knowledge.KnowledgeAssociation;
import com.personalai.platform.knowledge.KnowledgeFormat;
import com.personalai.platform.knowledge.KnowledgeSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentCompatibilityTest {
    @Test
    void legacyReferencesResolveWithoutDuplicatingTheCanonicalAgentList() {
        var repository = new InMemoryAgentRepository();
        assertThat(repository.findAll()).extracting(Agent::id)
                .containsExactly("coding-agent", "documentation-agent", "writing-agent");
        assertThat(repository.findById("translation-agent").orElseThrow().id()).isEqualTo("translation-agent");
        assertThat(repository.findById("email-assistant").orElseThrow().id()).isEqualTo("email-assistant");
        assertThat(repository.findById("translation-agent").orElseThrow().name())
                .isEqualTo(repository.findById("writing-agent").orElseThrow().name());
    }

    @Test
    void aLegacyUpdateChangesTheSharedWritingConfigurationAndRetainsTheRequestedId() {
        var repository = new InMemoryAgentRepository();
        var previous = repository.findById("email-assistant").orElseThrow();
        var edited = new Agent(previous.id(), "My writer", previous.description(), "Keep instructions.",
                "mock", "mock-precise", 0.2, 1024, List.of(), List.of("email-style"), List.of());

        assertThat(repository.update(edited).id()).isEqualTo("email-assistant");
        assertThat(repository.findById("writing-agent").orElseThrow().systemPrompt()).isEqualTo("Keep instructions.");
        assertThat(repository.findById("translation-agent").orElseThrow().ruleIds()).containsExactly("email-style");
        assertThat(repository.findAll()).hasSize(3);
    }

    @Test
    void existingKnowledgeAssociationsFollowTheSameIdentityWithoutGrantingProjectAccess() {
        var associated = new KnowledgeSource("notes", "notes.md", KnowledgeFormat.MARKDOWN, "notes-content", 20,
                List.of(new KnowledgeAssociation(KnowledgeAssociation.Scope.AGENT, "translation-agent")));
        assertThat(associated.isAssignedToAgent("writing-agent")).isTrue();
        assertThat(associated.isAssignedToAgent("email-assistant")).isTrue();
        assertThat(associated.isAssignedToAgent("coding-agent")).isFalse();
        var projectOnly = new KnowledgeSource("project", "project.md", KnowledgeFormat.MARKDOWN, "project-content", 20,
                List.of(new KnowledgeAssociation(KnowledgeAssociation.Scope.PROJECT, "translation-agent")));
        assertThat(projectOnly.isAssignedToAgent("writing-agent")).isFalse();
    }
}
