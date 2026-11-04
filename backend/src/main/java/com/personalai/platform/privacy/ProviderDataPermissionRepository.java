package com.personalai.platform.privacy;

import java.util.Optional;

public interface ProviderDataPermissionRepository {
    Optional<ProviderDataPermission> find(String providerId);
    ProviderDataPermission save(ProviderDataPermission permission);
}
