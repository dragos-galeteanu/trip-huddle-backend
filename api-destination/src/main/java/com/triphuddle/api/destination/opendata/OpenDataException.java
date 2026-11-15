package com.triphuddle.api.destination.opendata;

/**
 * Thrown when Wikidata or Wikimedia Commons can't be reached or answer something unreadable.
 */
public class OpenDataException extends RuntimeException {

    public OpenDataException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenDataException(String message) {
        super(message);
    }
}
