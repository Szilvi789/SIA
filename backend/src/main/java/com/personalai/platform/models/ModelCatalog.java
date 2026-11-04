package com.personalai.platform.models;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.providers.ProviderRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ModelCatalog {
    public static final Set<String> TIERS = Set.of("ECONOMY", "BALANCED", "ADVANCED");
    public static final Set<String> CAPABILITIES = Set.of("TEXT", "STRUCTURED_OUTPUT", "TOOLS", "VISION", "EMBEDDING");
    public static final Set<String> PARAMETERS = Set.of("maxOutputTokens", "temperature", "topP", "reasoningEffort", "timeoutMs", "tools");
    private final ModelRepository repository;
    private final ProviderRegistry providers;
    public ModelCatalog() { this(new InMemoryModelRepository(), null); }
    @Autowired public ModelCatalog(ModelRepository repository, ProviderRegistry providers) { this.repository = repository; this.providers = providers; }
    public List<ModelDescriptor> findAll() { return repository.findAll(); }
    public ModelDescriptor findById(String id) {
        return repository.findById(id).orElseThrow(() -> invalid("modelId", "Select an available model."));
    }
    public ModelDescriptor requireEnabled(String id) {
        var model = findById(id);
        if (!model.enabled()) throw invalid("modelId", "The selected model is disabled.");
        return model;
    }
    public ModelDescriptor requireCompatible(String providerId, String modelId) {
        var model = requireEnabled(modelId);
        if (!model.providerId().equals(providerId)) throw invalid("modelId", "The selected model does not belong to this provider.");
        return model;
    }
    public ModelDescriptor save(String id, ModelDescriptor model) {
        if (model == null || !id.equals(model.id())) throw invalid("id", "The path and model configuration ID must match.");
        identifier(model.id(), "id"); identifier(model.providerId(), "providerId");
        text(model.apiModelId(), 200, "apiModelId"); text(model.name(), 120, "name");
        if (model.description() != null && model.description().length() > 2000) throw invalid("description", "Use at most 2000 characters.");
        if (providers != null) providers.configuration(model.providerId());
        if (!CAPABILITIES.containsAll(model.declaredCapabilities())) throw invalid("capabilities", "Select supported capability names.");
        if (!PARAMETERS.containsAll(model.declaredParameters())) throw invalid("supportedParameters", "Select supported generation parameter names.");
        if (model.contextWindow() != null && model.contextWindow() <= 0) throw invalid("contextWindow", "Context window must be positive when known.");
        if (model.maxOutputTokens() != null && model.maxOutputTokens() <= 0) throw invalid("maxOutputTokens", "Output token limit must be positive when known.");
        if (model.contextWindow() != null && model.maxOutputTokens() != null && model.maxOutputTokens() > model.contextWindow()) throw invalid("maxOutputTokens", "Output token limit cannot exceed context window.");
        price(model.inputTokenPrice(), "inputTokenPrice"); price(model.outputTokenPrice(), "outputTokenPrice");
        if (model.inputTokenPrice() != null || model.outputTokenPrice() != null) {
            if (model.currency() == null || !model.currency().matches("[A-Z]{3}")) throw invalid("currency", "Pricing requires a three-letter uppercase currency.");
            if (model.pricingUnit() == null || model.pricingUnit() <= 0) throw invalid("pricingUnit", "Pricing requires a positive token unit.");
            if (model.pricingUpdatedAt() == null) throw invalid("pricingUpdatedAt", "Pricing requires a last-updated timestamp.");
        }
        if (model.declaredReasoningEfforts().size() > 20 || model.declaredReasoningEfforts().stream().anyMatch(e -> e.isBlank() || e.length() > 80)) throw invalid("reasoningEfforts", "Use at most 20 named reasoning controls.");
        if (!model.declaredReasoningEfforts().isEmpty() && !model.declaredParameters().contains("reasoningEffort")) throw invalid("reasoningEfforts", "Reasoning controls require the reasoningEffort parameter.");
        consistency(model.declaredCapabilities(), "TOOLS", model.toolCalling());
        consistency(model.declaredCapabilities(), "STRUCTURED_OUTPUT", model.structuredOutput());
        consistency(model.declaredCapabilities(), "VISION", model.vision());
        consistency(model.declaredCapabilities(), "EMBEDDING", model.embedding());
        return repository.save(model);
    }
    public ApplicationModelSettings settings() { return repository.settings(); }
    public ApplicationModelSettings saveSettings(ApplicationModelSettings settings) {
        if (settings == null) throw invalid("settings", "Provide model settings.");
        if (settings.defaultModelId() != null && !settings.defaultModelId().isBlank()) requireEnabled(settings.defaultModelId());
        validateTiers(settings.tierMappings());
        return repository.saveSettings(settings);
    }
    public void validateTiers(java.util.Map<String, String> mappings) { mappings.forEach((tier, model) -> { requireTier(tier); requireEnabled(model); }); }
    public static String requireTier(String tier) {
        var normalized = tier == null ? "" : tier.toUpperCase(Locale.ROOT);
        if (!TIERS.contains(normalized)) throw invalid("tier", "Select Economy, Balanced, or Advanced.");
        return normalized;
    }
    private static void consistency(Set<String> capabilities, String capability, Boolean supported) {
        if (capabilities.contains(capability) && !Boolean.TRUE.equals(supported)) throw invalid("capabilities", capability + " requires explicit supported metadata.");
    }
    private static void price(BigDecimal price, String field) { if (price != null && (price.signum() < 0 || price.precision() > 30 || price.scale() > 16)) throw invalid(field, "Use a nonnegative price with at most 16 decimal places."); }
    private static void identifier(String value, String field) { if (value == null || !value.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,79}")) throw invalid(field, "Use 1-80 letters, numbers, dots, underscores, or hyphens."); }
    private static void text(String value, int max, String field) { if (value == null || value.isBlank() || value.length() > max) throw invalid(field, "Enter a value of at most " + max + " characters."); }
    private static InvalidConfigurationException invalid(String field, String message) { return new InvalidConfigurationException(field, message); }
}
