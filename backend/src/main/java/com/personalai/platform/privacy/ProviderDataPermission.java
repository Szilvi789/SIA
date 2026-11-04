package com.personalai.platform.privacy;

/** Contains permissions only; credentials and OAuth tokens are never stored here. */
public record ProviderDataPermission(String providerId, Boolean enabled, Boolean taskContent,
        Boolean personalInstructions, Boolean knowledgePassages) {
    public ProviderDataPermission {
        enabled = Boolean.TRUE.equals(enabled);
        taskContent = Boolean.TRUE.equals(taskContent);
        personalInstructions = Boolean.TRUE.equals(personalInstructions);
        knowledgePassages = Boolean.TRUE.equals(knowledgePassages);
    }
    public static ProviderDataPermission denied(String providerId) {
        return new ProviderDataPermission(providerId, false, false, false, false);
    }
}
