package com.openclaw.common.error;

/**
 * Contract for all openclaw error codes.
 * <p>
 * Each module may publish its own enum implementing this interface; the {@link #code()}
 * value must be globally unique (prefixed with the module name, e.g. {@code GATEWAY_4001}).
 * <p>
 * Grouped by HTTP-style classes:
 * <ul>
 *   <li>{@code 4xxx} &ndash; client / input errors</li>
 *   <li>{@code 5xxx} &ndash; server / infrastructure errors</li>
 *   <li>{@code 6xxx} &ndash; upstream (provider / channel) errors</li>
 * </ul>
 */
public interface ErrorCode {

    /** Globally unique machine-readable code. */
    String code();

    /** Default human readable message in English; i18n is resolved at the edge. */
    String defaultMessage();
}
