package com.personalai.platform.models;

import com.personalai.platform.common.InvalidConfigurationException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModelCatalog {
    private static final List<ModelDescriptor> MODELS = List.of(
            new ModelDescriptor("mock-balanced", "Mock Balanced", "mock",
                    "A deterministic demo for everyday tasks. No AI inference."),
            new ModelDescriptor("mock-precise", "Mock Precise", "mock",
                    "A concise deterministic demo. No AI inference."),
            new ModelDescriptor("mock-creative", "Mock Creative", "mock",
                    "A more exploratory demo template. No AI inference."));

    public List<ModelDescriptor> findAll() {
        return MODELS;
    }

    public ModelDescriptor requireCompatible(String providerId, String modelId) {
        var model = MODELS.stream().filter(candidate -> candidate.id().equals(modelId)).findFirst()
                .orElseThrow(() -> new InvalidConfigurationException("Select an available model."));
        if (!model.providerId().equals(providerId)) {
            throw new InvalidConfigurationException("The selected model does not belong to this provider.");
        }
        return model;
    }
}
