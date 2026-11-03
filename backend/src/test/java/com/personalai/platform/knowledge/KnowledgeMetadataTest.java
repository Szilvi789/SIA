package com.personalai.platform.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalai.platform.common.InvalidConfigurationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class KnowledgeMetadataTest {
    @ParameterizedTest
    @EnumSource(KnowledgeFormat.class)
    void supportsDeclaredFormatsAndCanonicalMediaTypes(KnowledgeFormat format) {
        var fileName = "reference" + format.extension().toUpperCase(Locale.ROOT);
        var upload = new DocumentUpload(fileName, format, format.mediaType(), 42);

        assertThat(KnowledgeFormat.fromFileName(fileName)).isEqualTo(format);
        assertThat(upload.byteSize()).isEqualTo(42);
    }

    @ParameterizedTest
    @ValueSource(strings = {"document.exe", "document", "../secret.md", "C:\\secret.md", "C:secret.md", ".md", "bad\n.md"})
    void rejectsUnsupportedNamesAndPaths(String fileName) {
        assertThatThrownBy(() -> KnowledgeFormat.fromFileName(fileName))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void rejectsMismatchedExtensionsAndUploadMediaTypes() {
        assertThatThrownBy(() -> new DocumentUpload("guide.pdf", KnowledgeFormat.MARKDOWN, "text/markdown", 42))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> new DocumentUpload("guide.md", KnowledgeFormat.MARKDOWN, "application/pdf", 42))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void copiesAssociationsAndDistinguishesAgentFromProjectScope() {
        var associations = new ArrayList<>(List.of(
                new KnowledgeAssociation(KnowledgeAssociation.Scope.AGENT, "coding-agent"),
                new KnowledgeAssociation(KnowledgeAssociation.Scope.PROJECT, "documentation-agent")));
        var source = source("guide", associations);
        associations.clear();

        assertThat(source.associations()).hasSize(2);
        assertThat(source.isAssignedToAgent("coding-agent")).isTrue();
        assertThat(source.isAssignedToAgent("documentation-agent")).isFalse();
        assertThatThrownBy(() -> source.associations().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNegativeSizesAndStoragePaths() {
        assertThatThrownBy(() -> new KnowledgeSource("guide", "guide.md", KnowledgeFormat.MARKDOWN,
                "../secret", 0, List.of())).isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> new KnowledgeSource("guide", "guide.md", KnowledgeFormat.MARKDOWN,
                "document-key", -1, List.of())).isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> new DocumentUpload("guide.md", KnowledgeFormat.MARKDOWN, "text/markdown", -1))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void rejectsInvalidAssociationsAndDuplicateMetadata() {
        var association = new KnowledgeAssociation(KnowledgeAssociation.Scope.AGENT, "coding-agent");
        assertThatThrownBy(() -> new KnowledgeAssociation(null, "coding-agent"))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> new KnowledgeAssociation(KnowledgeAssociation.Scope.AGENT, " "))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> source("guide", List.of(association, association)))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    private KnowledgeSource source(String id, List<KnowledgeAssociation> associations) {
        return new KnowledgeSource(id, "guide.md", KnowledgeFormat.MARKDOWN, "document-key", 42, associations);
    }
}
