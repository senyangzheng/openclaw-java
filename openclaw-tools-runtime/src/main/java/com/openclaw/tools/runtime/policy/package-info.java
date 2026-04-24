/**
 * {@link com.openclaw.tools.runtime.policy.ToolPolicyPipeline} skeleton — the inner 9-step allow-list /
 * strip sequence (see {@code .cursor/plan/05-translation-conventions.md} §13.2). M3.1 provides the
 * composition / diagnostic logging shell only; concrete {@link com.openclaw.tools.runtime.policy.PolicyStep}
 * implementations for {@code tools.profile}, {@code tools.global}, {@code tools.agent},
 * {@code tools.approvalCache}, {@code tools.dedupe}, {@code tools.subagent.allowlist}, {@code tools.sandbox},
 * {@code tools.schema}, {@code tools.order} land in M3.2.
 */
package com.openclaw.tools.runtime.policy;
