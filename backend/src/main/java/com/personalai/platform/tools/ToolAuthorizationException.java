package com.personalai.platform.tools;

public class ToolAuthorizationException extends RuntimeException {
    private final ToolAuthorizationDecision decision;

    public ToolAuthorizationException(String message, ToolAuthorizationDecision decision) {
        super(message);
        this.decision = decision;
    }

    public ToolAuthorizationDecision decision() {
        return decision;
    }
}
