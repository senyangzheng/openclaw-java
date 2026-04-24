package com.openclaw.hooks;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code openclaw.hooks.*} binding. Currently only {@code catchErrors} (default {@code true}; never disable in
 * production).
 */
@ConfigurationProperties(prefix = "openclaw.hooks")
public class HookRuntimeProperties {

    /** Default for {@link HookRunner#runModifyingHook(String, Object, HookContext, Object, java.util.function.BiFunction)}. */
    private boolean catchErrors = true;

    public boolean isCatchErrors() {
        return catchErrors;
    }

    public void setCatchErrors(final boolean catchErrors) {
        this.catchErrors = catchErrors;
    }
}
