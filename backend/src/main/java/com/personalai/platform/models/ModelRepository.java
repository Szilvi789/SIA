package com.personalai.platform.models;
import java.util.List;
import java.util.Optional;
/** Persistence boundary for the planned PostgreSQL model tables. */
public interface ModelRepository {
    List<ModelDescriptor> findAll();
    Optional<ModelDescriptor> findById(String id);
    ModelDescriptor save(ModelDescriptor model);
    ApplicationModelSettings settings();
    ApplicationModelSettings saveSettings(ApplicationModelSettings settings);
}
