package com.personalai.platform.tools;

/** Implementations are registered by the application, never by document or model content. */
public interface AgentTool {
    ToolDefinition definition();

    /** Called only after ToolExecutionService authorizes the exact invocation. */
    ToolResult execute(ToolInvocation invocation);
}
