package com.personalai.platform.providers;

/** Public connection metadata deliberately contains no credentials. */
public record ProviderConfiguration(String id, String name, boolean enabled, boolean adapterAvailable,
        boolean configured, boolean mock) { }
