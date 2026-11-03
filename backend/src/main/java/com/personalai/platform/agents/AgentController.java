package com.personalai.platform.agents;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final AgentService agents;

    public AgentController(AgentService agents) {
        this.agents = agents;
    }

    @GetMapping
    public List<AgentResponse> findAll() {
        return agents.findAll().stream().map(AgentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AgentResponse findById(@PathVariable String id) {
        return AgentResponse.from(agents.findById(id));
    }

    @PutMapping("/{id}")
    public AgentResponse update(@PathVariable String id, @Valid @RequestBody AgentUpdateRequest request) {
        return AgentResponse.from(agents.update(id, request));
    }
}
