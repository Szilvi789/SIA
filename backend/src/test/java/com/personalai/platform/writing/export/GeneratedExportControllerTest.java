package com.personalai.platform.writing.export;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.personalai.platform.common.ApiExceptionHandler;
import com.personalai.platform.configuration.RuntimeDataSettings;
import com.personalai.platform.configuration.RuntimeDataSettings.Category;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class GeneratedExportControllerTest {
    @TempDir Path temporary;

    @Test
    void responseContainsOnlyOpaqueReferenceAndFormatAndClientFilenameIsNeverAStoragePath() throws Exception {
        var settings = settings("personal");
        var mvc = MockMvcBuilders.standaloneSetup(new GeneratedExportController(new GeneratedExportService(settings)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        var bytes = "Synthetic generated content".getBytes(StandardCharsets.UTF_8);
        var upload = new MockMultipartFile("file", "../../private-title.txt", "application/octet-stream", bytes);
        var response = mvc.perform(multipart("/api/writing/exports").file(upload).param("format", "TXT"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.format").value("TXT"))
                .andExpect(jsonPath("$.id").isString()).andReturn().getResponse().getContentAsString();
        var json = JsonMapper.builder().build().readTree(response);
        assertEquals(2, json.size());
        assertFalse(response.contains("private-title"));
        assertFalse(response.contains(temporary.toString()));
        var stored = settings.directory(Category.GENERATED).resolve(json.get("id").asString() + ".txt");
        assertArrayEquals(bytes, Files.readAllBytes(stored));
    }

    @Test
    void demoRejectsPrivateExportPersistence() throws Exception {
        var settings = settings("demo");
        var mvc = MockMvcBuilders.standaloneSetup(new GeneratedExportController(new GeneratedExportService(settings)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(multipart("/api/writing/exports")
                        .file(new MockMultipartFile("file", "draft.txt", "text/plain", new byte[0])).param("format", "TXT"))
                .andExpect(status().isBadRequest());
        assertFalse(Files.exists(settings.dataDirectory()));
    }

    @Test
    void multipartTemporaryFilesAndLimitsUseOnlyThePrivateGeneratedNamespace() throws Exception {
        var settings = settings("personal");
        var config = new PrivateExportMultipartConfiguration().multipartConfigElement(settings);
        assertTrue(Path.of(config.getLocation()).startsWith(settings.dataDirectory().resolve("generated")));
        assertEquals(4096, config.getMaxFileSize());
        assertEquals(4096 + 64 * 1024, config.getMaxRequestSize());
        assertTrue(config.getFileSizeThreshold() <= 64 * 1024);
        assertTrue(Files.isDirectory(Path.of(config.getLocation())));
    }

    private RuntimeDataSettings settings(String mode) {
        return new RuntimeDataSettings(mode, temporary.resolve("private").toString(), "", 4096);
    }
}
