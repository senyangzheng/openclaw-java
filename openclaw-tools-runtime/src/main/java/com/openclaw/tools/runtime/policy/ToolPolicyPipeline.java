package com.openclaw.tools.runtime.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.openclaw.tools.Tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composes {@link PolicyStep}s into the <b>inner 9-step pipeline</b> described by
 * {@code .cursor/plan/05-translation-conventions.md} §13.2. Full step implementations land in M3.2; this
 * class is the M3.1 skeleton that accepts an externally-built step list and applies them in order,
 * preserving the hard invariant that the <i>outer</i> 5-step assembly wraps this pipeline (see plan §13.1).
 *
 * <p>For each step the pipeline logs {@code tools.policy.step label={} in={} out={}} so diagnostics can be
 * reconstructed from log lines alone.
 */
public final class ToolPolicyPipeline {

    private static final Logger log = LoggerFactory.getLogger(ToolPolicyPipeline.class);

    private final List<PolicyStep> steps;

    public ToolPolicyPipeline(final List<PolicyStep> steps) {
        Objects.requireNonNull(steps, "steps");
        this.steps = List.copyOf(steps);
    }

    /** Apply every step sequentially. Input list is not mutated. */
    public List<Tool> apply(final List<Tool> tools) {
        Objects.requireNonNull(tools, "tools");
        List<Tool> current = new ArrayList<>(tools);
        for (final PolicyStep step : steps) {
            final int before = current.size();
            current = new ArrayList<>(step.apply(List.copyOf(current)));
            log.debug("tools.policy.step label={} in={} out={}",
                    step.provenance().stepLabel(), before, current.size());
        }
        return List.copyOf(current);
    }

    public List<PolicyStep> steps() {
        return steps;
    }
}
