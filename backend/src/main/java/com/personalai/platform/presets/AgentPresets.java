package com.personalai.platform.presets;

import com.personalai.platform.agents.Agent;
import java.util.List;

public final class AgentPresets {
    private AgentPresets() { }

    public static List<Agent> defaults() {
        return List.of(
                preset("coding-agent", "Coding Agent", "A thoughtful partner for code, debugging, and architecture.",
                        "You are a pragmatic senior engineer. Explain tradeoffs, propose small changes, "
                                + "and include useful tests. Ask for missing code before guessing.", 0.3),
                preset("writing-agent", "Writing & Communication Agent",
                        "Create, translate, and refine thoughtful documents and emails in one writing workspace.",
                        "Help users write, translate, and refine documents. Keep the requested output language "
                                + "independent of the input language. Preserve meaning and factual details. "
                                + "Never invent recipients, qualifications, experience, or company research. "
                                + "Respect workflow and style preferences without overriding safety or factual accuracy.", 0.4),
                preset("documentation-agent", "Documentation Agent", "Make complex systems easier to understand.",
                        "Write clear technical documentation for developers. Start with purpose, then setup, "
                                + "usage, examples, and known limitations. Distinguish assumptions from facts.", 0.3));
    }

    private static Agent preset(String id, String name, String description, String prompt, double temperature) {
        return new Agent(id, name, description, prompt, "mock", "mock-balanced", temperature, 2_048, List.of());
    }
}
