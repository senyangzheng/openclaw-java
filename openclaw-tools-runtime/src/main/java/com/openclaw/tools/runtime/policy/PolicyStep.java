package com.openclaw.tools.runtime.policy;

import java.util.List;

import com.openclaw.tools.Tool;

/**
 * A single stage in the {@link ToolPolicyPipeline inner 9-step pipeline}. Each step receives the current
 * list of tools and returns a (potentially) reduced / augmented list. The concrete implementations for the
 * 9 canonical steps land in M3.2 ({@code tools.profile}, {@code tools.global}, {@code tools.agent}, ...).
 *
 * <p>Steps MUST be stateless; the pipeline applies them sequentially and logs a one-line summary tagged with
 * {@link #provenance()} so the diagnostic trail is reproducible.
 */
public interface PolicyStep {

    /** Provenance metadata for log / diagnostics output. */
    PolicyProvenance provenance();

    /** Apply one step of allow-listing / stripping. Must not mutate the input list. */
    List<Tool> apply(List<Tool> tools);
}
