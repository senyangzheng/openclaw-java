package com.openclaw.gateway.core;

import com.openclaw.common.error.ErrorCode;
import com.openclaw.common.error.OpenClawException;

/**
 * Token-based authorization gate. M1 ships a {@link MockAuthGuard} that accepts a single
 * configured token; real JWT-based guard arrives with {@code openclaw-security} in M5.
 */
public interface AuthGuard {

    ErrorCode UNAUTHORIZED = new ErrorCode() {
        @Override
        public String code() {
            return "GATEWAY_4010";
        }

        @Override
        public String defaultMessage() {
            return "Unauthorized";
        }
    };

    /** Throws {@link OpenClawException} with {@link #UNAUTHORIZED} on failure. */
    void verify(String token);
}
