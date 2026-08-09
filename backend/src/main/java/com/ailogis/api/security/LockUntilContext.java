package com.ailogis.api.security;

import java.time.LocalDateTime;

/**
 * Thread-local carrying the {@code lockUntil} timestamp from
 * {@link LoginAttemptService#onFailure} into
 * {@link com.ailogis.api.exception.GlobalExceptionHandler} so the FE can
 * display a countdown and auto-unlock when the lock window elapses. Cleared
 * after each request to avoid leaks across threads.
 */
public final class LockUntilContext {

    private static final ThreadLocal<LocalDateTime> CTX = new ThreadLocal<>();

    private LockUntilContext() {
    }

    public static void set(LocalDateTime value) {
        CTX.set(value);
    }

    public static LocalDateTime get() {
        return CTX.get();
    }

    public static void clear() {
        CTX.remove();
    }
}
