package com.personalai.platform;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.personalai.platform.knowledge.KnowledgeAssociation;
import com.personalai.platform.knowledge.KnowledgeFormat;
import com.personalai.platform.knowledge.KnowledgeRepository;
import com.personalai.platform.knowledge.KnowledgeSource;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class PlatformApiTest {
    private static ConfigurableApplicationContext application;
    private static MockMvc mvc;

    @BeforeAll
    static void startApplication() {
        // Spring 7's SpringExtension requires JUnit 6. Explicit lifecycle keeps these full-context tests on JUnit 5.
        application = SpringApplication.run(PersonalAiAgentPlatformApplication.class,
                "--server.port=0", "--server.address=127.0.0.1");
        mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) application).build();
    }

    @AfterAll
    static void stopApplication() {
        if (application != null) {
            application.close();
        }
    }

    @Test
    void reportsHealthyMockMode() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.provider").value("mock"));
    }

    @Test
    void listsTheThreeCanonicalAgentsAndAvailableModels() throws Exception {
        mvc.perform(get("/api/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value("coding-agent"))
                .andExpect(jsonPath("$[0].enabledTools", hasSize(0)));
        mvc.perform(get("/api/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value("mock-balanced"))
                .andExpect(jsonPath("$[0].providerId").value("mock"));
    }

    @Test
    void findsAnAgentAndReturnsASanitized404ForMissingAgents() throws Exception {
        mvc.perform(get("/api/agents/translation-agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("translation-agent"));
        mvc.perform(get("/api/agents/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Agent was not found."))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void executesChatUsingTheSelectedAgentAndModel() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("""
                {"agentId":"email-assistant","modelId":"mock-precise","message":"Invite Alex to a design review"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.agentId").value("email-assistant"))
                .andExpect(jsonPath("$.modelId").value("mock-precise"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content", containsString("No AI model was called.")))
                .andExpect(jsonPath("$.content", containsString("Invite Alex to a design review")));
    }

    @Test
    void savesConfigurationAndUsesItInTheNextExecution() throws Exception {
        mvc.perform(put("/api/agents/documentation-agent")
                        .contentType(MediaType.APPLICATION_JSON).content(validUpdate("mock", "mock-precise", 0.1, "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("documentation-agent"))
                .andExpect(jsonPath("$.name").value("API writer"))
                .andExpect(jsonPath("$.modelId").value("mock-precise"));
        mvc.perform(get("/api/agents/documentation-agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemPrompt").value("Document APIs with examples."));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("""
                {"agentId":"documentation-agent","modelId":"mock-precise","message":"Describe the health endpoint"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", containsString("Document APIs with examples.")))
                .andExpect(jsonPath("$.content", containsString("temperature 0.1")));
    }

    @Test
    void rejectsBlankAndMissingChatFields() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"coding-agent\",\"modelId\":\"mock-balanced\",\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").isNotEmpty());
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.agentId").isNotEmpty())
                .andExpect(jsonPath("$.errors.modelId").isNotEmpty());
    }

    @Test
    void rejectsOversizedChatMessages() throws Exception {
        var json = "{\"agentId\":\"coding-agent\",\"modelId\":\"mock-balanced\",\"message\":\"" + "x".repeat(12_001) + "\"}";
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").isNotEmpty());
    }

    @Test
    void rejectsUnknownChatModelsAndAgents() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("""
                {"agentId":"coding-agent","modelId":"unavailable","message":"Hello"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("""
                {"agentId":"missing","modelId":"mock-balanced","message":"Hello"}
                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnsupportedProviderAndModelConfiguration() throws Exception {
        mvc.perform(put("/api/agents/coding-agent").contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdate("openai", "mock-balanced", 0.3, "[]")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/agents/coding-agent").contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdate("mock", "unavailable", 0.3, "[]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOutOfRangeParametersAndUnavailableTools() throws Exception {
        mvc.perform(put("/api/agents/coding-agent").contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdate("mock", "mock-balanced", 2.1, "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.temperature").isNotEmpty());
        mvc.perform(put("/api/agents/coding-agent").contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdate("mock", "mock-balanced", 0.3, "[\"terminal\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.enabledTools").isNotEmpty());
    }

    @Test
    void rejectsMalformedJsonWithoutLeakingParserDetails() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The request body must contain valid JSON with the expected field types."))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void savesModuleAssignmentsAndPreservesThemForLegacyUpdates() throws Exception {
        application.getBean(KnowledgeRepository.class).save(new KnowledgeSource(
                "agent-notes", "notes.md", KnowledgeFormat.MARKDOWN, "notes-content", 128,
                List.of(new KnowledgeAssociation(KnowledgeAssociation.Scope.AGENT, "translation-agent"))));
        var assigned = """
                {"name":"Translation Agent","description":"Translate with care.",
                 "systemPrompt":"Preserve meaning.","providerId":"mock","modelId":"mock-balanced",
                 "temperature":0.2,"maxOutputTokens":1024,"enabledTools":[],
                 "ruleIds":["translation-rules","email-style"],"knowledgeSourceIds":["agent-notes"]}
                """;
        mvc.perform(put("/api/agents/translation-agent").contentType(MediaType.APPLICATION_JSON).content(assigned))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleIds", hasSize(2)))
                .andExpect(jsonPath("$.ruleIds[0]").value("translation-rules"))
                .andExpect(jsonPath("$.knowledgeSourceIds[0]").value("agent-notes"));

        // The original request shape must not erase assignments configured by a newer client.
        mvc.perform(put("/api/agents/translation-agent").contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdate("mock", "mock-precise", 0.3, "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleIds", hasSize(2)))
                .andExpect(jsonPath("$.knowledgeSourceIds[0]").value("agent-notes"));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("""
                {"agentId":"translation-agent","modelId":"mock-balanced","message":"A short message"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", containsString("No AI model was called.")));

        mvc.perform(put("/api/agents/translation-agent").contentType(MediaType.APPLICATION_JSON)
                        .content(assigned.replace("[\"translation-rules\",\"email-style\"]", "[]")
                                .replace("[\"agent-notes\"]", "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleIds", hasSize(0)))
                .andExpect(jsonPath("$.knowledgeSourceIds", hasSize(0)));
    }

    @Test
    void reportsInvalidModuleAssignmentsWithoutChangingConfiguration() throws Exception {
        var original = application.getBean(com.personalai.platform.agents.AgentService.class)
                .findById("email-assistant");
        var body = validUpdate("mock", "mock-balanced", 0.3, "[]").stripTrailing();
        var prefix = body.substring(0, body.length() - 1);
        mvc.perform(put("/api/agents/email-assistant").contentType(MediaType.APPLICATION_JSON)
                        .content(prefix + ",\"ruleIds\":[\"global-instructions\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ruleIds").isNotEmpty());
        mvc.perform(put("/api/agents/email-assistant").contentType(MediaType.APPLICATION_JSON)
                        .content(prefix + ",\"knowledgeSourceIds\":[\"unknown-source\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.knowledgeSourceIds").isNotEmpty());
        org.assertj.core.api.Assertions.assertThat(application.getBean(com.personalai.platform.agents.AgentService.class)
                .findById("email-assistant")).isEqualTo(original);
    }

    private String validUpdate(String providerId, String modelId, double temperature, String toolsJson) {
        return """
                {"name":"API writer","description":"Technical writing helper",
                 "systemPrompt":"Document APIs with examples.","providerId":"%s","modelId":"%s",
                 "temperature":%s,"maxOutputTokens":1024,"enabledTools":%s}
                """.formatted(providerId, modelId, temperature, toolsJson);
    }
}
