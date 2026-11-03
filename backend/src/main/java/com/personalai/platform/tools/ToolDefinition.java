package com.personalai.platform.tools;

import java.util.Objects;
import java.util.Set;

/** Application-owned metadata. Required permissions are requirements, never grants. */
public record ToolDefinition(
        String id,
        String name,
        String description,
        Set<ToolPermission> requiredPermissions) {
    public ToolDefinition {
        requireText(id, "id");
        requireText(name, "name");
        requireText(description, "description");
        requiredPermissions = Set.copyOf(Objects.requireNonNull(requiredPermissions));
    }

    public boolean requiresApproval() {
        return requiredPermissions.stream().anyMatch(ToolPermission::requiresApproval);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tool " + field + " must not be blank.");
        }
    }
}
