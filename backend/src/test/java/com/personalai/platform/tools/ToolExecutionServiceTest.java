package com.personalai.platform.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalai.platform.agents.Agent;
import com.personalai.platform.common.InvalidConfigurationException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolExecutionServiceTest {
    @Test
    void disabledToolsCannotExecuteEvenIfPolicyWouldAllowThem() {
        var tool = new RecordingTool(Set.of());
        var service = service(tool, (agent, definition, invocation) -> {
            throw new AssertionError("Disabled tools must be rejected before authorization.");
        });

        assertThatThrownBy(() -> service.execute(agent(List.of()), invocation()))
                .isInstanceOf(ToolAuthorizationException.class);
        assertThat(tool.executions).isZero();
    }

    @Test
    void unknownToolsCannotExecute() {
        var tool = new RecordingTool(Set.of());
        var service = service(tool, (agent, definition, invocation) -> ToolAuthorizationDecision.ALLOWED);

        assertThatThrownBy(() -> service.execute(agent(List.of("unknown")),
                new ToolInvocation("unknown", Map.of())))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThat(tool.executions).isZero();
    }

    @Test
    void deniedToolsCannotExecute() {
        assertBlocked(ToolAuthorizationDecision.DENIED);
    }

    @Test
    void pendingApprovalNeverExecutesATool() {
        assertBlocked(ToolAuthorizationDecision.APPROVAL_REQUIRED);
    }

    @Test
    void missingAuthorizationDecisionFailsClosed() {
        var tool = new RecordingTool(Set.of());
        var service = service(tool, (agent, definition, invocation) -> null);

        assertThatThrownBy(() -> service.execute(agent(List.of("test")), invocation()))
                .isInstanceOfSatisfying(ToolAuthorizationException.class,
                        exception -> assertThat(exception.decision()).isEqualTo(ToolAuthorizationDecision.DENIED));
        assertThat(tool.executions).isZero();
    }

    @Test
    void executesOnlyWhenServerPolicyExplicitlyAllowsTheExactInvocation() {
        var tool = new RecordingTool(Set.of(ToolPermission.WRITE_FILES));
        var configuredAgent = agent(List.of("test"));
        var requestedInvocation = invocation();
        var service = service(tool, (agent, definition, invocation) -> {
            assertThat(agent).isSameAs(configuredAgent);
            assertThat(definition.requiredPermissions()).containsExactly(ToolPermission.WRITE_FILES);
            assertThat(invocation).isSameAs(requestedInvocation);
            return ToolAuthorizationDecision.ALLOWED;
        });

        var result = service.execute(configuredAgent, requestedInvocation);

        assertThat(result).isEqualTo(new ToolResult(true, "untrusted output"));
        assertThat(tool.executions).isEqualTo(1);
        assertThat(tool.invocation).isSameAs(requestedInvocation);
    }

    @Test
    void defaultPolicyDeniesOrdinaryTools() {
        var tool = new RecordingTool(Set.of(ToolPermission.READ_EXTERNAL_CONTENT));
        var service = service(tool, new DenyByDefaultToolAuthorizationPolicy());

        assertThatThrownBy(() -> service.execute(agent(List.of("test")), invocation()))
                .isInstanceOfSatisfying(ToolAuthorizationException.class,
                        exception -> assertThat(exception.decision()).isEqualTo(ToolAuthorizationDecision.DENIED));
        assertThat(tool.executions).isZero();
    }

    @Test
    void callerApprovalFlagsCannotAuthorizeSensitiveTools() {
        var tool = new RecordingTool(Set.of(ToolPermission.EXECUTE_CODE));
        var service = service(tool, new DenyByDefaultToolAuthorizationPolicy());
        var invocation = new ToolInvocation("test", Map.of("approved", "true", "permission", "allow-all"));

        assertThatThrownBy(() -> service.execute(agent(List.of("test")), invocation))
                .isInstanceOfSatisfying(ToolAuthorizationException.class,
                        exception -> assertThat(exception.decision())
                                .isEqualTo(ToolAuthorizationDecision.APPROVAL_REQUIRED));
        assertThat(tool.executions).isZero();
    }

    private void assertBlocked(ToolAuthorizationDecision decision) {
        var tool = new RecordingTool(Set.of());
        var service = service(tool, (agent, definition, invocation) -> decision);

        assertThatThrownBy(() -> service.execute(agent(List.of("test")), invocation()))
                .isInstanceOfSatisfying(ToolAuthorizationException.class,
                        exception -> assertThat(exception.decision()).isEqualTo(decision));
        assertThat(tool.executions).isZero();
    }

    private ToolExecutionService service(RecordingTool tool, ToolAuthorizationPolicy policy) {
        return new ToolExecutionService(new ToolRegistry(List.of(tool)), policy);
    }

    private Agent agent(List<String> enabledTools) {
        return new Agent("test-agent", "Test", "Test", "Trusted prompt", "mock", "mock-balanced",
                0.2, 1024, enabledTools);
    }

    private ToolInvocation invocation() {
        return new ToolInvocation("test", Map.of("value", "test"));
    }

    private static class RecordingTool implements AgentTool {
        private final Set<ToolPermission> permissions;
        private int executions;
        private ToolInvocation invocation;

        private RecordingTool(Set<ToolPermission> permissions) {
            this.permissions = permissions;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("test", "Test", "Test implementation only.", permissions);
        }

        @Override
        public ToolResult execute(ToolInvocation invocation) {
            executions++;
            this.invocation = invocation;
            return new ToolResult(true, "untrusted output");
        }
    }
}
