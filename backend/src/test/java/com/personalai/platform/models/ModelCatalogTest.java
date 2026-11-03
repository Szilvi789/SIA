package com.personalai.platform.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalai.platform.common.InvalidConfigurationException;
import org.junit.jupiter.api.Test;

class ModelCatalogTest {
    private final ModelCatalog models = new ModelCatalog();

    @Test
    void resolvesAvailableModelsForTheirProvider() {
        assertThat(models.requireCompatible("mock", "mock-balanced").name()).isEqualTo("Mock Balanced");
        assertThat(models.findAll()).hasSize(3);
    }

    @Test
    void rejectsAModelFromADifferentProvider() {
        assertThatThrownBy(() -> models.requireCompatible("another-provider", "mock-balanced"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("does not belong");
    }
}
