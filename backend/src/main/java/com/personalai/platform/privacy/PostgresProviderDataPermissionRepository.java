package com.personalai.platform.privacy;

import com.personalai.platform.persistence.PersistentDocumentStore;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "sai.mode", havingValue = "personal")
public class PostgresProviderDataPermissionRepository implements ProviderDataPermissionRepository {
    private final PersistentDocumentStore store;
    public PostgresProviderDataPermissionRepository(PersistentDocumentStore store) { this.store = store; }
    public Optional<ProviderDataPermission> find(String id) { return store.find("provider-permissions", id, ProviderDataPermission.class); }
    public ProviderDataPermission save(ProviderDataPermission permission) { return store.save("provider-permissions", permission.providerId(), permission); }
}
