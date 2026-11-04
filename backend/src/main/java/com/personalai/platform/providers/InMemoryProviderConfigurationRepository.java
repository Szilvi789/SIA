package com.personalai.platform.providers;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
/** TEMPORARY: provider enabled states reset on restart; credentials never enter this repository. */
@Repository
public class InMemoryProviderConfigurationRepository implements ProviderConfigurationRepository {
    private final Map<String, Boolean> states = new ConcurrentHashMap<>();
    public Map<String, Boolean> enabledStates() { return Map.copyOf(states); }
    public void setEnabled(String id, boolean enabled) { states.put(id, enabled); }
}
