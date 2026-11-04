package com.personalai.platform.providers;
import com.personalai.platform.common.InvalidConfigurationException;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController
public class ProviderController {
    private final ProviderRegistry providers;
    public ProviderController(ProviderRegistry providers) { this.providers = providers; }
    @GetMapping("/api/providers") public List<ProviderConfiguration> findAll() { return providers.findAll(); }
    @GetMapping("/api/providers/{id}") public ProviderConfiguration find(@PathVariable String id) { return providers.configuration(id); }
    @PutMapping("/api/providers/{id}") public ProviderConfiguration update(@PathVariable String id, @RequestBody Update request) {
        if (request.enabled() == null) throw new InvalidConfigurationException("enabled", "Specify whether the provider is enabled.");
        return providers.setEnabled(id, request.enabled());
    }
    public record Update(Boolean enabled) { }
}
