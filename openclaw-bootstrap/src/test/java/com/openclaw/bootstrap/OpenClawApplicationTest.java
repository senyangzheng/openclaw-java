package com.openclaw.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenClawApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Test
    void shouldExposeActuatorHealth() {
        final RestTemplate rest = restTemplateBuilder.build();

        @SuppressWarnings("unchecked")
        final Map<String, Object> body = rest.getForObject("http://localhost:" + port + "/actuator/health", Map.class);

        assertThat(body).isNotNull();
        assertThat(body).containsEntry("status", "UP");
    }

    @Test
    void shouldReturnHelloWithProfileAndNodeName() {
        final RestTemplate rest = restTemplateBuilder.build();

        @SuppressWarnings("unchecked")
        final Map<String, Object> body = rest.getForObject("http://localhost:" + port + "/hello", Map.class);

        assertThat(body).isNotNull();
        assertThat(body).containsEntry("app", "openclaw-java");
        assertThat(body).containsEntry("profile", "test");
        assertThat(body).containsEntry("nodeName", "claw-test");
    }
}
