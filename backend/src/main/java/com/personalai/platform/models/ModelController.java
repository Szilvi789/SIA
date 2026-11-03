package com.personalai.platform.models;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModelController {
    private final ModelCatalog models;

    public ModelController(ModelCatalog models) {
        this.models = models;
    }

    @GetMapping("/api/models")
    public List<ModelDescriptor> findAll() {
        return models.findAll();
    }
}
