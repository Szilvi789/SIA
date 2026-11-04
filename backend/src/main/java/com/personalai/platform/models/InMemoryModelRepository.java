package com.personalai.platform.models;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** TEMPORARY: model definitions and tier mappings reset on process restart. */
@Repository
public class InMemoryModelRepository implements ModelRepository {
    private final Map<String, ModelDescriptor> models = new LinkedHashMap<>();
    private ApplicationModelSettings settings = new ApplicationModelSettings("mock-balanced",
            Map.of("ECONOMY", "mock-precise", "BALANCED", "mock-balanced", "ADVANCED", "mock-creative"));
    public InMemoryModelRepository() {
        save(new ModelDescriptor("mock-balanced", "Mock Balanced", "mock", "A deterministic demo for everyday tasks. No AI inference."));
        save(new ModelDescriptor("mock-precise", "Mock Precise", "mock", "A concise deterministic demo. No AI inference."));
        save(new ModelDescriptor("mock-creative", "Mock Creative", "mock", "A more exploratory demo template. No AI inference."));
    }
    public synchronized List<ModelDescriptor> findAll() { return List.copyOf(models.values()); }
    public synchronized Optional<ModelDescriptor> findById(String id) { return Optional.ofNullable(models.get(id)); }
    public synchronized ModelDescriptor save(ModelDescriptor model) { models.put(model.id(), model); return model; }
    public synchronized ApplicationModelSettings settings() { return settings; }
    public synchronized ApplicationModelSettings saveSettings(ApplicationModelSettings value) { settings = value; return value; }
}
