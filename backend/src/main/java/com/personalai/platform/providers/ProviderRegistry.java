package com.personalai.platform.providers;

import com.personalai.platform.common.InvalidConfigurationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProviderRegistry {
    private final Map<String, AIProvider> providers;
    private final Map<String, String> names = new LinkedHashMap<>();
    private final ProviderConfigurationRepository configuration;
    public ProviderRegistry(List<AIProvider> providers) { this(providers, new InMemoryProviderConfigurationRepository()); }
    @Autowired public ProviderRegistry(List<AIProvider> providers, ProviderConfigurationRepository configuration) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(AIProvider::id, Function.identity()));
        this.configuration = configuration;
        names.put("mock", "Mock demo"); names.put("openai", "OpenAI"); names.put("anthropic", "Anthropic"); names.put("local", "Local model (future adapter)");
        providers.forEach(provider -> names.putIfAbsent(provider.id(), provider.id()));
    }
    public List<ProviderConfiguration> findAll() { return names.keySet().stream().map(this::configuration).toList(); }
    public ProviderConfiguration configuration(String id) {
        var name = names.get(id);
        if (name == null) throw new InvalidConfigurationException("providerId", "Select an available provider.");
        var adapter = providers.get(id);
        boolean enabled = configuration.enabledStates().getOrDefault(id, "mock".equals(id));
        return new ProviderConfiguration(id, name, enabled, adapter != null,
                adapter != null && adapter.configured(), adapter != null && adapter.mock());
    }
    public ProviderConfiguration setEnabled(String id, boolean enabled) {
        configuration(id);
        configuration.setEnabled(id, enabled);
        return configuration(id);
    }
    public boolean available(String id) {
        var config = configuration(id);
        return config.enabled() && config.adapterAvailable() && config.configured();
    }
    public AIProvider require(String id) {
        var config = configuration(id);
        if (!config.enabled()) throw new InvalidConfigurationException("providerId", "The selected provider is disabled.");
        if (!config.adapterAvailable()) throw new InvalidConfigurationException("providerId", "This provider has no installed execution adapter. Configure an adapter before generation.");
        if (!config.configured()) throw new InvalidConfigurationException("providerId", "The selected provider requires explicit server-side credential configuration.");
        return providers.get(id);
    }
}
