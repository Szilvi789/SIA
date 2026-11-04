package com.personalai.platform.providers;
import java.util.Map;
public interface ProviderConfigurationRepository {
    Map<String, Boolean> enabledStates();
    void setEnabled(String id, boolean enabled);
}
