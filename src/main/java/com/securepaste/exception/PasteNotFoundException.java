package com.securepaste.exception;

/**
 * Exception thrown when a paste is not found.
 */
public class PasteNotFoundException extends RuntimeException {

    public PasteNotFoundException(String message) {
        super(message);
    }

    public PasteNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}