package com.personalai.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Exercises the actual controllers, saved agent defaults, resolver, execution, and usage wiring together. */
class ModelRuntimeApiTest {
    private static ConfigurableApplicationContext application;
    private static MockMvc mvc;

    @BeforeAll
    static void startApplication() {
        application = SpringApplication.run(PersonalAiAgentPlatformApplication.class,
                "--server.port=0", "--server.address=127.0.0.1");
        mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) application).build();
    }

    @AfterAll
    static void stopApplication() { if (application != null) application.close(); }

    @Test
    void independentSavedDefaultsDriveChatAndWritingWhileOverridesRemainTemporary() throws Exception {
        save("coding-agent", "\"mock-precise\"", "null");
        save("documentation-agent", "\"mock-creative\"", "null");
        save("writing-agent", "\"mock-precise\"", "null");

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("""
                {"agentId":"coding-agent","message":"Review a method","conversationId":"review-1"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("mock-precise"))
                .andExpect(jsonPath("$.providerId").value("mock"))
                .andExpect(jsonPath("$.source").isNotEmpty())
                .andExpect(jsonPath("$.execution.usageId").isNotEmpty());
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("""
                {"agentId":"documentation-agent","message":"Document a method"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("mock-creative"));
        mvc.perform(post("/api/writing/generate").contentType(MediaType.APPLICATION_JSON).content("""
                {"workflow":"GENERAL","input":"Write a useful paragraph"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("mock-precise"))
                .andExpect(jsonPath("$.providerId").value("mock"))
                .andExpect(jsonPath("$.execution.usageId").isNotEmpty());

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("""
                {"agentId":"coding-agent","message":"Explore a solution","overrideModelId":"mock-creative"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("mock-creative"));
        mvc.perform(post("/api/writing/generate").contentType(MediaType.APPLICATION_JSON).content("""
                {"workflow":"GENERAL","input":"Write a paragraph","modelId":"mock-creative","overrideModelId":"mock-balanced"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("mock-creative"));
        mvc.perform(get("/api/agents/coding-agent"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modelId").value("mock-precise"));
        mvc.perform(get("/api/agents/writing-agent"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modelId").value("mock-precise"));
    }

    @Test
    void tierDefaultsAndMappingsSurviveLegacyUpdatesAndAgentAliases() throws Exception {
        var settings = """
                {"defaultTier":"economy","tierMappings":{"economy":"mock-precise","advanced":"mock-creative"},"allowFallback":false}
                """;
        save("email-assistant", "null", settings);
        mvc.perform(get("/api/agents/writing-agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelSettings.defaultTier").value("ECONOMY"));
        mvc.perform(post("/api/writing/generate").contentType(MediaType.APPLICATION_JSON).content("""
                {"input":"Write a clear message"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modelId").value("mock-precise"));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("""
                {"agentId":"translation-agent","message":"Explore an idea","tier":"advanced"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modelId").value("mock-creative"));

        // An older client omits modelSettings entirely when editing the same shared writing agent.
        var legacy = update("\"mock-balanced\"", "null").replace(",\"modelSettings\":null", "");
        mvc.perform(put("/api/agents/translation-agent").contentType(MediaType.APPLICATION_JSON).content(legacy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelSettings.defaultTier").value("ECONOMY"))
                .andExpect(jsonPath("$.modelSettings.tierMappings.ADVANCED").value("mock-creative"));
        mvc.perform(get("/api/agents/email-assistant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelSettings.tierMappings.ECONOMY").value("mock-precise"));
    }

    @Test
    void writingAcceptsAnEmptyDefaultSelectionAndDispatchesConfiguredModelIdsWhileRejectingUnsupportedParameters() throws Exception {
        save("writing-agent", "\"mock-precise\"", "{\"tierMappings\":{}}");
        mvc.perform(post("/api/writing/generate").contentType(MediaType.APPLICATION_JSON).content("""
                {"workflow":"GENERAL","input":"Write a paragraph","modelId":""}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modelId").value("mock-precise"));
        mvc.perform(put("/api/models/configured-writing").contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"configured-writing","name":"Configured writing","providerId":"mock","description":"Configured model regression",
                 "apiModelId":"independent-api-name","enabled":true,"capabilities":["TEXT","STRUCTURED_OUTPUT"],
                 "maxOutputTokens":2048,"supportedParameters":["maxOutputTokens"],"structuredOutput":true,
                 "toolCalling":false,"vision":false,"embedding":false}
                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/writing/generate").contentType(MediaType.APPLICATION_JSON).content("""
                {"workflow":"GENERAL","input":"Write a paragraph","modelId":"configured-writing"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("configured-writing"))
                .andExpect(jsonPath("$.execution.apiModelId").value("independent-api-name"));
        mvc.perform(post("/api/writing/generate").contentType(MediaType.APPLICATION_JSON).content("""
                {"workflow":"GENERAL","input":"Write a paragraph","modelId":"configured-writing","parameters":{"temperature":0.4}}
                """))
                .andExpect(status().isBadRequest());
    }

    private void save(String id, String modelJson, String settingsJson) throws Exception {
        mvc.perform(put("/api/agents/" + id).contentType(MediaType.APPLICATION_JSON).content(update(modelJson, settingsJson)))
                .andExpect(status().isOk());
    }

    private String update(String modelJson, String settingsJson) {
        return """
                {"name":"Configured agent","description":"Independent model defaults","systemPrompt":"Follow the task carefully.",
                "providerId":"mock","modelId":%s,"temperature":0.3,"maxOutputTokens":1024,"enabledTools":[],"modelSettings":%s}
                """.formatted(modelJson, settingsJson);
    }
}

