package com.ailogis.api.security;

/**
 * Thread-local carrying the number of remaining login attempts from
 * {@link LoginAttemptService#onFailure} into
 * {@link com.ailogis.api.exception.GlobalExceptionHandler} so the response body
 * can include the exact {@code remainingAttempts} value reflecting the user's
 * post-attempt state. Cleared after each request to avoid leaks across threads.
 */
public final class RemainingAttemptsContext {

    private static final ThreadLocal<Integer> CTX = new ThreadLocal<>();

    private RemainingAttemptsContext() {
    }

    public static void set(Integer value) {
        CTX.set(value);
    }

    public static Integer get() {
        return CTX.get();
    }

    public static void clear() {
        CTX.remove();
    }
}
