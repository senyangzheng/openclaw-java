package com.openclaw.channels.core;

import com.openclaw.common.error.OpenClawException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelRegistryTest {

    @Test
    void shouldLookupByChannelId() {
        final ChannelAdapter a = stub("web");
        final ChannelAdapter b = stub("mock");

        final ChannelRegistry registry = new ChannelRegistry(List.of(a, b));

        assertThat(registry.require("web")).isSameAs(a);
        assertThat(registry.find("mock")).hasValue(b);
        assertThat(registry.find("unknown")).isEmpty();
    }

    @Test
    void shouldFailOnDuplicateChannelId() {
        assertThatThrownBy(() -> new ChannelRegistry(List.of(stub("web"), stub("web"))))
            .isInstanceOf(OpenClawException.class)
            .hasMessageContaining("Duplicate channel");
    }

    @Test
    void shouldThrowWhenRequiringUnknown() {
        final ChannelRegistry empty = new ChannelRegistry(List.of());

        assertThatThrownBy(() -> empty.require("x"))
            .isInstanceOf(OpenClawException.class);
    }

    private static ChannelAdapter stub(final String id) {
        return new ChannelAdapter() {
            @Override
            public String channelId() {
                return id;
            }

            @Override
            public void deliver(final OutboundMessage outbound) {
            }
        };
    }
}
