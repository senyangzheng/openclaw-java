package com.openclaw.bootstrap.web;

import com.openclaw.config.OpenClawProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Tiny smoke endpoint that lets us verify M0 is wired end-to-end
 * (configuration properties + web layer + Fastjson2 serialization).
 */
@RestController
public class HelloController {

    private final OpenClawProperties properties;

    public HelloController(final OpenClawProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        return Map.of(
            "app", "openclaw-java",
            "profile", properties.profile(),
            "nodeName", properties.nodeName(),
            "timestamp", Instant.now().toString()
        );
    }
}
