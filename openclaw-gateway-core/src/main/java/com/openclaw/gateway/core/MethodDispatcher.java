package com.openclaw.gateway.core;

import com.openclaw.common.error.CommonErrorCode;
import com.openclaw.common.error.OpenClawException;
import com.openclaw.gateway.api.GatewayRequest;
import com.openclaw.gateway.api.GatewayResponse;
import com.openclaw.logging.MdcKeys;
import com.openclaw.logging.MdcScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Routes a {@link GatewayRequest} to the correct {@link MethodHandler} and wraps the result
 * in a {@link GatewayResponse}. M1 runs every method through {@link AuthGuard#verify(String)} uniformly;
 * per-method ACLs / rate-limits land in M4–M5.
 */
public class MethodDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MethodDispatcher.class);

    private final Map<String, MethodHandler> byMethod;
    private final AuthGuard authGuard;

    public MethodDispatcher(final List<MethodHandler> handlers, final AuthGuard authGuard) {
        Objects.requireNonNull(handlers, "handlers");
        this.authGuard = Objects.requireNonNull(authGuard, "authGuard");
        this.byMethod = handlers.stream()
            .collect(Collectors.toUnmodifiableMap(
                MethodHandler::method,
                h -> h,
                (existing, dup) -> {
                    throw new OpenClawException(CommonErrorCode.ILLEGAL_STATE,
                        "Duplicate gateway method: " + existing.method());
                }
            ));
        log.info("gateway.methods registered count={} methods={}", byMethod.size(), byMethod.keySet());
    }

    public GatewayResponse dispatch(final GatewayRequest request) {
        Objects.requireNonNull(request, "request");
        try (var ignored = MdcScope.of(MdcKeys.REQUEST_ID, request.id())) {
            try {
                authGuard.verify(request.authToken());
                final MethodHandler handler = byMethod.get(request.method());
                if (handler == null) {
                    return GatewayResponse.failure(request.id(),
                        CommonErrorCode.NOT_FOUND,
                        "Unknown gateway method: " + request.method());
                }
                final Map<String, Object> result = handler.handle(request);
                return GatewayResponse.success(request.id(), result == null ? Map.of() : result);
            } catch (OpenClawException e) {
                log.warn("gateway.dispatch.failure method={} code={} msg={}",
                    request.method(), e.getErrorCode().code(), e.getMessage());
                return GatewayResponse.failure(request.id(), e.getErrorCode(), e.getMessage());
            } catch (RuntimeException e) {
                log.error("gateway.dispatch.error method={}", request.method(), e);
                return GatewayResponse.failure(request.id(), CommonErrorCode.INTERNAL_ERROR, e.getMessage());
            }
        }
    }

    public List<String> methods() {
        return List.copyOf(byMethod.keySet());
    }
}
