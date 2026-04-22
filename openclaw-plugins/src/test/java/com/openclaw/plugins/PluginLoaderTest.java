package com.openclaw.plugins;

import com.openclaw.autoreply.command.ChatCommand;
import com.openclaw.channels.core.InboundMessage;
import com.openclaw.plugins.demo.HelloChatCommand;
import com.openclaw.plugins.demo.HelloGreeter;
import com.openclaw.routing.RoutingKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot-level test for the plugin loader. Uses
 * {@link ApplicationContextRunner} rather than {@code @SpringBootTest} so we
 * stay off Spring Boot's mockito-reset listeners — JDK 21 self-attach is
 * unavailable in CI sandboxes and was blowing up the original attempt.
 *
 * <p>Verifies:
 * <ol>
 *   <li>The bundled {@link com.openclaw.plugins.demo.HelloPlugin} is discovered
 *       via {@link java.util.ServiceLoader} — no manual registration needed.</li>
 *   <li>Its {@code onLoad} callback registered a {@link HelloGreeter} bean
 *       under the mangled name {@code plugin.hello.greeter}.</li>
 *   <li>The {@link PluginRegistry} exposes an up-to-date descriptor.</li>
 *   <li>An explicit {@code exclude} short-circuits the demo plugin.</li>
 * </ol>
 */
class PluginLoaderTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OpenClawPluginsAutoConfiguration.class));

    @Test
    void shouldDiscoverAndLoadBundledHelloPlugin() {
        runner.run(context -> {
            final PluginRegistry registry = context.getBean(PluginRegistry.class);
            assertThat(registry.descriptors())
                .extracting("id")
                .contains("hello");

            final var descriptor = registry.find("hello").orElseThrow();
            assertThat(descriptor.version()).isEqualTo("0.1.0");
            assertThat(descriptor.className()).isEqualTo("com.openclaw.plugins.demo.HelloPlugin");
        });
    }

    @Test
    void shouldRegisterGreeterBeanFromPlugin() {
        runner.run(context -> {
            assertThat(context.containsBean("plugin.hello.greeter")).isTrue();
            final HelloGreeter greeter =
                context.getBean("plugin.hello.greeter", HelloGreeter.class);
            assertThat(greeter.greet("alice")).contains("alice", "hello plugin");
        });
    }

    @Test
    void shouldRegisterChatCommandBeanFromPlugin() {
        runner.run(context -> {
            assertThat(context.containsBean("plugin.hello.chatCommand")).isTrue();
            final ChatCommand cmd = context.getBean("plugin.hello.chatCommand", ChatCommand.class);
            assertThat(cmd).isInstanceOf(HelloChatCommand.class);
            assertThat(cmd.name()).isEqualTo("hello");

            final InboundMessage match = new InboundMessage(
                UUID.randomUUID().toString(),
                RoutingKey.of("web", "anon", "c-1"),
                "/hello alice", null, null);
            assertThat(cmd.matches(match)).isTrue();
            assertThat(cmd.handle(match)).contains("alice");

            final InboundMessage noMatch = new InboundMessage(
                UUID.randomUUID().toString(),
                RoutingKey.of("web", "anon", "c-1"),
                "helloworld", null, null);
            assertThat(cmd.matches(noMatch)).isFalse();
        });
    }

    @Test
    void shouldSkipPluginsInExcludeList() {
        runner.withPropertyValues("openclaw.plugins.exclude=hello")
            .run(context -> {
                final PluginRegistry registry = context.getBean(PluginRegistry.class);
                assertThat(registry.find("hello")).isEmpty();
                assertThat(registry.descriptors()).isEmpty();
                assertThat(context.containsBean("plugin.hello.greeter")).isFalse();
            });
    }

    @Test
    void shouldSkipAllPluginsWhenDisabled() {
        runner.withPropertyValues("openclaw.plugins.enabled=false")
            .run(context -> {
                final PluginRegistry registry = context.getBean(PluginRegistry.class);
                assertThat(registry.descriptors()).isEmpty();
                assertThat(context.containsBean("plugin.hello.greeter")).isFalse();
            });
    }
}
