package com.personalai.platform.privacy;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.configuration.RuntimeDataSettings;
import com.personalai.platform.providers.AIProvider;
import com.personalai.platform.providers.ProviderRegistry;
import com.personalai.platform.providers.ProviderRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Local retrieval does not authorize transmitting retrieved passages to another party. */
@Service
public class CloudDataSharingPolicy {
    private final ProviderDataPermissionRepository permissions;
    private final ProviderRegistry providers;
    private final boolean personal;

    @Autowired
    public CloudDataSharingPolicy(ProviderDataPermissionRepository permissions, ProviderRegistry providers, RuntimeDataSettings settings) {
        this(permissions, providers, settings.isPersonal());
    }
    public CloudDataSharingPolicy(ProviderDataPermissionRepository permissions, ProviderRegistry providers, boolean personal) {
        this.permissions = permissions; this.providers = providers; this.personal = personal;
    }
    public static CloudDataSharingPolicy denyExternalByDefault(ProviderRegistry providers) {
        return new CloudDataSharingPolicy(new InMemoryProviderDataPermissionRepository(), providers, false);
    }
    public List<ProviderDataPermission> findAll() {
        return providers.findAll().stream().map(provider -> permission(provider.id())).toList();
    }
    public ProviderDataPermission permission(String id) {
        providers.configuration(id);
        return permissions.find(id).orElseGet(() -> ProviderDataPermission.denied(id));
    }
    public ProviderDataPermission save(String id, ProviderDataPermission permission) {
        providers.configuration(id);
        if (permission == null || !id.equals(permission.providerId())) throw invalid("The provider and permission identifiers must match.");
        if (!personal && permission.enabled()) throw invalid("Demo mode does not allow external data sharing. Start a separate personal environment first.");
        return permissions.save(permission);
    }
    public void validate(AIProvider provider, ProviderRequest request, DataSharingConsent consent) {
        var locality = provider.dataLocality();
        if (locality == DataLocality.LOCAL) return;
        if (locality != DataLocality.EXTERNAL) throw invalid("This adapter has not declared where data is processed. Generation is blocked.");
        if (!personal) throw invalid("Cloud model requests are disabled in demo mode.");
        var permission = permission(provider.id());
        if (!permission.enabled() || !permission.taskContent() || !permission.personalInstructions()) {
            throw invalid("Allow task content and agent instructions for this provider in privacy settings before using its cloud API.");
        }
        if (consent == null || !provider.id().equals(consent.providerId())
                || !Boolean.TRUE.equals(consent.taskContent()) || !Boolean.TRUE.equals(consent.personalInstructions())) {
            throw invalid("Authorize sending this task and its instructions to the effective cloud provider before generating.");
        }
        if (!request.knowledgeContext().isEmpty() && (!permission.knowledgePassages()
                || request.knowledgeContext().stream().anyMatch(excerpt -> !consent.knowledgeSourceIds().contains(excerpt.sourceId())))) {
            throw invalid("Local retrieval does not authorize cloud sharing. Authorize the selected knowledge sources for this request first.");
        }
    }
    private static InvalidConfigurationException invalid(String message) { return new InvalidConfigurationException("dataSharingConsent", message); }
}
