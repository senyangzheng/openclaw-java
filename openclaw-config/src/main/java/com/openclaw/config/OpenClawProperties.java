package com.openclaw.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Root configuration properties for openclaw runtime.
 * <p>
 * Additional per-module properties must live in their own module (e.g.
 * {@code com.openclaw.gateway.GatewayProperties}); this class only holds
 * settings that apply to the runtime as a whole.
 *
 * <p>Source is {@code application.yml} with prefix {@code openclaw}:
 * <pre>
 * openclaw:
 *   profile: dev
 *   node-name: claw-local
 *   startup-timeout: 30s
 * </pre>
 */
@ConfigurationProperties(prefix = "openclaw")
@Validated
public record OpenClawProperties(
    @NotBlank String profile,
    @NotBlank String nodeName,
    Duration startupTimeout
) {

    public OpenClawProperties {
        if (startupTimeout == null) {
            startupTimeout = Duration.ofSeconds(30);
        }
    }
}
