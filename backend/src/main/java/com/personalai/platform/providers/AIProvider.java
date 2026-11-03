package com.personalai.platform.providers;

/** Provider adapters receive validated execution context; business services own orchestration. */
public interface AIProvider {
    String id();
    String generate(ProviderRequest request);
}
