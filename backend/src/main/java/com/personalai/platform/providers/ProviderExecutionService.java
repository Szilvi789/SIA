package com.personalai.platform.providers;

import com.personalai.platform.agents.Agent;
import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.models.ModelResolver;
import com.personalai.platform.models.ModelSelection;
import com.personalai.platform.models.ResolvedModel;
import com.personalai.platform.usage.UsageService;
import com.personalai.platform.privacy.CloudDataSharingPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/** Shared runtime resolution and accounting; business services retain their instruction/knowledge orchestration. */
@Service
public class ProviderExecutionService {
    private final ModelResolver resolver;
    private final ProviderRegistry providers;
    private final UsageService usage;
    private final CloudDataSharingPolicy dataSharing;
    public ProviderExecutionService(ModelResolver resolver, ProviderRegistry providers, UsageService usage) {
        this(resolver, providers, usage, CloudDataSharingPolicy.denyExternalByDefault(providers));
    }
    @Autowired
    public ProviderExecutionService(ModelResolver resolver, ProviderRegistry providers, UsageService usage, CloudDataSharingPolicy dataSharing) {
        this.resolver = resolver; this.providers = providers; this.usage = usage; this.dataSharing = dataSharing;
    }
    public ExecutionResult execute(Agent agent, ModelSelection selection, Set<String> requiredCapabilities,
            String workflow, Function<ResolvedModel, ProviderRequest> requestFactory) {
        return execute(agent, selection, requiredCapabilities, workflow, requestFactory, content -> { });
    }
    public ExecutionResult execute(Agent agent, ModelSelection selection, Set<String> requiredCapabilities,
            String workflow, Function<ResolvedModel, ProviderRequest> requestFactory, Consumer<String> outputValidator) {
        selection = selection == null ? ModelSelection.defaults() : selection;
        ResolvedModel resolved;
        try { resolved = resolver.resolve(agent, selection, requiredCapabilities); }
        catch (RuntimeException exception) {
            var context = resolver.selectionContext(agent, selection);
            usage.recordRejected(agent.id(), workflow, selection.conversationId(), context.providerId(), context.modelId(), context.mock(),
                    publicError(exception));
            throw exception;
        }
        try { return executeResolved(agent, selection, workflow, requestFactory, outputValidator, resolved); }
        catch (ProviderUnavailableException unavailable) {
            var fallback = resolver.resolveFallback(agent, selection, requiredCapabilities, resolved);
            return executeResolved(agent, selection, workflow, requestFactory, outputValidator, fallback);
        }
    }
    private ExecutionResult executeResolved(Agent agent, ModelSelection selection, String workflow,
            Function<ResolvedModel, ProviderRequest> requestFactory, Consumer<String> outputValidator, ResolvedModel resolved) {
        ProviderResult result = null;
        boolean mock = providers.configuration(resolved.model().providerId()).mock();
        try {
            var provider = providers.require(resolved.model().providerId());
            var original = requestFactory.apply(resolved);
            var allowedTools = resolved.parameters().tools();
            var tools = original.availableTools().stream().filter(tool -> allowedTools == null || allowedTools.contains(tool.id())).toList();
            var request = new ProviderRequest(agent, resolved.model(), original.message(), original.instructions(),
                    original.knowledgeContext(), tools, original.writingContext(), resolved.parameters());
            dataSharing.validate(provider, request, selection.dataSharingConsent());
            try { result = provider.generateResult(request); }
            catch (ProviderUnavailableException exception) { throw exception; }
            catch (RuntimeException exception) { throw new InvalidConfigurationException("The provider could not complete generation. Check the server-side adapter configuration."); }
            if (result == null || result.content() == null) throw new InvalidConfigurationException("The provider returned an invalid response.");
            outputValidator.accept(result.content());
            var record = usage.record(agent.id(), workflow, selection.conversationId(), resolved.model(), mock, result.usage(), "SUCCESS", null);
            return new ExecutionResult(result.content(), new ExecutionMetadata(resolved.model().providerId(), resolved.model().id(),
                    resolved.model().apiModelId(), resolved.source(), resolved.fallbackUsed(), mock, record.id()));
        } catch (RuntimeException exception) {
            usage.record(agent.id(), workflow, selection.conversationId(), resolved.model(), mock, result == null ? null : result.usage(), "FAILED", publicError(exception));
            throw exception;
        }
    }
    private static String publicError(RuntimeException exception) {
        return exception instanceof InvalidConfigurationException ? exception.getMessage() : "Provider execution failed.";
    }
}
