package com.personalai.platform.models;

import com.personalai.platform.agents.Agent;
import com.personalai.platform.agents.AgentIds;
import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.providers.ProviderRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ModelResolver {
    private final ModelCatalog models;
    private final ProviderRegistry providers;
    public ModelResolver(ModelCatalog models, ProviderRegistry providers) { this.models = models; this.providers = providers; }

    public ResolvedModel resolve(Agent agent, ModelSelection selection, Set<String> requiredCapabilities) {
        selection = selection == null ? ModelSelection.defaults() : selection;
        validateSelection(selection);
        var settings = settings(agent);
        var choice = choose(agent, selection, settings);
        var selected = models.requireEnabled(choice.modelId());
        var model = selected;
        boolean fallback = false;
        if (!providers.available(model.providerId())) {
            if (!settings.allowFallback() || Boolean.FALSE.equals(selection.allowFallback()) || !present(settings.fallbackModelId())) {
                providers.require(model.providerId());
            }
            model = models.requireEnabled(settings.fallbackModelId());
            if (!noMoreExpensive(selected, model)) throw invalid("fallbackModelId", "Fallback pricing is unknown or higher. Select the fallback explicitly instead.");
            providers.require(model.providerId());
            fallback = true;
        }
        var parameters = parameters(agent, settings, selection.parameters(), model);
        validateCapabilities(model, requiredCapabilities, parameters);
        validateGuardrails(settings, model);
        return new ResolvedModel(model, parameters, choice.source(), fallback, selected.id());
    }

    public ResolvedModel resolveFallback(Agent agent, ModelSelection selection, Set<String> requiredCapabilities, ResolvedModel original) {
        selection = selection == null ? ModelSelection.defaults() : selection;
        var settings = settings(agent);
        if (original.fallbackUsed() || !settings.allowFallback() || Boolean.FALSE.equals(selection.allowFallback())
                || !present(settings.fallbackModelId()) || original.model().id().equals(settings.fallbackModelId())) {
            throw new com.personalai.platform.providers.ProviderUnavailableException();
        }
        var fallback = models.requireEnabled(settings.fallbackModelId());
        if (!noMoreExpensive(original.model(), fallback)) throw invalid("fallbackModelId", "Fallback pricing is unknown or higher. Select the fallback explicitly instead.");
        providers.require(fallback.providerId());
        var parameters = parameters(agent, settings, selection.parameters(), fallback);
        validateCapabilities(fallback, requiredCapabilities, parameters);
        validateGuardrails(settings, fallback);
        return new ResolvedModel(fallback, parameters, original.source(), true, original.selectedModelId());
    }

    /** Best-effort identifiers for rejected requests; never substitutes the agent's provider for an unknown model. */
    public SelectionContext selectionContext(Agent agent, ModelSelection selection) {
        selection = selection == null ? ModelSelection.defaults() : selection;
        String modelId;
        try { modelId = choose(agent, selection, settings(agent)).modelId(); }
        catch (RuntimeException ignored) { return new SelectionContext(null, null, false); }
        try {
            var model = models.findById(modelId);
            return new SelectionContext(model.providerId(), modelId, providers.configuration(model.providerId()).mock());
        } catch (RuntimeException ignored) { return new SelectionContext(null, modelId, false); }
    }
    public record SelectionContext(String providerId, String modelId, boolean mock) { }

    /** Saving a disconnected provider is allowed; execution readiness is checked at request time. */
    public void validateAgentSettings(Agent agent) {
        var settings = settings(agent);
        providers.configuration(agent.providerId());
        if (present(agent.modelId())) models.requireCompatible(agent.providerId(), agent.modelId());
        models.validateTiers(settings.tierMappings());
        if (present(settings.defaultTier())) ModelCatalog.requireTier(settings.defaultTier());
        var choice = choose(agent, ModelSelection.defaults(), settings);
        var model = models.requireEnabled(choice.modelId());
        if ("AGENT_DEFAULT".equals(choice.source()) && !model.providerId().equals(agent.providerId())) {
            throw invalid("modelId", "The selected model does not belong to this provider.");
        }
        var required = "writing-agent".equals(AgentIds.canonical(agent.id())) ? Set.of("TEXT", "STRUCTURED_OUTPUT") : Set.of("TEXT");
        validateCapabilities(model, required, parameters(agent, settings, null, model));
        for (var tierModel : settings.tierMappings().values()) {
            var tier = models.requireEnabled(tierModel);
            validateCapabilities(tier, required, parameters(agent, settings, null, tier));
        }
        if (present(settings.fallbackModelId())) {
            var fallback = models.requireEnabled(settings.fallbackModelId());
            validateCapabilities(fallback, required, parameters(agent, settings, null, fallback));
        } else if (settings.allowFallback()) throw invalid("fallbackModelId", "Choose a fallback model before allowing fallback.");
        if (settings.maxEstimatedCost() != null && (settings.maxEstimatedCost().signum() < 0 || settings.maxEstimatedCost().precision() > 30 || settings.maxEstimatedCost().scale() > 16)) throw invalid("maxEstimatedCost", "Use a nonnegative cost limit with at most 16 decimal places.");
        if (settings.maxEstimatedCost() != null && (settings.currency() == null || !settings.currency().matches("[A-Z]{3}"))) throw invalid("currency", "A cost guardrail requires a three-letter uppercase currency.");
    }

    private Choice choose(Agent agent, ModelSelection selection, ModelSettings settings) {
        if (present(selection.modelId())) return new Choice(selection.modelId(), "REQUEST_MODEL");
        if (present(selection.tier())) return new Choice(tier(selection.tier(), settings), "REQUEST_TIER");
        if (present(selection.overrideModelId())) return new Choice(selection.overrideModelId(), "TASK_MODEL");
        if (present(selection.overrideTier())) return new Choice(tier(selection.overrideTier(), settings), "TASK_TIER");
        if (present(settings.defaultTier())) return new Choice(tier(settings.defaultTier(), settings), "AGENT_TIER");
        if (present(agent.modelId())) return new Choice(agent.modelId(), "AGENT_DEFAULT");
        if (present(models.settings().defaultModelId())) return new Choice(models.settings().defaultModelId(), "APPLICATION_DEFAULT");
        throw invalid("modelId", "Configure an agent or application default model.");
    }
    private String tier(String tier, ModelSettings settings) {
        var normalized = ModelCatalog.requireTier(tier);
        var model = settings.tierMappings().getOrDefault(normalized, models.settings().tierMappings().get(normalized));
        if (!present(model)) throw invalid("tier", "Configure a model for the " + normalized + " tier before using it.");
        return model;
    }
    private GenerationParameters parameters(Agent agent, ModelSettings settings, GenerationParameters override, ModelDescriptor model) {
        var supported = model.declaredParameters();
        var base = new GenerationParameters(supported.contains("maxOutputTokens") ? agent.maxOutputTokens() : null,
                supported.contains("temperature") ? agent.temperature() : null, null, null, null,
                agent.enabledTools().isEmpty() ? null : agent.enabledTools());
        var result = base.merge(settings.parameters()).merge(override);
        validateParameters(model, result, agent.enabledTools());
        return result;
    }
    public void validateParameters(ModelDescriptor model, GenerationParameters parameters, List<String> allowedTools) {
        check(model, "maxOutputTokens", parameters.maxOutputTokens()); check(model, "temperature", parameters.temperature());
        check(model, "topP", parameters.topP()); check(model, "reasoningEffort", parameters.reasoningEffort());
        check(model, "timeoutMs", parameters.timeoutMs());
        if (parameters.tools() != null && !parameters.tools().isEmpty()) check(model, "tools", parameters.tools());
        if (parameters.maxOutputTokens() != null && (parameters.maxOutputTokens() <= 0 || parameters.maxOutputTokens() > 1_000_000
                || (model.maxOutputTokens() != null && parameters.maxOutputTokens() > model.maxOutputTokens()))) throw invalid("maxOutputTokens", "The output token limit exceeds this model's configured range.");
        if (parameters.temperature() != null && (!Double.isFinite(parameters.temperature()) || parameters.temperature() < 0 || parameters.temperature() > 2)) throw invalid("temperature", "Temperature must be between 0 and 2 for this adapter contract.");
        if (parameters.topP() != null && (!Double.isFinite(parameters.topP()) || parameters.topP() <= 0 || parameters.topP() > 1)) throw invalid("topP", "Top-p must be greater than zero and at most one.");
        if (parameters.timeoutMs() != null && (parameters.timeoutMs() < 100 || parameters.timeoutMs() > 300_000)) throw invalid("timeoutMs", "Timeout must be between 100 and 300000 ms.");
        if (parameters.reasoningEffort() != null && !model.declaredReasoningEfforts().contains(parameters.reasoningEffort())) throw invalid("reasoningEffort", "Select a reasoning control registered for this model.");
        if (parameters.tools() != null && (parameters.tools().size() > 50 || !allowedTools.containsAll(parameters.tools()))) throw invalid("tools", "Runtime tool permissions cannot exceed the agent's enabled tools.");
    }
    private void validateCapabilities(ModelDescriptor model, Set<String> required, GenerationParameters parameters) {
        if (!model.declaredCapabilities().containsAll(required)) throw invalid("modelId", "This model lacks required workflow capabilities: " + String.join(", ", required) + ".");
        if (required.contains("STRUCTURED_OUTPUT") && !Boolean.TRUE.equals(model.structuredOutput())) throw invalid("modelId", "This workflow requires structured output support.");
        if (parameters.tools() != null && !parameters.tools().isEmpty() && !"mock".equals(model.providerId()) && !Boolean.TRUE.equals(model.toolCalling())) throw invalid("tools", "This model does not support tool calling.");
    }
    private void validateGuardrails(ModelSettings settings, ModelDescriptor model) {
        if ("mock".equals(model.providerId())) return;
        if (settings.maxEstimatedCost() != null && !settings.allowUnknownCost()) throw invalid("maxEstimatedCost", "A reliable pre-request cost is unavailable for this adapter. Allow unknown estimates explicitly or use a different model.");
        if (settings.maxEstimatedCost() != null && model.currency() != null && !settings.currency().equals(model.currency())) throw invalid("currency", "The cost guardrail and model pricing currencies differ.");
    }
    private boolean noMoreExpensive(ModelDescriptor selected, ModelDescriptor fallback) {
        if ("mock".equals(fallback.providerId())) return true;
        if ("mock".equals(selected.providerId())) return false;
        if (selected.inputTokenPrice() == null || selected.outputTokenPrice() == null || fallback.inputTokenPrice() == null || fallback.outputTokenPrice() == null
                || selected.pricingUnit() == null || fallback.pricingUnit() == null
                || selected.pricingUpdatedAt() == null || fallback.pricingUpdatedAt() == null
                || selected.currency() == null || !selected.currency().equals(fallback.currency())) return false;
        var leftUnit = BigDecimal.valueOf(selected.pricingUnit()); var rightUnit = BigDecimal.valueOf(fallback.pricingUnit());
        return fallback.inputTokenPrice().multiply(leftUnit).compareTo(selected.inputTokenPrice().multiply(rightUnit)) <= 0
                && fallback.outputTokenPrice().multiply(leftUnit).compareTo(selected.outputTokenPrice().multiply(rightUnit)) <= 0;
    }
    private static void check(ModelDescriptor model, String parameter, Object value) {
        if (value != null && !model.declaredParameters().contains(parameter)) throw invalid(parameter, "The selected model does not support " + parameter + ".");
    }
    private static void validateSelection(ModelSelection selection) {
        if (present(selection.modelId()) && present(selection.tier())) throw invalid("modelId", "Choose a model or tier for the current request.");
        if (present(selection.overrideModelId()) && present(selection.overrideTier())) throw invalid("overrideModelId", "Choose a model or tier for the task override.");
        for (var id : new String[] { selection.modelId(), selection.overrideModelId() }) if (id != null && id.length() > 80) throw invalid("modelId", "Use at most 80 characters for a model ID.");
        if (selection.conversationId() != null && selection.conversationId().length() > 120) throw invalid("conversationId", "Use at most 120 characters for the conversation ID.");
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static ModelSettings settings(Agent agent) { return agent.modelSettings() == null ? ModelSettings.defaults() : agent.modelSettings(); }
    private static InvalidConfigurationException invalid(String field, String message) { return new InvalidConfigurationException(field, message); }
    private record Choice(String modelId, String source) { }
}
