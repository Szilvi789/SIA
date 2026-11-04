package com.personalai.platform.configuration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class RuntimeConfigControllerTest {
    @TempDir Path temporary;

    @Test
    void diagnosticsDescribeModeAndCloudPrivacyWithoutPublishingPrivatePaths() {
        var settings = new RuntimeDataSettings("demo", temporary.resolve("private").toString(), "", 1024);
        var response = new RuntimeConfigController(settings).runtimeConfig();
        assertEquals("demo", response.mode());
        assertFalse(response.persistent());
        assertEquals("external", response.dataStorage());
        assertTrue(response.cloudRequestsMayLeaveDevice());
        var json = JsonMapper.builder().build().writeValueAsString(response);
        assertFalse(json.contains(temporary.toString()));
        assertFalse(json.contains("private"));
        var personal = new RuntimeDataSettings("personal", temporary.resolve("private").toString(), "", 1024);
        assertTrue(new RuntimeConfigController(personal).runtimeConfig().persistent());
    }
}
