package com.personalai.platform.configuration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RuntimeConfigController {
    private final RuntimeDataSettings settings;

    public RuntimeConfigController(RuntimeDataSettings settings) { this.settings = settings; }

    @GetMapping("/api/runtime-config")
    RuntimeConfig runtimeConfig() {
        // Personal startup requires PostgreSQL; the personal persistence configuration fails closed.
        return new RuntimeConfig(settings.modeName(), settings.isPersonal(), "external", true);
    }

    record RuntimeConfig(String mode, boolean persistent, String dataStorage, boolean cloudRequestsMayLeaveDevice) {}
}
