package com.personalai.platform.tools;

import com.personalai.platform.agents.Agent;
import org.springframework.stereotype.Component;

/** No grant store or approval workflow exists yet, so this policy never allows execution. */
@Component
public class DenyByDefaultToolAuthorizationPolicy implements ToolAuthorizationPolicy {
    @Override
    public ToolAuthorizationDecision authorize(
            Agent agent, ToolDefinition definition, ToolInvocation invocation) {
        return definition.requiresApproval()
                ? ToolAuthorizationDecision.APPROVAL_REQUIRED
                : ToolAuthorizationDecision.DENIED;
    }
}
