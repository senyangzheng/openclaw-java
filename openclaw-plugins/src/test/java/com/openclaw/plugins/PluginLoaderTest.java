package com.openclaw.plugins;

import com.openclaw.agents.core.hooks.BeforeAgentStartEvent;
import com.openclaw.hooks.HookContext;
import com.openclaw.hooks.HookNames;
import com.openclaw.hooks.HookOutcome;
import com.openclaw.hooks.HookRunner;
import com.openclaw.hooks.ModifyingHookResult;
import com.openclaw.plugins.demo.HelloGreeter;
import com.openclaw.providers.api.ChatMessage;
import com.openclaw.sessions.SessionKey;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot-level test for the plugin loader. Uses {@link ApplicationContextRunner} rather than
 * {@code @SpringBootTest} so we stay off Spring Boot's mockito-reset listeners — JDK 21 self-attach is
 * unavailable in CI sandboxes and was blowing up the original attempt.
 *
 * <p>Verifies:
 * <ol>
 *   <li>The bundled {@link com.openclaw.plugins.demo.HelloPlugin} is discovered via
 *       {@link java.util.ServiceLoader} — no manual registration needed.</li>
 *   <li>Its {@code onLoad} callback registered a {@link HelloGreeter} bean under the mangled name
 *       {@code plugin.hello.greeter}.</li>
 *   <li>Its {@code onLoad} callback registered a {@code before_agent_start} hook that short-circuits on
 *       {@code "/hello <name>"} and is invisible for any other input (replaces the deleted
 *       {@code ChatCommand} SPI).</li>
 *   <li>The {@link PluginRegistry} exposes an up-to-date descriptor.</li>
 *   <li>An explicit {@code exclude} short-circuits the demo plugin.</li>
 * </ol>
 */
class PluginLoaderTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenClawPluginsAutoConfiguration.class))
            .withBean(HookRunner.class);

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
    void shouldRegisterBeforeAgentStartHookFromPlugin() {
        runner.run(context -> {
            final HookRunner hookRunner = context.getBean(HookRunner.class);
            final SessionKey sessionKey = new SessionKey("web", "anon", "c-1");

            // /hello alice → short-circuit
            final BeforeAgentStartEvent match = new BeforeAgentStartEvent(
                    sessionKey, ChatMessage.user("/hello alice"), java.util.List.of(), java.util.Map.of());
            final ModifyingHookResult<Object> matched = hookRunner.runModifyingHook(
                    HookNames.BEFORE_AGENT_START,
                    match,
                    HookContext.of(HookNames.BEFORE_AGENT_START, java.util.Map.of()),
                    new Object(),
                    (acc, delta) -> acc);
            assertThat(matched.isShortCircuit()).isTrue();
            assertThat(matched.shortCircuit()).contains("alice");

            // anything else → chain continues (no short-circuit)
            final BeforeAgentStartEvent noMatch = new BeforeAgentStartEvent(
                    sessionKey, ChatMessage.user("helloworld"), java.util.List.of(), java.util.Map.of());
            final ModifyingHookResult<Object> continued = hookRunner.runModifyingHook(
                    HookNames.BEFORE_AGENT_START,
                    noMatch,
                    HookContext.of(HookNames.BEFORE_AGENT_START, java.util.Map.of()),
                    new Object(),
                    (acc, delta) -> acc);
            assertThat(continued.isShortCircuit()).isFalse();
        });
    }

    @Test
    void shouldLeaveNoShortCircuitHookWhenPluginIsExcluded() {
        runner.withPropertyValues("openclaw.plugins.exclude=hello")
                .run(context -> {
                    final PluginRegistry registry = context.getBean(PluginRegistry.class);
                    assertThat(registry.find("hello")).isEmpty();
                    assertThat(registry.descriptors()).isEmpty();
                    assertThat(context.containsBean("plugin.hello.greeter")).isFalse();

                    final HookRunner hookRunner = context.getBean(HookRunner.class);
                    final BeforeAgentStartEvent match = new BeforeAgentStartEvent(
                            new SessionKey("web", "anon", "c-1"),
                            ChatMessage.user("/hello alice"), java.util.List.of(), java.util.Map.of());
                    final ModifyingHookResult<Object> res = hookRunner.runModifyingHook(
                            HookNames.BEFORE_AGENT_START, match,
                            HookContext.of(HookNames.BEFORE_AGENT_START, java.util.Map.of()),
                            new Object(), (acc, delta) -> acc);
                    assertThat(res.isShortCircuit()).isFalse();
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

    // No-op handler guards stop the unused import list from being misleading in the compiled file.
    @SuppressWarnings("unused")
    private static final HookOutcome GUARD_HOOK_OUTCOME_REFERENCE = HookOutcome.EMPTY;
}
