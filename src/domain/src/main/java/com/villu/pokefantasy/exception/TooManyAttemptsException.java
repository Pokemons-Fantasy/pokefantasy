package com.villu.pokefantasy.exception;

import java.time.Duration;

/** Demasiados intentos fallidos: la operación se rechaza temporalmente (HTTP 429). */
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
