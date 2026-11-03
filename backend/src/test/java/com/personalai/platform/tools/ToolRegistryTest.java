package com.personalai.platform.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalai.platform.common.InvalidConfigurationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {
    @Test
    void supportsAnEmptyProductionRegistry() {
        var registry = new ToolRegistry(List.of());

        assertThat(registry.list()).isEmpty();
        assertThat(registry.find("missing")).isEmpty();
        assertThat(registry.definitionsFor(List.of())).isEmpty();
    }

    @Test
    void exposesOnlyRegisteredDefinitionsInConfiguredOrder() {
        var first = tool("first");
        var second = tool("second");
        var implementations = new ArrayList<>(List.of(first, second));
        var registry = new ToolRegistry(implementations);
        implementations.clear();

        assertThat(registry.list()).extracting(ToolDefinition::id).containsExactly("first", "second");
        assertThat(registry.find("first")).contains(first);
        assertThat(registry.requireTool("second")).isSameAs(second);
        assertThat(registry.definitionsFor(List.of("second", "first")))
                .extracting(ToolDefinition::id).containsExactly("second", "first");
        assertThatThrownBy(() -> registry.list().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateRegistrations() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(tool("same"), tool("same"))))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("Duplicate tool");
    }

    @Test
    void rejectsUnknownEnabledTools() {
        var registry = new ToolRegistry(List.of(tool("known")));

        assertThatThrownBy(() -> registry.validateEnabledTools(List.of("unknown")))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("Unknown tool");
        assertThatThrownBy(() -> registry.definitionsFor(List.of("unknown")))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void rejectsDuplicateEnabledTools() {
        var registry = new ToolRegistry(List.of(tool("known")));

        assertThatThrownBy(() -> registry.validateEnabledTools(List.of("known", "known")))
                .isInstanceOf(InvalidConfigurationException.class).hasMessageContaining("Duplicate enabled");
    }

    @Test
    void permissionMetadataIsImmutableAndDistinguishesSensitiveOperations() {
        var permissions = new HashSet<>(Set.of(ToolPermission.WRITE_FILES));
        var definition = new ToolDefinition("writer", "Writer", "Writes within future approved scope.", permissions);
        permissions.clear();

        assertThat(definition.requiredPermissions()).containsExactly(ToolPermission.WRITE_FILES);
        assertThat(definition.requiresApproval()).isTrue();
        assertThat(ToolPermission.EXECUTE_CODE.requiresApproval()).isTrue();
        assertThat(ToolPermission.MODIFY_GIT.requiresApproval()).isTrue();
        assertThat(new ToolDefinition("read", "Read", "Reads external content.",
                Set.of(ToolPermission.READ_EXTERNAL_CONTENT)).requiresApproval()).isFalse();
        assertThatThrownBy(() -> definition.requiredPermissions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void snapshotsInvocationArguments() {
        var arguments = new HashMap<>(Map.of("query", "original"));
        var invocation = new ToolInvocation("test", arguments);
        arguments.put("query", "changed");

        assertThat(invocation.arguments()).containsEntry("query", "original");
        assertThatThrownBy(() -> invocation.arguments().put("approved", "true"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void snapshotsToolDefinitionDuringRegistration() {
        var definitions = new ArrayList<>(List.of(new ToolDefinition("test", "Test", "Test tool.",
                Set.of(ToolPermission.WRITE_FILES))));
        var tool = new AgentTool() {
            @Override
            public ToolDefinition definition() {
                return definitions.getFirst();
            }

            @Override
            public ToolResult execute(ToolInvocation invocation) {
                return new ToolResult(true, "test");
            }
        };
        var registry = new ToolRegistry(List.of(tool));
        definitions.set(0, new ToolDefinition("test", "Test", "Changed metadata.", Set.of()));

        assertThat(registry.definitionFor("test").requiredPermissions()).containsExactly(ToolPermission.WRITE_FILES);
    }

    private AgentTool tool(String id) {
        return new AgentTool() {
            @Override
            public ToolDefinition definition() {
                return new ToolDefinition(id, id, "Test implementation.", Set.of());
            }

            @Override
            public ToolResult execute(ToolInvocation invocation) {
                return new ToolResult(true, "test");
            }
        };
    }
}
