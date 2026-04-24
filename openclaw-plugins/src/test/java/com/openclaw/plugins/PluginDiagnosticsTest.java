package com.openclaw.plugins;

import java.util.stream.IntStream;

import com.openclaw.plugin.CapabilityType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluginDiagnosticsTest {

    @Test
    void shouldRecordEntriesInOrder() {
        final PluginDiagnostics d = new PluginDiagnostics();
        d.record(PluginDiagnostics.loaderError("p1", "C", new RuntimeException("boom")));
        d.record(PluginDiagnostics.capabilityConflict("p2", "p1", CapabilityType.TOOL, "x"));

        final var snapshot = d.snapshot();
        assertThat(snapshot).hasSize(2);
        assertThat(snapshot.get(0).kind()).isEqualTo(PluginDiagnostics.Kind.LOADER_ERROR);
        assertThat(snapshot.get(1).kind()).isEqualTo(PluginDiagnostics.Kind.CAPABILITY_CONFLICT);
        assertThat(snapshot.get(1).capabilityName()).isEqualTo("x");
        assertThat(snapshot.get(1).extras()).containsEntry("existingPluginId", "p1");
    }

    @Test
    void shouldCapAtMaxEntriesDroppingOldest() {
        final PluginDiagnostics d = new PluginDiagnostics();
        IntStream.range(0, PluginDiagnostics.MAX_ENTRIES + 50).forEach(i ->
                d.record(PluginDiagnostics.loaderError("p" + i, "C", new RuntimeException("e" + i))));

        final var snap = d.snapshot();
        assertThat(snap).hasSize(PluginDiagnostics.MAX_ENTRIES);
        // first kept entry should be the 50th — the first 50 should have been evicted
        assertThat(snap.get(0).pluginId()).isEqualTo("p50");
    }

    @Test
    void shouldProduceImmutableSnapshots() {
        final PluginDiagnostics d = new PluginDiagnostics();
        d.record(PluginDiagnostics.configSchema("p1", "openclaw.plugins.p1.apiKey", "missing"));
        final var snap1 = d.snapshot();
        d.record(PluginDiagnostics.unloadError("p1", new RuntimeException("teardown")));
        assertThat(snap1).hasSize(1); // unchanged
        assertThat(d.snapshot()).hasSize(2);
    }
}
