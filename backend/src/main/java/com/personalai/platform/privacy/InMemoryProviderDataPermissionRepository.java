package com.personalai.platform.privacy;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "sai.mode", havingValue = "demo", matchIfMissing = true)
public class InMemoryProviderDataPermissionRepository implements ProviderDataPermissionRepository {
    private final ConcurrentHashMap<String, ProviderDataPermission> permissions = new ConcurrentHashMap<>();
    public Optional<ProviderDataPermission> find(String id) { return Optional.ofNullable(permissions.get(id)); }
    public ProviderDataPermission save(ProviderDataPermission permission) { permissions.put(permission.providerId(), permission); return permission; }
}
