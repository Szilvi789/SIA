package com.personalai.platform.privacy;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class DataPermissionController {
    private final CloudDataSharingPolicy policy;
    public DataPermissionController(CloudDataSharingPolicy policy) { this.policy = policy; }
    @GetMapping("/api/provider-data-permissions") public List<ProviderDataPermission> findAll() { return policy.findAll(); }
    @GetMapping("/api/providers/{id}/data-permission") public ProviderDataPermission find(@PathVariable String id) { return policy.permission(id); }
    @PutMapping("/api/providers/{id}/data-permission") public ProviderDataPermission save(@PathVariable String id, @RequestBody ProviderDataPermission permission) { return policy.save(id, permission); }
}
