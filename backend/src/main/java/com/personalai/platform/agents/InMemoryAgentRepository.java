package com.personalai.platform.agents;

import com.personalai.platform.common.ResourceNotFoundException;
import com.personalai.platform.presets.AgentPresets;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryAgentRepository implements AgentRepository {
    private final ConcurrentMap<String, Agent> agents = new ConcurrentHashMap<>();

    public InMemoryAgentRepository() {
        AgentPresets.defaults().forEach(agent -> agents.put(agent.id(), agent));
    }

    @Override
    public List<Agent> findAll() {
        return agents.values().stream().sorted(Comparator.comparing(Agent::id)).toList();
    }

    @Override
    public Optional<Agent> findById(String id) {
        return Optional.ofNullable(agents.get(AgentIds.canonical(id))).map(agent -> withId(agent, id));
    }

    @Override
    public Agent update(Agent agent) {
        var canonical = withId(agent, AgentIds.canonical(agent.id()));
        if (agents.replace(canonical.id(), canonical) == null) {
            throw new ResourceNotFoundException("Agent was not found.");
        }
        return agent;
    }

    private Agent withId(Agent agent, String id) {
        return agent.withId(id);
    }
}
