package com.personalai.platform.tools;

import com.personalai.platform.common.InvalidConfigurationException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Registers application-owned implementations. No production tools are supplied yet. */
@Component
public class ToolRegistry {
    private final Map<String, RegisteredTool> registered;

    public ToolRegistry(List<AgentTool> tools) {
        var entries = new LinkedHashMap<String, RegisteredTool>();
        for (var tool : tools) {
            var definition = tool.definition();
            if (entries.putIfAbsent(definition.id(), new RegisteredTool(tool, definition)) != null) {
                throw new InvalidConfigurationException("Duplicate tool id: " + definition.id());
            }
        }
        registered = Collections.unmodifiableMap(entries);
    }

    public List<ToolDefinition> list() {
        return registered.values().stream().map(RegisteredTool::definition).toList();
    }

    public Optional<AgentTool> find(String id) {
        return Optional.ofNullable(registered.get(id)).map(RegisteredTool::implementation);
    }

    public AgentTool requireTool(String id) {
        return requireRegistered(id).implementation();
    }

    public ToolDefinition definitionFor(String id) {
        return requireRegistered(id).definition();
    }

    public void validateEnabledTools(List<String> ids) {
        if (ids == null) {
            throw new InvalidConfigurationException("Enabled tools must be a list.");
        }
        var unique = new HashSet<String>();
        for (var id : ids) {
            if (!unique.add(id)) {
                throw new InvalidConfigurationException("Duplicate enabled tool id: " + id);
            }
            requireRegistered(id);
        }
    }

    public List<ToolDefinition> definitionsFor(List<String> ids) {
        validateEnabledTools(ids);
        return ids.stream().map(this::definitionFor).toList();
    }

    private RegisteredTool requireRegistered(String id) {
        var tool = registered.get(id);
        if (tool == null) {
            throw new InvalidConfigurationException("Unknown tool id: " + id);
        }
        return tool;
    }

    private record RegisteredTool(AgentTool implementation, ToolDefinition definition) {
    }
}
