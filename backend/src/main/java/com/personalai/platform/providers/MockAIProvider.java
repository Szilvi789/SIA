package com.personalai.platform.providers;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MockAIProvider implements AIProvider {
    @Override
    public String id() {
        return "mock";
    }

    @Override
    public String generate(ProviderRequest request) {
        if (request.writingContext() != null) {
            return com.personalai.platform.writing.infrastructure.MockWritingRenderer.render(request);
        }
        var task = excerpt(request.message(), 700);
        var body = switch (request.agent().id()) {
            case "coding-agent" -> "Here is a practical starting point for: \"" + task + "\"\n\n"
                    + "1. Define the expected behavior and the smallest reproducible example.\n"
                    + "2. Separate the core logic from external dependencies.\n"
                    + "3. Cover the successful path and one meaningful failure case with tests.\n\n"
                    + "Share the relevant code and error output to shape the implementation.";
            case "translation-agent" -> "Translation brief\n\nSource text: \"" + task + "\"\n\n"
                    + "Confirm the target language, audience, and tone. A real translation provider would "
                    + "preserve names, formatting, and meaning using those preferences. "
                    + "This demo prepares the brief; it does not translate the text.";
            case "email-assistant" -> "Subject: A quick follow-up\n\nHi [Name],\n\n"
                    + "I am reaching out about \"" + task + "\". "
                    + "Could you share your thoughts and let me know a suitable next step?\n\n"
                    + "Thank you,\n[Your name]\n\nReplace the placeholders and review the details before sending.";
            case "documentation-agent" -> "Documentation outline: " + task + "\n\n"
                    + "Overview\nDescribe the purpose, audience, and problem being solved.\n\n"
                    + "Getting started\nList prerequisites, configuration, and the first useful command.\n\n"
                    + "Usage\nAdd an example with expected output and common errors.\n\n"
                    + "Limitations\nRecord assumptions, supported versions, and planned improvements.";
            default -> "A useful first step for \"" + task + "\" is to define the goal, constraints, and expected result.";
        };
        var style = switch (request.model().id()) {
            case "mock-precise" -> "Focus: concise, verifiable next steps.";
            case "mock-creative" -> "Explore: try two approaches and compare their tradeoffs.";
            default -> "Focus: a clear plan with room to iterate.";
        };
        return String.format(Locale.ROOT,
                "Demo response · %s\nNo AI model was called.\n\n%s\n\n%s\n\n"
                        + "Active instructions: %s\nConfiguration: %s · temperature %.1f · max output %d tokens. "
                        + "These settings are included for inspection; this template does not perform sampling or token accounting.",
                request.agent().name(), body, style, excerpt(request.agent().systemPrompt(), 240),
                request.model().name(), request.agent().temperature(), request.agent().maxOutputTokens());
    }

    private String excerpt(String text, int limit) {
        var normalized = text.strip();
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit) + "…";
    }
}
