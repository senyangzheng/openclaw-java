package com.openclaw.plugins.demo;

import com.openclaw.agents.core.hooks.BeforeAgentStartEvent;
import com.openclaw.hooks.HookNames;
import com.openclaw.hooks.HookOutcome;
import com.openclaw.hooks.HookRegistration;
import com.openclaw.hooks.HookRunner;
import com.openclaw.hooks.ModifyingHookHandler;
import com.openclaw.plugin.CapabilityType;
import com.openclaw.plugin.OpenClawPlugin;
import com.openclaw.plugin.PluginContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bundled demo plugin. Proves the loader works end-to-end without requiring any external jar: registers a
 * {@link HelloGreeter} singleton and a {@code before_agent_start} hook that handles
 * {@code /hello [name]} by returning a deterministic greeting, skipping the provider.
 *
 * <p><b>Migration note (M3 / A3)</b>: before M3 this plugin registered a {@code ChatCommand} bean. That SPI
 * was redundant with the more general hook mechanism, so it was deleted and this plugin now wires directly
 * into {@link HookRunner#registerModifying(String, String, int, com.openclaw.hooks.ModifyingHookHandler)}.
 * The user-facing behavior (send {@code "/hello alice"} → reply {@code "hello, alice — from ..."}) is
 * unchanged.
 *
 * <p><b>Opt-out</b>: set {@code openclaw.plugins.exclude: [hello]} in {@code application.yml} to skip.
 */
public class HelloPlugin implements OpenClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(HelloPlugin.class);

    private static final String ID = "hello";
    /** Trigger prefix. Anything else is ignored and falls through to the LLM. */
    public static final String TRIGGER = "/hello";

    private HookRegistration hookRegistration;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String version() {
        return "0.1.0";
    }

    @Override
    public String description() {
        return "Built-in demo plugin. Registers plugin.hello.greeter bean + "
                + "a '/hello' before_agent_start hook that short-circuits the auto-reply pipeline.";
    }

    @Override
    public int order() {
        // run early so later plugins can observe the demo bean in their onLoad hooks
        return -100;
    }

    @Override
    public void onLoad(final PluginContext context) {
        log.info("plugin.hello.onLoad pluginId={} activeProfiles={}",
                context.pluginId(), (Object) context.environment().getActiveProfiles());

        final HelloGreeter greeter = context.registerSingleton("greeter", HelloGreeter::new);
        final HookRunner hookRunner = context.beanFactory().getBean(HookRunner.class);

        final ModifyingHookHandler<BeforeAgentStartEvent> handler =
                (event, ctx) -> tryGreet(greeter, event);
        this.hookRegistration = hookRunner.registerModifying(
                HookNames.BEFORE_AGENT_START,
                "plugin.hello.command",
                /* priority */ 100,
                handler);
        // Record the hook registration in the capability registry too — makes it visible to
        // PluginRegistry.diagnostics / admin endpoints without any extra bookkeeping on the plugin side.
        // HOOK is ALLOW_MULTIPLE so this never conflicts with the agents-core internal users.
        context.registerCapability(CapabilityType.HOOK, HookNames.BEFORE_AGENT_START, hookRegistration);
        log.info("plugin.hello.hook.registered name={} handler={}",
                HookNames.BEFORE_AGENT_START, "plugin.hello.command");
    }

    @Override
    public void onUnload() {
        if (hookRegistration != null) {
            log.info("plugin.hello.onUnload removing hook {}#{}",
                    hookRegistration.hookName(), hookRegistration.handlerId());
            hookRegistration = null;
        }
    }

    private static HookOutcome tryGreet(final HelloGreeter greeter, final BeforeAgentStartEvent event) {
        if (event.userMessage() == null) {
            return HookOutcome.EMPTY;
        }
        final String raw = event.userMessage().content();
        if (raw == null) {
            return HookOutcome.EMPTY;
        }
        final String stripped = raw.stripLeading();
        if (!stripped.equals(TRIGGER)
                && !stripped.startsWith(TRIGGER + " ")
                && !stripped.startsWith(TRIGGER + "\t")) {
            return HookOutcome.EMPTY;
        }
        final String rest = stripped.substring(TRIGGER.length()).trim();
        final String name = rest.isEmpty() ? "friend" : rest;
        final String reply = greeter.greet(name);
        log.info("plugin.hello.shortcircuit replyLen={}", reply.length());
        return HookOutcome.shortCircuit(reply);
    }
}
