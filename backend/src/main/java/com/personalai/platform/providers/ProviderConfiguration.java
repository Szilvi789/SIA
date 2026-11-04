package com.personalai.platform.providers;

/** Public connection metadata deliberately contains no credentials. */
public record ProviderConfiguration(String id, String name, boolean enabled, boolean adapterAvailable,
        boolean configured, boolean mock, com.personalai.platform.privacy.DataLocality dataLocality) {
    public ProviderConfiguration(String id, String name, boolean enabled, boolean adapterAvailable, boolean configured, boolean mock) {
        this(id, name, enabled, adapterAvailable, configured, mock, mock ? com.personalai.platform.privacy.DataLocality.LOCAL : com.personalai.platform.privacy.DataLocality.UNKNOWN);
    }
}
