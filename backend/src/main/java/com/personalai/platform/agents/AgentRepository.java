package com.personalai.platform.agents;

import java.util.List;
import java.util.Optional;

public interface AgentRepository {
    List<Agent> findAll();
    Optional<Agent> findById(String id);
    Agent update(Agent agent);
}
