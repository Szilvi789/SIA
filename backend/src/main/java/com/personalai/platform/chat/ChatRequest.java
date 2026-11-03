package com.personalai.platform.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank @Size(max = 80) String agentId,
        @NotBlank @Size(max = 80) String modelId,
        @NotBlank @Size(max = 12_000) String message) { }
