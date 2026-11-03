package com.personalai.platform.rules;

import java.util.List;

public record ResolvedInstructions(String systemPrompt, List<String> appliedRuleIds) {
    public ResolvedInstructions {
        if (systemPrompt == null) {
            throw new IllegalArgumentException("Resolved instructions cannot be null.");
        }
        appliedRuleIds = List.copyOf(appliedRuleIds);
    }
}
