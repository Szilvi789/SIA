package com.personalai.platform.tools;

import java.util.Map;
import java.util.Objects;

/** Untrusted tool arguments. No argument can confer permission or prove user approval. */
public record ToolInvocation(String toolId, Map<String, String> arguments) {
    public ToolInvocation {
        if (toolId == null || toolId.isBlank()) {
            throw new IllegalArgumentException("Tool id must not be blank.");
        }
        arguments = Map.copyOf(Objects.requireNonNull(arguments));
    }
}
