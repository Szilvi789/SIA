package com.personalai.platform.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RuntimeDataConfiguration {
    @Bean
    RuntimeDataSettings runtimeDataSettings(
            @Value("${sai.mode:demo}") String mode,
            @Value("${sai.data-dir:}") String dataDirectory,
            @Value("${sai.repository-root:}") String repositoryRoot,
            @Value("${sai.storage.max-upload-bytes:10485760}") long maximumUploadBytes,
            @Value("${server.address:127.0.0.1}") String serverAddress) {
        var settings = new RuntimeDataSettings(mode, dataDirectory, repositoryRoot, maximumUploadBytes);
        requirePersonalLoopback(settings, serverAddress);
        return settings;
    }

    static void requirePersonalLoopback(RuntimeDataSettings settings, String serverAddress) {
        if (settings.isPersonal() && !java.util.Set.of("127.0.0.1", "::1", "localhost").contains(serverAddress)) {
            throw new IllegalStateException("Personal mode requires a loopback server address until authentication is available.");
        }
    }
}
