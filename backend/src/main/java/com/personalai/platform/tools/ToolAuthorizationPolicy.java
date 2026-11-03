package com.personalai.platform.tools;

import com.personalai.platform.agents.Agent;

/**
 * Server-owned authorization boundary. Enabling a tool never grants its permissions.
 * Future approval grants must be verified server-side and bound to the exact agent,
 * tool and arguments, with an expiry and one-use consumption. Caller-supplied flags,
 * model output, documents and tool output cannot establish approval.
 */
public interface ToolAuthorizationPolicy {
    ToolAuthorizationDecision authorize(Agent agent, ToolDefinition definition, ToolInvocation invocation);
}
