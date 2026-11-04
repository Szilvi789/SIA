package com.personalai.platform.models;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController
public class ModelController {
    private final ModelCatalog models;
    public ModelController(ModelCatalog models) { this.models = models; }
    @GetMapping("/api/models") public List<ModelDescriptor> findAll() { return models.findAll(); }
    @GetMapping("/api/models/{id}") public ModelDescriptor findById(@PathVariable String id) { return models.findById(id); }
    @PutMapping("/api/models/{id}") public ModelDescriptor save(@PathVariable String id, @RequestBody ModelDescriptor model) { return models.save(id, model); }
    @GetMapping("/api/model-settings") public ApplicationModelSettings settings() { return models.settings(); }
    @PutMapping("/api/model-settings") public ApplicationModelSettings settings(@RequestBody ApplicationModelSettings settings) { return models.saveSettings(settings); }
}
