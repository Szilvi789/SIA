package com.personalai.platform.providers;

/** Provider adapters receive validated execution context; business services own orchestration. */
public interface AIProvider {
    String id();
    String generate(ProviderRequest request);

    /** Adapters may report real provider usage without fabricating counts for legacy/mock output. */
    default ProviderResult generateResult(ProviderRequest request) {
        return new ProviderResult(generate(request), null);
    }

    default boolean configured() { return "mock".equals(id()); }
    default boolean mock() { return "mock".equals(id()); }
    default com.personalai.platform.privacy.DataLocality dataLocality() {
        return mock() ? com.personalai.platform.privacy.DataLocality.LOCAL : com.personalai.platform.privacy.DataLocality.UNKNOWN;
    }
}
