package com.openclaw.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class MdcScopeTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void shouldRestorePreviousMdcOnClose() {
        MDC.put(MdcKeys.REQUEST_ID, "old");

        try (var ignored = MdcScope.of(MdcKeys.REQUEST_ID, "new").with(MdcKeys.SESSION_ID, "s1")) {
            assertThat(MDC.get(MdcKeys.REQUEST_ID)).isEqualTo("new");
            assertThat(MDC.get(MdcKeys.SESSION_ID)).isEqualTo("s1");
        }

        assertThat(MDC.get(MdcKeys.REQUEST_ID)).isEqualTo("old");
        assertThat(MDC.get(MdcKeys.SESSION_ID)).isNull();
    }

    @Test
    void shouldIgnoreNullKeyOrValue() {
        try (var scope = MdcScope.of(MdcKeys.REQUEST_ID, "r").with(null, "x").with(MdcKeys.USER_ID, null)) {
            assertThat(MDC.get(MdcKeys.REQUEST_ID)).isEqualTo("r");
            assertThat(MDC.get(MdcKeys.USER_ID)).isNull();
        }
    }
}
