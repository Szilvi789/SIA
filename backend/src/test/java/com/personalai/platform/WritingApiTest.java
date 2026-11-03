package com.personalai.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.personalai.platform.rules.RuleService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

class WritingApiTest {
    private static ConfigurableApplicationContext application;
    private static MockMvc mvc;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @BeforeAll
    static void startApplication() {
        // Keep the established explicit lifecycle on JUnit 5, without Spring 7's JUnit 6 extension.
        application = SpringApplication.run(PersonalAiAgentPlatformApplication.class,
                "--server.port=0", "--server.address=127.0.0.1");
        mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) application).build();
    }
    @AfterAll
    static void stopApplication() { if (application != null) application.close(); }

    @Test
    void exposesCanonicalWritingWorkspaceAndCompatibleLegacyAgentRoutes() throws Exception {
        mvc.perform(get("/api/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.id == 'writing-agent')]", hasSize(1)));
        for (String id : List.of("writing-agent", "translation-agent", "email-assistant")) {
            mvc.perform(get("/api/agents/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.name").value("Writing & Communication Agent"));
        }
        mvc.perform(get("/api/writing/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflows", hasSize(7)))
                .andExpect(jsonPath("$.workflows[?(@.id == 'EMAIL')].supported", org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.workflows[?(@.id == 'CV')].supported", org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.capabilities.generation").value("MOCK"))
                .andExpect(jsonPath("$.capabilities.sending").value(false))
                .andExpect(jsonPath("$.capabilities.attachments").value(false))
                .andExpect(jsonPath("$.accounts", hasSize(2)))
                .andExpect(jsonPath("$.accounts[0].mock").value(true));
    }

    @Test
    void generationKeepsHungarianInputIndependentFromGermanOutputAndRejectsPlannedModes() throws Exception {
        var request = Map.of("workflow", "TRANSLATION", "input", "Kérem, írjon egy szakmai levelet.",
                "preferences", Map.of("inputLanguage", "HU", "outputLanguage", "DE", "translationMode", "PROFESSIONAL"));
        mvc.perform(post("/api/writing/generate").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflow").value("TRANSLATION"))
                .andExpect(jsonPath("$.outputLanguage").value("DE"))
                .andExpect(jsonPath("$.translationMode").value("PROFESSIONAL"))
                .andExpect(jsonPath("$.detectedLanguage.language").value("HU"))
                .andExpect(jsonPath("$.detectedLanguage.method", containsString("User-selected")))
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.content", containsString("keine KI-Generierung oder echte Übersetzung")))
                .andExpect(jsonPath("$.content", containsString("Kérem, írjon egy szakmai levelet.")));
        mvc.perform(post("/api/writing/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workflow\":\"CV\",\"input\":\"Create my CV\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("Phase 2")));
    }

    @Test
    void emailGenerationPreservesTheUnrequestedManuallyEditedPart() throws Exception {
        var email = Map.of("subject", "Exact reviewed subject", "body", "Exact edited body\n  keep whitespace", "generationPart", "SUBJECT");
        mvc.perform(post("/api/writing/generate").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("workflow", "EMAIL", "input", "A subject please", "email", email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Exact edited body\n  keep whitespace"))
                .andExpect(jsonPath("$.subject").value("Demo: your message"));
        var bodyOnly = new HashMap<String, Object>(email);
        bodyOnly.put("generationPart", "BODY");
        mvc.perform(post("/api/writing/generate").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("workflow", "EMAIL", "input", "A body please", "email", bodyOnly))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Exact reviewed subject"))
                .andExpect(jsonPath("$.content", containsString("DEMO")));
    }

    @Test
    void editablePresetsAreComposedForPreviewAndGenerationWithoutChangingProtectedRules() throws Exception {
        String originalCore = application.getBean(RuleService.class).findById("writing-core").content();
        var preset = new HashMap<String, Object>(Map.of("id", "api-plain-email", "name", "Plain API email", "description", "No greeting",
                "workflow", "EMAIL", "enabled", true, "ruleIds", List.of("writing-email"), "markdown", "# Plain style\nDo not include a greeting or closing."));
        mvc.perform(post("/api/writing/presets").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(preset)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("api-plain-email"));
        preset.put("markdown", "# Updated API style\nKeep sentences concise. Do not include a greeting or closing.");
        mvc.perform(put("/api/writing/presets/api-plain-email").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(preset)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.markdown", containsString("Updated API style")));
        String task = JSON.writeValueAsString(Map.of("workflow", "EMAIL", "input", "UNTRUSTED TASK MARKER",
                "preferences", Map.of("presetId", "api-plain-email", "outputLanguage", "DE")));
        var preview = mvc.perform(post("/api/writing/preview-instructions").contentType(MediaType.APPLICATION_JSON).content(task))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemPrompt", containsString("# Updated API style")))
                .andExpect(jsonPath("$.systemPrompt", containsString("# Application security policy")))
                .andReturn().getResponse().getContentAsString();
        var generated = mvc.perform(post("/api/writing/generate").contentType(MediaType.APPLICATION_JSON).content(task))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(preview).path("systemPrompt").asString()).doesNotContain("UNTRUSTED TASK MARKER");
        assertThat(JSON.readTree(generated).path("appliedRuleIds")).isEqualTo(JSON.readTree(preview).path("appliedRuleIds"));
        var invalid = new HashMap<>(preset);
        invalid.put("id", "api-global-override");
        invalid.put("ruleIds", List.of("global-instructions"));
        mvc.perform(post("/api/writing/presets").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
        assertThat(application.getBean(RuleService.class).findById("writing-core").content()).isEqualTo(originalCore);
    }

    @Test
    void draftRoundTripPreservesLatestEditedContentAndRejectsAStaleRevision() throws Exception {
        var draft = new HashMap<String, Object>(Map.of("title", "Reviewed draft", "workflow", "CUSTOM_DOCUMENT", "input", "Original task",
                "content", "Initial content", "subject", "Initial subject", "modelId", "mock-precise",
                "preferences", Map.of("outputLanguage", "HU")));
        var created = mvc.perform(post("/api/writing/drafts").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(draft)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.revision").value(1))
                .andReturn().getResponse().getContentAsString();
        String id = JSON.readTree(created).path("id").asString();
        draft.put("revision", 1);
        draft.put("content", "Latest manual text\n  exact spacing");
        draft.put("subject", "Reviewed subject");
        String update = JSON.writeValueAsString(draft);
        mvc.perform(put("/api/writing/drafts/" + id).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2));
        mvc.perform(put("/api/writing/drafts/" + id).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.revision").isNotEmpty());
        mvc.perform(get("/api/writing/drafts/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Latest manual text\n  exact spacing"))
                .andExpect(jsonPath("$.subject").value("Reviewed subject"))
                .andExpect(jsonPath("$.input").value("Original task"))
                .andExpect(jsonPath("$.modelId").value("mock-precise"))
                .andExpect(jsonPath("$.preferences.outputLanguage").value("HU"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void emailPreviewShowsTheExactSnapshotAndMockDeliveryCannotBeConfirmedIntoSending() throws Exception {
        String message = JSON.writeValueAsString(Map.of("accountId", "mock-personal", "recipientName", "Example recipient", "to", "recipient@example.invalid",
                "cc", "copy@example.invalid", "bcc", "private@example.invalid", "subject", "Final reviewed subject", "body", "Final reviewed body\n  exact spacing"));
        var preview = mvc.perform(post("/api/writing/email/preview").contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.email").value("personal@example.invalid"))
                .andExpect(jsonPath("$.email.to").value("recipient@example.invalid"))
                .andExpect(jsonPath("$.email.cc").value("copy@example.invalid"))
                .andExpect(jsonPath("$.email.bcc").value("private@example.invalid"))
                .andExpect(jsonPath("$.email.subject").value("Final reviewed subject"))
                .andExpect(jsonPath("$.email.body").value("Final reviewed body\n  exact spacing"))
                .andExpect(jsonPath("$.attachments", hasSize(0)))
                .andExpect(jsonPath("$.sendingAvailable").value(false))
                .andExpect(jsonPath("$.mock").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = JSON.readTree(preview).path("id").asString();
        mvc.perform(post("/api/writing/email/send").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("previewId", id, "confirmed", false, "idempotencyKey", "api-attempt-0001"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("explicit confirmation")));
        mvc.perform(post("/api/writing/email/send").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("previewId", id, "confirmed", true, "idempotencyKey", "api-attempt-0001"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("never sends email")));
    }
}
