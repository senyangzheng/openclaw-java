package com.openclaw.lanes;

/**
 * Built-in global lane categories.
 * <p>
 * Mirrors openclaw TS {@code CommandLane} enum referenced from
 * {@code src/gateway/server-lanes.ts · applyGatewayLaneConcurrency}.
 *
 * <p>Default {@link #MAIN} when callers don't specify a global lane.
 *
 * <p>See {@code .cursor/plan/04-milestones.md} M3.3 and
 * {@code .cursor/plan/05-translation-conventions.md} §14.
 */
public enum CommandLane {

    MAIN("main"),
    CRON("cron"),
    SUBAGENT("subagent");

    private final String laneName;

    CommandLane(final String laneName) {
        this.laneName = laneName;
    }

    public String laneName() {
        return laneName;
    }
}
