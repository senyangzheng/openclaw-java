package com.openclaw.plugins.demo;

import com.openclaw.plugin.OpenClawPlugin;
import com.openclaw.plugin.PluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bundled demo plugin. Proves the loader works end-to-end without requiring any
 * external jar: registers a single greeter bean and logs a banner on load.
 *
 * <p><b>Opt-out</b>: set
 * {@code openclaw.plugins.exclude: [hello]} in {@code application.yml} to skip.
 */
public class HelloPlugin implements OpenClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(HelloPlugin.class);

    private static final String ID = "hello";

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
            + "'/hello' chat command that short-circuits the auto-reply pipeline.";
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
        context.registerSingleton("chatCommand", () -> new HelloChatCommand(greeter));
    }

    @Override
    public void onUnload() {
        log.info("plugin.hello.onUnload");
    }
}
