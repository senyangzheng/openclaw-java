package com.openclaw.secrets;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeSecretResolverTest {

    @Test
    void shouldReturnFirstResolvedValueInOrder() {
        final InMemorySecretResolver low = new InMemorySecretResolver().put("k", "lower");
        final InMemorySecretResolver high = new HighPriorityMemoryResolver().put("k", "higher");

        final CompositeSecretResolver composite = new CompositeSecretResolver(List.of(low, high));

        assertThat(composite.resolve("k")).hasValue("higher");
    }

    @Test
    void shouldReturnEmptyWhenAllResolversMiss() {
        final CompositeSecretResolver composite = new CompositeSecretResolver(
            List.of(new InMemorySecretResolver())
        );

        assertThat(composite.resolve("missing")).isEmpty();
    }

    private static final class HighPriorityMemoryResolver extends InMemorySecretResolver {
        @Override
        public int getOrder() {
            return 10;
        }
    }
}
