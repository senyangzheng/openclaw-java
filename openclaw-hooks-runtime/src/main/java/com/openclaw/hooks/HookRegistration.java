package com.openclaw.hooks;

/**
 * Identifier handle returned when registering a hook. Can be used for deregistration and diagnostics.
 *
 * @param hookName    canonical hook name (see {@link HookNames})
 * @param handlerId   stable id supplied by the registrant (usually {@code pluginId + ":" + logicalName})
 * @param priority    higher priority runs earlier; same priority preserves registration order
 * @param registerSeq monotonic registration counter, acts as tiebreaker for equal priority
 * @param modifying   {@code true} for {@link ModifyingHookHandler}, {@code false} for {@link VoidHookHandler}
 */
public record HookRegistration(String hookName,
                               String handlerId,
                               int priority,
                               long registerSeq,
                               boolean modifying) {

    public HookRegistration {
        if (hookName == null || hookName.isBlank()) {
            throw new IllegalArgumentException("hookName is required");
        }
        if (handlerId == null || handlerId.isBlank()) {
            throw new IllegalArgumentException("handlerId is required");
        }
    }
}
