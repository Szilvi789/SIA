package com.personalai.platform.writing;

import com.personalai.platform.agents.AgentService;
import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.knowledge.KnowledgeQuery;
import com.personalai.platform.knowledge.KnowledgeRetriever;
import com.personalai.platform.knowledge.KnowledgeService;
import com.personalai.platform.providers.ProviderExecutionService;
import com.personalai.platform.providers.ProviderRequest;
import com.personalai.platform.rules.ResolvedInstructions;
import com.personalai.platform.tools.ToolRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class WritingService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final AgentService agents;
    private final ProviderExecutionService execution;
    private final WritingInstructionComposer composer;
    private final LanguageDetector detector;
    private final KnowledgeService knowledge;
    private final KnowledgeRetriever retriever;
    private final ToolRegistry tools;
    private final Clock clock;

    public WritingService(AgentService agents, ProviderExecutionService execution,
            WritingInstructionComposer composer, LanguageDetector detector, KnowledgeService knowledge,
            KnowledgeRetriever retriever, ToolRegistry tools, Clock clock) {
        this.agents = agents; this.execution = execution; this.composer = composer;
        this.detector = detector; this.knowledge = knowledge; this.retriever = retriever; this.tools = tools; this.clock = clock;
    }
    public ResolvedInstructions previewInstructions(WritingRequest request) {
        requireWorkflow(request);
        return composer.compose(agents.findById("writing-agent"), request);
    }
    public WritingResult generate(WritingRequest request) {
        requireWorkflow(request);
        var agent = agents.findById("writing-agent");
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
        var message = input;
        var availableTools = tools.definitionsFor(agent.enabledTools());
        var result = execution.execute(agent, request.selection(), Set.of("TEXT", "STRUCTURED_OUTPUT"),
                request.workflow().name(), resolved -> new ProviderRequest(agent, resolved.model(), message, instructions,
                        context, availableTools, new WritingContext(request.workflow(), request.preferences(), request.email(), detected),
                        resolved.parameters()), this::parse);
        var parsed = parse(result.content());
        String body = parsed.content();
        String subject = parsed.subject();
        if (request.workflow() == WritingWorkflow.EMAIL) {
            if (request.email().generationPart() == EmailComposition.GenerationPart.SUBJECT) body = request.email().body();
            if (request.email().generationPart() == EmailComposition.GenerationPart.BODY) subject = request.email().subject();
        } else subject = "";
        return new WritingResult(UUID.randomUUID().toString(), parsed.title(), body, subject, request.workflow(), detected,
                request.preferences().outputLanguage(), request.preferences().translationMode(), result.metadata().mock(),
                instructions.appliedRuleIds(), Instant.now(clock), result.metadata().providerId(), result.metadata().modelId(),
                result.metadata().source(), result.metadata());
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
