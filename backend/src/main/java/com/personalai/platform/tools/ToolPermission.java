package com.personalai.platform.tools;

/** Capabilities a future tool requires; these declarations do not grant access. */
public enum ToolPermission {
    READ_EXTERNAL_CONTENT(false),
    READ_FILES(true),
    WRITE_FILES(true),
    EXECUTE_CODE(true),
    READ_GIT(false),
    MODIFY_GIT(true),
    CALL_EXTERNAL_API(true);

    private final boolean requiresApproval;

    ToolPermission(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public boolean requiresApproval() {
        return requiresApproval;
    }
}
