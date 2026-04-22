package com.openclaw.logging;

/**
 * Canonical MDC key names. Every request/event entrypoint MUST populate at least
 * {@link #REQUEST_ID} so that downstream logs are correlatable.
 */
public final class MdcKeys {

    public static final String REQUEST_ID = "requestId";
    public static final String SESSION_ID = "sessionId";
    public static final String USER_ID = "userId";
    public static final String TENANT_ID = "tenantId";
    public static final String CHANNEL = "channel";
    public static final String AGENT_ID = "agentId";
    public static final String PROVIDER = "provider";

    private MdcKeys() {
    }
}
