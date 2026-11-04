package com.personalai.platform.writing.export;

import com.personalai.platform.configuration.RuntimeDataSettings;
import com.personalai.platform.configuration.RuntimeDataSettings.Category;
import jakarta.servlet.MultipartConfigElement;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sai.mode", havingValue = "personal")
public class PrivateExportMultipartConfiguration {
    @Bean
    MultipartConfigElement multipartConfigElement(RuntimeDataSettings settings) throws IOException {
        var temporary = settings.directory(Category.GENERATED).resolve("temporary");
        settings.requireContained(temporary);
        Files.createDirectories(temporary);
        settings.restrictToOwner(temporary, true);
        settings.requireContained(temporary);
        // Override any generic servlet temp location: multipart runtime bytes belong in the private namespace.
        var maximum = settings.maximumUploadBytes();
        return new MultipartConfigElement(temporary.toString(), maximum, maximum + 64 * 1024,
                (int) Math.min(maximum, 64 * 1024));
    }
}
