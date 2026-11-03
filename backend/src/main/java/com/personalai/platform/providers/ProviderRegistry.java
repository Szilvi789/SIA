package com.personalai.platform.providers;

import com.personalai.platform.common.InvalidConfigurationException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ProviderRegistry {
    private final Map<String, AIProvider> providers;

    public ProviderRegistry(List<AIProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(AIProvider::id, Function.identity()));
    }

    public AIProvider require(String id) {
        var provider = providers.get(id);
        if (provider == null) {
            throw new InvalidConfigurationException("Select an available provider.");
        }
        return provider;
    }
}
