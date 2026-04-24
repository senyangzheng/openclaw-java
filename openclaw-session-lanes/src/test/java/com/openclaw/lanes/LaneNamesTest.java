package com.openclaw.lanes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LaneNamesTest {

    @Test
    void resolveSessionLane_prefixesBareKey() {
        assertThat(LaneNames.resolveSessionLane("acct:1:conv:a"))
                .isEqualTo("session:acct:1:conv:a");
    }

    @Test
    void resolveSessionLane_idempotentWhenAlreadyPrefixed() {
        assertThat(LaneNames.resolveSessionLane("session:acct:1"))
                .isEqualTo("session:acct:1");
    }

    @Test
    void resolveSessionLane_blankFallsBackToMain() {
        assertThat(LaneNames.resolveSessionLane(null)).isEqualTo("session:main");
        assertThat(LaneNames.resolveSessionLane("")).isEqualTo("session:main");
        assertThat(LaneNames.resolveSessionLane("   ")).isEqualTo("session:main");
    }

    @Test
    void resolveGlobalLane_blankFallsBackToMain() {
        assertThat(LaneNames.resolveGlobalLane(null)).isEqualTo("main");
        assertThat(LaneNames.resolveGlobalLane(" ")).isEqualTo("main");
        assertThat(LaneNames.resolveGlobalLane("cron")).isEqualTo("cron");
    }

    @Test
    void isProbeLane_detectsKnownPrefixes() {
        assertThat(LaneNames.isProbeLane("auth-probe:qwen")).isTrue();
        assertThat(LaneNames.isProbeLane("session:probe-xyz")).isTrue();
        assertThat(LaneNames.isProbeLane("session:normal")).isFalse();
        assertThat(LaneNames.isProbeLane(null)).isFalse();
    }
}
