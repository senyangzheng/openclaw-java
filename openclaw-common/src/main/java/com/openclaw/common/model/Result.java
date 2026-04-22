package com.openclaw.common.model;

import com.openclaw.common.error.ErrorCode;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Rust-style {@code Result<T>} union for functional chains where exceptions would obscure control flow.
 * Used mainly inside Agent / Provider runtime (where retries / fallbacks are explicit data).
 *
 * <p>Prefer throwing {@code OpenClawException} for cross-layer failures; reserve {@code Result}
 * for in-module algorithmic branching.
 */
public sealed interface Result<T> permits Result.Ok, Result.Err {

    static <T> Result<T> ok(final T value) {
        return new Ok<>(value);
    }

    static <T> Result<T> err(final ErrorCode code, final String message) {
        return new Err<>(code, message);
    }

    boolean isOk();

    default boolean isErr() {
        return !isOk();
    }

    Optional<T> valueOpt();

    Optional<ErrorCode> errorCodeOpt();

    Optional<String> errorMessageOpt();

    <R> Result<R> map(Function<? super T, ? extends R> mapper);

    record Ok<T>(T data) implements Result<T> {

        @Override
        public boolean isOk() {
            return true;
        }

        @Override
        public Optional<T> valueOpt() {
            return Optional.ofNullable(data);
        }

        @Override
        public Optional<ErrorCode> errorCodeOpt() {
            return Optional.empty();
        }

        @Override
        public Optional<String> errorMessageOpt() {
            return Optional.empty();
        }

        @Override
        public <R> Result<R> map(final Function<? super T, ? extends R> mapper) {
            return new Ok<>(mapper.apply(data));
        }
    }

    record Err<T>(ErrorCode code, String message) implements Result<T> {

        public Err {
            Objects.requireNonNull(code, "code");
        }

        @Override
        public boolean isOk() {
            return false;
        }

        @Override
        public Optional<T> valueOpt() {
            return Optional.empty();
        }

        @Override
        public Optional<ErrorCode> errorCodeOpt() {
            return Optional.of(code);
        }

        @Override
        public Optional<String> errorMessageOpt() {
            return Optional.ofNullable(message);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <R> Result<R> map(final Function<? super T, ? extends R> mapper) {
            return (Result<R>) this;
        }
    }
}
