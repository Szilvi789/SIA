package com.personalai.platform.tools;

import com.personalai.platform.agents.Agent;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Future application entry point for tools; deliberately not exposed by chat or HTTP.
 * Callers must load the Agent from application-owned configuration before invoking.
 */
@Service
public class ToolExecutionService {
    private final ToolRegistry tools;
    private final ToolAuthorizationPolicy authorization;

    public ToolExecutionService(ToolRegistry tools, ToolAuthorizationPolicy authorization) {
        this.tools = tools;
        this.authorization = authorization;
    }

    public ToolResult execute(Agent agent, ToolInvocation invocation) {
        Objects.requireNonNull(agent);
        Objects.requireNonNull(invocation);
        var tool = tools.requireTool(invocation.toolId());
        if (!agent.enabledTools().contains(invocation.toolId())) {
            throw new ToolAuthorizationException(
                    "Tool is not enabled for this agent: " + invocation.toolId(),
                    ToolAuthorizationDecision.DENIED);
        }
        var decision = authorization.authorize(agent, tools.definitionFor(invocation.toolId()), invocation);
        if (decision != ToolAuthorizationDecision.ALLOWED) {
            throw new ToolAuthorizationException("Tool execution is not authorized: " + invocation.toolId(),
                    decision == null ? ToolAuthorizationDecision.DENIED : decision);
        }
        return tool.execute(invocation);
    }
}
