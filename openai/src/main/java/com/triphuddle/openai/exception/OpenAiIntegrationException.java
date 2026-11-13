package com.triphuddle.openai.exception;

/**
 * Thrown when text generation is disabled, misconfigured or fails.
 */
public class OpenAiIntegrationException extends RuntimeException {

    /**
     * @param message what went wrong
     */
    public OpenAiIntegrationException(String message) {
        super(message);
    }

    /**
     * @param message what went wrong
     * @param cause the underlying failure
     */
    public OpenAiIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
