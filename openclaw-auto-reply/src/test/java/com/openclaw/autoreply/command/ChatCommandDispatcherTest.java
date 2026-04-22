package com.openclaw.autoreply.command;

import com.openclaw.channels.core.InboundMessage;
import com.openclaw.routing.RoutingKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ChatCommandDispatcherTest {

    @Test
    void shouldReturnFirstMatchByOrder() {
        final ChatCommand low = stubCommand("low", -10, inbound -> true, inbound -> "low-reply");
        final ChatCommand high = stubCommand("high", 10, inbound -> true, inbound -> "high-reply");
        final ChatCommandDispatcher dispatcher = new ChatCommandDispatcher(List.of(high, low));

        final Optional<ChatCommandDispatcher.DispatchResult> result =
            dispatcher.tryHandle(inbound("anything"));

        assertThat(result).isPresent();
        assertThat(result.get().commandName()).isEqualTo("low");
        assertThat(result.get().reply()).isEqualTo("low-reply");
    }

    @Test
    void shouldSkipNonMatchingAndReturnEmptyWhenNoneMatch() {
        final AtomicInteger counter = new AtomicInteger();
        final ChatCommand cmd = stubCommand("never", 0,
            inbound -> {
                counter.incrementAndGet();
                return false;
            },
            inbound -> {
                throw new AssertionError("handle should not be called when matches=false");
            });

        final ChatCommandDispatcher dispatcher = new ChatCommandDispatcher(List.of(cmd));

        assertThat(dispatcher.tryHandle(inbound("hi"))).isEmpty();
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void shouldFallBackToEmptyWhenHandleThrows() {
        final ChatCommand cmd = stubCommand("boom", 0,
            inbound -> true,
            inbound -> {
                throw new IllegalStateException("kaboom");
            });

        final ChatCommandDispatcher dispatcher = new ChatCommandDispatcher(List.of(cmd));

        assertThat(dispatcher.tryHandle(inbound("hi"))).isEmpty();
    }

    @Test
    void shouldTreatMatchesExceptionAsNonMatchAndContinue() {
        final ChatCommand flaky = stubCommand("flaky", -1,
            inbound -> {
                throw new RuntimeException("match failed");
            },
            inbound -> "should-not-run");
        final ChatCommand good = stubCommand("good", 0, inbound -> true, inbound -> "ok");

        final ChatCommandDispatcher dispatcher = new ChatCommandDispatcher(List.of(flaky, good));

        final Optional<ChatCommandDispatcher.DispatchResult> result = dispatcher.tryHandle(inbound("hi"));
        assertThat(result).isPresent();
        assertThat(result.get().commandName()).isEqualTo("good");
    }

    @Test
    void shouldReReadLazySupplierOnEachCall() {
        // Simulates plugin.onLoad registering a command AFTER the dispatcher
        // was constructed. The lazy supplier is the whole reason we tolerate
        // a list allocation per inbound.
        final AtomicInteger reads = new AtomicInteger();
        final java.util.List<ChatCommand> mutable = new java.util.ArrayList<>();
        final ChatCommandDispatcher dispatcher = new ChatCommandDispatcher(() -> {
            reads.incrementAndGet();
            return List.copyOf(mutable);
        });

        assertThat(dispatcher.tryHandle(inbound("x"))).isEmpty();
        mutable.add(stubCommand("late", 0, inbound -> true, inbound -> "late-reply"));

        final Optional<ChatCommandDispatcher.DispatchResult> second = dispatcher.tryHandle(inbound("x"));
        assertThat(second).isPresent();
        assertThat(second.get().commandName()).isEqualTo("late");
        assertThat(reads.get()).isEqualTo(2);
    }

    private static InboundMessage inbound(final String text) {
        return new InboundMessage(
            UUID.randomUUID().toString(),
            RoutingKey.of("web", "anon", "c-test"),
            text,
            null,
            null
        );
    }

    /** Minimal stub so the test stays dependency-free (no Mockito → no JDK 21 self-attach headache). */
    private static ChatCommand stubCommand(final String name,
                                            final int order,
                                            final java.util.function.Predicate<InboundMessage> matcher,
                                            final java.util.function.Function<InboundMessage, String> handler) {
        return new ChatCommand() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean matches(final InboundMessage inbound) {
                return matcher.test(inbound);
            }

            @Override
            public String handle(final InboundMessage inbound) {
                return handler.apply(inbound);
            }

            @Override
            public int order() {
                return order;
            }
        };
    }
}
