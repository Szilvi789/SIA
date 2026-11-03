package com.personalai.platform.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalai.platform.common.InvalidConfigurationException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeServiceTest {
    private InMemoryKnowledgeRepository repository;
    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryKnowledgeRepository();
        service = new KnowledgeService(repository);
    }

    @Test
    void startsEmptyAndAcceptsAgentsWithoutKnowledge() {
        assertThat(repository.findAll()).isEmpty();
        assertThatCode(() -> service.validateAssignments("coding-agent", List.of())).doesNotThrowAnyException();
    }

    @Test
    void acceptsKnownSourcesExplicitlyAssociatedWithTheAgent() {
        repository.save(source("guide", KnowledgeAssociation.Scope.AGENT, "coding-agent"));
        repository.save(source("examples", KnowledgeAssociation.Scope.AGENT, "coding-agent"));

        assertThatCode(() -> service.validateAssignments("coding-agent", List.of("examples", "guide")))
                .doesNotThrowAnyException();
        assertThat(repository.findAll()).extracting(KnowledgeSource::id).containsExactly("examples", "guide");
    }

    @Test
    void rejectsUnknownSources() {
        assertThatThrownBy(() -> service.validateAssignments("coding-agent", List.of("missing")))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void rejectsAnotherAgentsSource() {
        repository.save(source("guide", KnowledgeAssociation.Scope.AGENT, "documentation-agent"));

        assertThatThrownBy(() -> service.validateAssignments("coding-agent", List.of("guide")))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void projectAssociationDoesNotGrantAgentAccessEvenForMatchingTargetIds() {
        repository.save(source("guide", KnowledgeAssociation.Scope.PROJECT, "coding-agent"));

        assertThatThrownBy(() -> service.validateAssignments("coding-agent", List.of("guide")))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void rejectsDuplicateReferences() {
        repository.save(source("guide", KnowledgeAssociation.Scope.AGENT, "coding-agent"));

        assertThatThrownBy(() -> service.validateAssignments("coding-agent", List.of("guide", "guide")))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void validatesReferencesAndAgentIdentity() {
        assertThatThrownBy(() -> service.validateAssignments("../agent", List.of()))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> service.validateAssignments("coding-agent", null))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> service.validateAssignments("coding-agent", List.of(" ")))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    private KnowledgeSource source(String id, KnowledgeAssociation.Scope scope, String targetId) {
        return new KnowledgeSource(id, "guide.md", KnowledgeFormat.MARKDOWN, "document-" + id, 42,
                List.of(new KnowledgeAssociation(scope, targetId)));
    }
}
