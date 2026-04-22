package com.openclaw.secrets.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Crypto configuration for {@code openclaw-secrets}. Exposed at
 * {@code openclaw.secrets.crypto.*} in {@code application.yml}.
 *
 * <p>The <b>KEK</b> (Key Encryption Key) is an AES-256 master key. It MUST be
 * supplied via an environment variable in real deployments — never checked in.
 * Examples:
 * <pre>
 *   export OPENCLAW_SECRETS_KEK_BASE64="$(openssl rand -base64 32)"
 * </pre>
 * The corresponding property binds automatically:
 * {@code openclaw.secrets.crypto.kek-base64=${OPENCLAW_SECRETS_KEK_BASE64}}.
 *
 * <p>Rotating the KEK means re-encrypting every DEK (Data Encryption Key) in
 * {@code oc_auth_profile}. A dedicated migration task is out of scope for M2.3.
 */
@ConfigurationProperties(prefix = "openclaw.secrets.crypto")
public class SecretsCryptoProperties {

    /** Base64-encoded 32-byte AES master key. Required when {@code store=jdbc}. */
    private String kekBase64;

    public String getKekBase64() {
        return kekBase64;
    }

    public void setKekBase64(final String kekBase64) {
        this.kekBase64 = kekBase64;
    }
}
