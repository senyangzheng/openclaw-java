package com.openclaw.tools.runtime.policy;

import java.util.Objects;

/**
 * Attaches "why was this tool kept / stripped" context to a {@link PolicyStep}. Mirrors ts
 * {@code agents/tool-policy-pipeline.ts} provenance labels used in diagnostic log lines like
 * {@code tools.profile (balanced) stripped 3 unknown entries}.
 *
 * @param stepLabel         human-readable step identifier (e.g. {@code "tools.profile (balanced)"})
 * @param stripPluginOnlyAllowlist whether this step's allow-list may strip {@code pluginOnly=true} tools
 */
public record PolicyProvenance(String stepLabel, boolean stripPluginOnlyAllowlist) {

    public PolicyProvenance {
        Objects.requireNonNull(stepLabel, "stepLabel");
    }
}
