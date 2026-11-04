package com.personalai.platform.providers;

import com.personalai.platform.common.InvalidConfigurationException;

/**
 * An adapter may throw this ONLY when it knows no request was dispatched and no billable work occurred.
 * Timeouts or ambiguous network failures must use a normal failure, never this retry signal.
 */
public class ProviderUnavailableException extends InvalidConfigurationException {
    public ProviderUnavailableException() { super("providerId", "The provider is temporarily unavailable before request dispatch."); }
}
