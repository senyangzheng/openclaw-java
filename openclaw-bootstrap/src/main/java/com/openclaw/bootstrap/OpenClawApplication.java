package com.openclaw.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Top-level entry point. M1 scope:
 * <ul>
 *   <li>wires common / logging / config / secrets / providers-api / sessions / routing;</li>
 *   <li>registers the Web channel adapter ({@code /api/channels/web/messages}) and
 *       the gateway HTTP entry point ({@code /api/gateway});</li>
 *   <li>exposes {@code /actuator/health} and a tiny {@code /hello};</li>
 *   <li>defaults to {@link com.openclaw.providers.api.mock.EchoMockProviderClient} — real
 *       Gemini / Qwen providers land in M2.</li>
 * </ul>
 * CLI mode is activated via {@code -Dopenclaw.cli.enabled=true}; see
 * {@code bin/openclaw-java}.
 */
@SpringBootApplication(scanBasePackages = "com.openclaw")
public class OpenClawApplication {

    private static final Logger log = LoggerFactory.getLogger(OpenClawApplication.class);

    public static void main(final String[] args) {
        final long start = System.currentTimeMillis();
        SpringApplication.run(OpenClawApplication.class, args);
        log.info("openclaw.bootstrap.ready elapsedMs={}", System.currentTimeMillis() - start);
    }
}
