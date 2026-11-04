package com.personalai.platform.privacy;

import static org.junit.jupiter.api.Assertions.*;

import com.personalai.platform.agents.Agent;
import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.knowledge.KnowledgeExcerpt;
import com.personalai.platform.models.InMemoryModelRepository;
import com.personalai.platform.models.ModelCatalog;
import com.personalai.platform.models.ModelDescriptor;
import com.personalai.platform.models.ModelResolver;
import com.personalai.platform.models.ModelSelection;
import com.personalai.platform.models.ModelSettings;
import com.personalai.platform.providers.AIProvider;
import com.personalai.platform.providers.ProviderExecutionService;
import com.personalai.platform.providers.ProviderRegistry;
import com.personalai.platform.providers.ProviderRequest;
import com.personalai.platform.providers.ProviderUnavailableException;
import com.personalai.platform.rules.ResolvedInstructions;
import com.personalai.platform.usage.UsageService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CloudDataSharingPolicyTest {
    private final Adapter cloud = new Adapter("openai", DataLocality.EXTERNAL);
    private final Adapter local = new Adapter("local", DataLocality.LOCAL);
    private final ProviderRegistry providers = new ProviderRegistry(List.of(cloud, local));
    private final InMemoryProviderDataPermissionRepository permissions = new InMemoryProviderDataPermissionRepository();
    private final CloudDataSharingPolicy policy = new CloudDataSharingPolicy(permissions, providers, true);

    @Test
    void absentPermissionsDenyEveryExternalContentCategory() {
        var permission = policy.permission("openai");
        assertFalse(permission.enabled());
        assertFalse(permission.taskContent());
        assertFalse(permission.personalInstructions());
        assertFalse(permission.knowledgePassages());
        assertThrows(InvalidConfigurationException.class,
                () -> policy.validate(cloud, request("openai", List.of()), consent("openai", Set.of())));
    }

    @Test
    void localProcessingRemainsAvailableWithoutCloudConsentIncludingRetrievedPassages() {
        var demo = new CloudDataSharingPolicy(permissions, providers, false);
        assertDoesNotThrow(() -> demo.validate(local,
                request("local", List.of(new KnowledgeExcerpt("source-one", "Synthetic passage"))), null));
    }

    @Test
    void undeclaredAndNullAdapterLocalitiesRemainBlockedEvenWithPermissionAndConsent() {
        allowCloud(true);
        AIProvider undeclared = new AIProvider() {
            public String id() { return "openai"; }
            public String generate(ProviderRequest request) { fail("A blocked adapter must not execute."); return ""; }
        };
        assertEquals(DataLocality.UNKNOWN, undeclared.dataLocality());
        assertThrows(InvalidConfigurationException.class,
                () -> policy.validate(undeclared, request("openai", List.of()), consent("openai", Set.of())));
        var nullLocality = new Adapter("openai", null);
        assertThrows(InvalidConfigurationException.class,
                () -> policy.validate(nullLocality, request("openai", List.of()), consent("openai", Set.of())));
    }

    @Test
    void demoModeCannotEnableCloudSharingOrDispatchDespitePreexistingPermissions() {
        var demo = new CloudDataSharingPolicy(permissions, providers, false);
        assertThrows(InvalidConfigurationException.class,
                () -> demo.save("openai", new ProviderDataPermission("openai", true, true, true, true)));
        allowCloud(true);
        assertThrows(InvalidConfigurationException.class,
                () -> demo.validate(cloud, request("openai", List.of()), consent("openai", Set.of())));
        assertDoesNotThrow(() -> demo.save("openai", ProviderDataPermission.denied("openai")));
    }

    @Test
    void permissionsCannotBeSavedForAnotherProviderAndNullCategoriesDefaultToDenied() {
        assertThrows(InvalidConfigurationException.class,
                () -> policy.save("openai", ProviderDataPermission.denied("local")));
        assertThrows(InvalidConfigurationException.class, () -> policy.save("openai", null));
        var permission = policy.save("openai", new ProviderDataPermission("openai", null, null, null, null));
        assertEquals(ProviderDataPermission.denied("openai"), permission);
    }

    @Test
    void everyProviderLevelTaskAndInstructionCategoryRequiresExplicitPermission() {
        for (var permission : List.of(
                new ProviderDataPermission("openai", false, true, true, true),
                new ProviderDataPermission("openai", true, false, true, true),
                new ProviderDataPermission("openai", true, true, false, true))) {
            policy.save("openai", permission);
            assertThrows(InvalidConfigurationException.class,
                    () -> policy.validate(cloud, request("openai", List.of()), consent("openai", Set.of())));
        }
    }

    @Test
    void eachTaskNeedsFreshConsentBoundToItsEffectiveProviderAndContentCategories() {
        allowCloud(false);
        var request = request("openai", List.of());
        assertThrows(InvalidConfigurationException.class, () -> policy.validate(cloud, request, null));
        for (var consent : List.of(
                consent("local", Set.of()),
                new DataSharingConsent("openai", false, true, Set.of()),
                new DataSharingConsent("openai", true, false, Set.of()),
                new DataSharingConsent("openai", null, true, null))) {
            assertThrows(InvalidConfigurationException.class, () -> policy.validate(cloud, request, consent));
        }
        assertDoesNotThrow(() -> policy.validate(cloud, request, consent("openai", Set.of())));
    }

    @Test
    void localRetrievalRequiresProviderKnowledgePermissionAndExplicitConsentForEverySource() {
        var request = request("openai", List.of(new KnowledgeExcerpt("source-one", "Synthetic first passage"),
                new KnowledgeExcerpt("source-two", "Synthetic second passage")));
        allowCloud(false);
        assertThrows(InvalidConfigurationException.class,
                () -> policy.validate(cloud, request, consent("openai", Set.of("source-one", "source-two"))));
        allowCloud(true);
        assertThrows(InvalidConfigurationException.class,
                () -> policy.validate(cloud, request, consent("openai", Set.of())));
        assertThrows(InvalidConfigurationException.class,
                () -> policy.validate(cloud, request, consent("openai", Set.of("source-one"))));
        assertDoesNotThrow(() -> policy.validate(cloud, request, consent("openai", Set.of("source-one", "source-two"))));
    }

    @Test
    void fallbackDoesNotReuseConsentForADifferentProviderOrSendAnyUnauthorizedBytes() {
        providers.setEnabled("local", true);
        providers.setEnabled("openai", true);
        var models = new ModelCatalog(new InMemoryModelRepository(), providers);
        models.save("primary", priced("primary", "local", "2"));
        models.save("fallback", priced("fallback", "openai", "1"));
        var agent = new Agent("coding-agent", "Synthetic agent", "Synthetic configuration", "Synthetic instructions",
                "local", "primary", 0.2, 128, List.of(), List.of(), List.of(),
                new ModelSettings(null, "fallback", true, Map.of(), null, null, null, true));
        local.unavailable = true;
        allowCloud(true);
        var usage = new UsageService(Clock.systemUTC());
        var execution = new ProviderExecutionService(new ModelResolver(models, providers), providers, usage, policy);
        var selection = new ModelSelection(null, null, null, null, null, true, null, consent("local", Set.of()));

        assertThrows(InvalidConfigurationException.class, () -> execution.execute(agent, selection, Set.of("TEXT"), "CHAT",
                resolved -> new ProviderRequest(agent, resolved.model(), "Synthetic task",
                        new ResolvedInstructions("Synthetic instructions", List.of()), List.of(), List.of())));

        assertEquals(1, local.calls);
        assertEquals(0, cloud.calls);
        assertEquals(2, usage.findAll().size());
        assertTrue(usage.findAll().stream().allMatch(record -> "FAILED".equals(record.status())));
    }

    private void allowCloud(boolean knowledge) {
        policy.save("openai", new ProviderDataPermission("openai", true, true, true, knowledge));
    }

    private static DataSharingConsent consent(String provider, Set<String> sources) {
        return new DataSharingConsent(provider, true, true, sources);
    }

    private static ProviderRequest request(String provider, List<KnowledgeExcerpt> knowledge) {
        var agent = new Agent("coding-agent", "Synthetic agent", "Synthetic configuration", "Synthetic instructions",
                provider, "synthetic-model", 0.2, 128, List.of());
        return new ProviderRequest(agent, new ModelDescriptor("synthetic-model", "Synthetic model", provider, "Synthetic fixture"),
                "Synthetic task", new ResolvedInstructions("Synthetic instructions", List.of()), knowledge, List.of());
    }

    private static ModelDescriptor priced(String id, String provider, String price) {
        return new ModelDescriptor(id, id, provider, "Synthetic test model", id + "-api", true, Set.of("TEXT"),
                null, null, new BigDecimal(price), new BigDecimal(price), "EUR", 1_000_000L,
                Instant.parse("2026-11-04T00:00:00Z"), Set.of(), Set.of(), false, false, false, false);
    }

    private static final class Adapter implements AIProvider {
        private final String id;
        private final DataLocality locality;
        private boolean unavailable;
        private int calls;

        private Adapter(String id, DataLocality locality) { this.id = id; this.locality = locality; }
        public String id() { return id; }
        public boolean configured() { return true; }
        public DataLocality dataLocality() { return locality; }
        public String generate(ProviderRequest request) {
            calls++;
            if (unavailable) { throw new ProviderUnavailableException(); }
            return "Synthetic response";
        }
    }
}
