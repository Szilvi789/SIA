package com.personalai.platform.writing;

import com.personalai.platform.agents.AgentService;
import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.knowledge.KnowledgeQuery;
import com.personalai.platform.knowledge.KnowledgeRetriever;
import com.personalai.platform.knowledge.KnowledgeService;
import com.personalai.platform.models.ModelCatalog;
import com.personalai.platform.providers.ProviderRegistry;
import com.personalai.platform.providers.ProviderRequest;
import com.personalai.platform.rules.ResolvedInstructions;
import com.personalai.platform.tools.ToolRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class WritingService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final AgentService agents;
    private final ModelCatalog models;
    private final ProviderRegistry providers;
    private final WritingInstructionComposer composer;
    private final LanguageDetector detector;
    private final KnowledgeService knowledge;
    private final KnowledgeRetriever retriever;
    private final ToolRegistry tools;
    private final Clock clock;

    public WritingService(AgentService agents, ModelCatalog models, ProviderRegistry providers,
            WritingInstructionComposer composer, LanguageDetector detector, KnowledgeService knowledge,
            KnowledgeRetriever retriever, ToolRegistry tools, Clock clock) {
        this.agents = agents; this.models = models; this.providers = providers; this.composer = composer;
        this.detector = detector; this.knowledge = knowledge; this.retriever = retriever; this.tools = tools; this.clock = clock;
    }
    public ResolvedInstructions previewInstructions(WritingRequest request) {
        requireWorkflow(request);
        return composer.compose(agents.findById("writing-agent"), request);
    }
    public WritingResult generate(WritingRequest request) {
        requireWorkflow(request);
        var agent = agents.findById("writing-agent");
        var model = models.requireCompatible(agent.providerId(), request.modelId());
        var instructions = composer.compose(agent, request);
        String input = request.input().strip();
        if (input.isBlank() && request.workflow() == WritingWorkflow.EMAIL) input = request.email().body().strip();
        if (input.isBlank()) throw new InvalidConfigurationException("input", "Enter text or instructions before generating.");
        if (input.length() > KnowledgeQuery.MAX_QUERY_CHARACTERS) {
            throw new InvalidConfigurationException("input", "Generation input must contain at most 12000 characters.");
        }
        var detected = request.preferences().inputLanguage() == WritingPreferences.InputLanguage.AUTO
                ? detector.detect(input)
                : new DetectedLanguage(DetectedLanguage.Language.valueOf(request.preferences().inputLanguage().name()), null, false, "User-selected input language; not automatically detected.");
        knowledge.validateAssignments(agent.id(), agent.knowledgeSourceIds());
        var context = retriever.retrieve(new KnowledgeQuery(agent.id(), agent.knowledgeSourceIds(), input));
        if (context.stream().anyMatch(excerpt -> !agent.knowledgeSourceIds().contains(excerpt.sourceId()))) {
            throw new InvalidConfigurationException("Knowledge retrieval returned an unassigned source.");
        }
        var response = providers.require(agent.providerId()).generate(new ProviderRequest(agent, model, input, instructions,
                context, tools.definitionsFor(agent.enabledTools()), new WritingContext(request.workflow(), request.preferences(), request.email(), detected)));
        var parsed = parse(response);
        String body = parsed.content();
        String subject = parsed.subject();
        if (request.workflow() == WritingWorkflow.EMAIL) {
            if (request.email().generationPart() == EmailComposition.GenerationPart.SUBJECT) body = request.email().body();
            if (request.email().generationPart() == EmailComposition.GenerationPart.BODY) subject = request.email().subject();
        } else subject = "";
        return new WritingResult(UUID.randomUUID().toString(), parsed.title(), body, subject, request.workflow(), detected,
                request.preferences().outputLanguage(), request.preferences().translationMode(), "mock".equals(agent.providerId()),
                instructions.appliedRuleIds(), Instant.now(clock));
    }
    private static void requireWorkflow(WritingRequest request) {
        if (!request.workflow().supported()) throw new InvalidConfigurationException("workflow", "This workflow is planned for Phase 2.");
    }
    private GeneratedText parse(String raw) {
        if (raw == null || raw.length() > 200000) throw new InvalidConfigurationException("The provider returned an invalid writing response.");
        try {
            var node = JSON.readTree(raw);
            if (!node.isObject() || !node.path("title").isString() || !node.path("content").isString() || !node.path("subject").isString()) {
                throw new InvalidConfigurationException("The provider must return structured title, content, and subject strings.");
            }
            return new GeneratedText(WritingValidation.required(node.path("title").asString(), 200, "title"),
                    WritingValidation.required(node.path("content").asString(), 65000, "content"),
                    WritingValidation.text(node.path("subject").asString(), 500, "subject"));
        } catch (InvalidConfigurationException exception) { throw exception; }
        catch (RuntimeException exception) { throw new InvalidConfigurationException("The provider returned malformed writing output."); }
    }
    private record GeneratedText(String title, String content, String subject) { }
}
