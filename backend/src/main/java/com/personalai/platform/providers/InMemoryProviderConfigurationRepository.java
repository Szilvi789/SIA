package com.personalai.platform.providers;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
/** TEMPORARY: provider enabled states reset on restart; credentials never enter this repository. */
@Repository
@ConditionalOnProperty(name = "sai.mode", havingValue = "demo", matchIfMissing = true)
public class InMemoryProviderConfigurationRepository implements ProviderConfigurationRepository {
    private final Map<String, Boolean> states = new ConcurrentHashMap<>();
    public Map<String, Boolean> enabledStates() { return Map.copyOf(states); }
    public void setEnabled(String id, boolean enabled) { states.put(id, enabled); }
}
