package com.personalai.platform.agents;

/** Stable aliases preserve references to the two workflows merged into one writing agent. */
public final class AgentIds {
    public static final String WRITING = "writing-agent";

    private AgentIds() { }

    public static String canonical(String id) {
        return "translation-agent".equals(id) || "email-assistant".equals(id) ? WRITING : id;
    }
}
