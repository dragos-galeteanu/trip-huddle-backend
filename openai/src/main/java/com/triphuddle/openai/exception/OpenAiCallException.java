package com.triphuddle.openai.exception;

import lombok.Getter;

import java.time.Duration;

/**
 * Thrown when an OpenAI call fails; says whether trying again may help, and how long OpenAI asked to wait.
 */
@Getter
public class OpenAiCallException extends OpenAiIntegrationException {

    private final boolean isRetryable;
    private final Integer statusCode;
    private final Duration retryAfter;

    public OpenAiCallException(String message, boolean isRetryable, Integer statusCode, Duration retryAfter,
                               Throwable cause) {
        super(message, cause);
        this.isRetryable = isRetryable;
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
    }

    public OpenAiCallException(String message, boolean isRetryable) {
        this(message, isRetryable, null, null, null);
    }
}
