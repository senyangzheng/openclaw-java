package com.openclaw.providers.registry;

import com.openclaw.providers.api.ChatChunkEvent;
import com.openclaw.providers.api.ChatRequest;
import com.openclaw.providers.api.ChatResponse;
import com.openclaw.providers.api.ProviderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link ProviderClient} facade over a {@link ProviderRegistry}. Every inbound call
 * walks the registry's preferred order, skipping cooling-down providers and falling
 * over on per-call failures. Reports success / failure back to the registry so the
 * next caller sees an updated health view.
 *
 * <p>Registered as {@code @Primary} so consumers autowiring a single
 * {@link ProviderClient} (e.g. {@link com.openclaw.autoreply.AutoReplyPipeline})
 * transparently pick up the registry's routing without code changes.
 *
 * <p><b>Why a {@link Supplier} instead of a {@link ProviderRegistry} reference?</b>
 * The composite is itself a {@link ProviderClient} bean, and
 * {@code DefaultProviderRegistry}'s constructor collects {@code List&lt;ProviderClient&gt;} —
 * if we took a direct reference here, Spring would see a cycle:
 * {@code composite → registry → List<ProviderClient> (includes composite)}.
 * Resolving the registry lazily (via {@code ObjectProvider::getObject} from the
 * autoconfig) defers the lookup to call time, by which point both beans are
 * fully created.
 */
public class CompositeProviderClient implements ProviderClient {

    public static final String PROVIDER_ID = "registry";
    private static final Logger log = LoggerFactory.getLogger(CompositeProviderClient.class);

    private final Supplier<ProviderRegistry> registrySupplier;

    public CompositeProviderClient(final Supplier<ProviderRegistry> registrySupplier) {
        this.registrySupplier = Objects.requireNonNull(registrySupplier, "registrySupplier");
    }

    /** Test / direct-wire convenience: takes a fully-built registry eagerly. */
    public static CompositeProviderClient of(final ProviderRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        return new CompositeProviderClient(() -> registry);
    }

    private ProviderRegistry registry() {
        return registrySupplier.get();
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public ChatResponse chat(final ChatRequest request) {
        final ProviderRegistry registry = registry();
        RuntimeException lastError = null;
        for (final String id : registry.providerIds()) {
            if (registry.isCoolingDown(id)) {
                log.debug("providers.registry.skip.cooldown providerId={}", id);
                continue;
            }
            final ProviderClient client = registry.get(id).orElse(null);
            if (client == null) {
                continue;
            }
            try {
                final ChatResponse response = client.chat(request);
                registry.reportSuccess(id);
                return response;
            } catch (RuntimeException e) {
                lastError = e;
                registry.reportFailure(id, e);
                log.warn("providers.registry.fallback providerId={} reason={}", id, e.toString());
            }
        }
        throw lastError != null
            ? lastError
            : new NoSuchElementException("No healthy ProviderClient available");
    }

    @Override
    public Flux<ChatChunkEvent> streamChat(final ChatRequest request) {
        final ProviderRegistry registry = registry();
        final List<String> ids = registry.providerIds();
        if (ids.isEmpty()) {
            return Flux.error(new NoSuchElementException("No ProviderClient registered"));
        }
        return attemptStream(registry, request, ids, 0);
    }

    private Flux<ChatChunkEvent> attemptStream(final ProviderRegistry registry,
                                               final ChatRequest request,
                                               final List<String> ids,
                                               final int index) {
        if (index >= ids.size()) {
            return Flux.error(new NoSuchElementException("All providers failed or are cooling down"));
        }
        final String id = ids.get(index);
        if (registry.isCoolingDown(id)) {
            log.debug("providers.registry.stream.skip.cooldown providerId={}", id);
            return attemptStream(registry, request, ids, index + 1);
        }
        final ProviderClient client = registry.get(id).orElse(null);
        if (client == null) {
            return attemptStream(registry, request, ids, index + 1);
        }
        return Flux.defer(() -> client.streamChat(request))
            .doOnComplete(() -> registry.reportSuccess(id))
            .onErrorResume(err -> {
                registry.reportFailure(id, err);
                log.warn("providers.registry.stream.fallback providerId={} reason={}", id, err.toString());
                return attemptStream(registry, request, ids, index + 1);
            });
    }
}
