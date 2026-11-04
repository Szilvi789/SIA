package com.personalai.platform.privacy;

import static org.junit.jupiter.api.Assertions.*;
import com.personalai.platform.configuration.RuntimeDataSettings;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RuntimeModeFilterTest {
    @TempDir Path temporary;
    private RuntimeModeFilter filter(String mode) {
        return new RuntimeModeFilter(new RuntimeDataSettings(mode, temporary.resolve("external").toString(), "", 1024));
    }
    @Test void staleBrowserModeIsRejectedBeforeTaskProcessing() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/writing/generate");
        request.addHeader("X-SAI-Mode", "personal");
        var response = new MockHttpServletResponse();
        var processed = new AtomicBoolean();
        filter("demo").doFilter(request, response, (req, res) -> processed.set(true));
        assertEquals(409, response.getStatus());
        assertFalse(processed.get());
        assertTrue(response.getContentAsString().contains("backend mode changed"));
    }
    @Test void matchingModeAndExplicitLegacyClientsCanUseTheirSelectedEnvironment() throws Exception {
        for (String header : new String[]{null, "personal"}) {
            var request = new MockHttpServletRequest("GET", "/api/conversations");
            if (header != null) request.addHeader("X-SAI-Mode", header);
            var processed = new AtomicBoolean();
            filter("personal").doFilter(request, new MockHttpServletResponse(), (req, res) -> processed.set(true));
            assertTrue(processed.get());
        }
    }
    @Test void runtimeDiscoveryRemainsAvailableAfterModeChanges() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/runtime-config");
        request.addHeader("X-SAI-Mode", "personal");
        var processed = new AtomicBoolean();
        filter("demo").doFilter(request, new MockHttpServletResponse(), (req, res) -> processed.set(true));
        assertTrue(processed.get());
    }
}
