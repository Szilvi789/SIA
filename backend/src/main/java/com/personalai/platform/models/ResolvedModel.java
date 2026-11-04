package com.personalai.platform.models;
public record ResolvedModel(ModelDescriptor model, GenerationParameters parameters, String source,
        boolean fallbackUsed, String selectedModelId) { }
