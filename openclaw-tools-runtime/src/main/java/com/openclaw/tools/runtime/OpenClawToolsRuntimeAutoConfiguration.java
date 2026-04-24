package com.openclaw.tools.runtime;

import com.openclaw.hooks.HookRunner;
import com.openclaw.tools.runtime.hook.AdjustedParamsStore;
import com.openclaw.tools.runtime.hook.AfterToolCallHookEmitter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Wires the tools runtime skeleton. Activated when a {@link HookRunner} bean is present (i.e. the hooks
 * runtime module is on the classpath). The 9-step policy pipeline content and the outer 5-step assembly
 * arrive in M3.2; this M3.1 autoconfig registers only the registry, the adjusted-params store and the
 * after-hook emitter.
 */
@AutoConfiguration
@ConditionalOnBean(HookRunner.class)
public class OpenClawToolsRuntimeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public AdjustedParamsStore adjustedParamsStore() {
        return new AdjustedParamsStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public AfterToolCallHookEmitter afterToolCallHookEmitter(final HookRunner hookRunner,
                                                             final AdjustedParamsStore store) {
        return new AfterToolCallHookEmitter(hookRunner, store);
    }
}
