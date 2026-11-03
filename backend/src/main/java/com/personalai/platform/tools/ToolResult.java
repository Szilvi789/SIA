package com.personalai.platform.tools;

import java.util.Objects;

/** Tool output is untrusted data, including output from external services or files. */
public record ToolResult(boolean successful, String content) {
    public ToolResult {
        Objects.requireNonNull(content);
    }
}
