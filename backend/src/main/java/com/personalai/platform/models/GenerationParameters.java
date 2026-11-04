package com.personalai.platform.models;

import java.util.List;

public record GenerationParameters(Integer maxOutputTokens, Double temperature, Double topP,
        String reasoningEffort, Integer timeoutMs, List<String> tools) {
    public GenerationParameters { tools = tools == null ? null : List.copyOf(tools); }
    public static GenerationParameters empty() { return new GenerationParameters(null, null, null, null, null, null); }
    public GenerationParameters merge(GenerationParameters override) {
        if (override == null) return this;
        return new GenerationParameters(override.maxOutputTokens != null ? override.maxOutputTokens : maxOutputTokens,
                override.temperature != null ? override.temperature : temperature,
                override.topP != null ? override.topP : topP,
                override.reasoningEffort != null ? override.reasoningEffort : reasoningEffort,
                override.timeoutMs != null ? override.timeoutMs : timeoutMs,
                override.tools != null ? override.tools : tools);
    }
}
