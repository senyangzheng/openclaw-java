package com.openclaw.providers.registry;

import com.openclaw.common.error.ErrorCode;

/** Error codes owned by {@code openclaw-providers-registry}. */
public enum ProviderRegistryErrorCode implements ErrorCode {

    /** Every preferred provider was exhausted (attempted or skipped on cooldown) without success. */
    ALL_PROVIDERS_EXHAUSTED("REGISTRY_5030", "All registered providers failed"),
    /** No {@link com.openclaw.providers.api.ProviderClient} was registered at all. */
    NO_PROVIDER_REGISTERED("REGISTRY_5031", "No ProviderClient is registered"),
    /** Caller-side abort must not trigger failover — surfaced separately so the caller can distinguish. */
    PROVIDER_CALL_ABORTED("REGISTRY_4999", "Provider call was aborted by the caller");

    private final String code;
    private final String defaultMessage;

    ProviderRegistryErrorCode(final String code, final String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
