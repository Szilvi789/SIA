package com.personalai.platform.models;
import java.util.Map;
public record ApplicationModelSettings(String defaultModelId, Map<String, String> tierMappings) {
    public ApplicationModelSettings { tierMappings = ModelSettings.normalizeMappings(tierMappings); }
}
