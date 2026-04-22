package com.openclaw.gateway.core;

import com.openclaw.common.error.OpenClawException;
import com.openclaw.common.util.Strings;

import java.util.Objects;

/**
 * Dev / M1-only auth guard: accepts either the configured shared token or any token when
 * the configured value is blank (permissive mode). Swap out via bean override for prod.
 */
public class MockAuthGuard implements AuthGuard {

    private final String sharedToken;

    public MockAuthGuard(final String sharedToken) {
        this.sharedToken = sharedToken;
    }

    @Override
    public void verify(final String token) {
        if (Strings.isBlank(sharedToken)) {
            return;
        }
        if (!Objects.equals(sharedToken, token)) {
            throw new OpenClawException(UNAUTHORIZED, "Invalid or missing auth token");
        }
    }
}
