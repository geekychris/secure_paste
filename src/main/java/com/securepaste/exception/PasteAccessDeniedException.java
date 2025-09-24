package com.securepaste.exception;

/**
 * Exception thrown when access to a paste is denied.
 */
public class PasteAccessDeniedException extends RuntimeException {

    public PasteAccessDeniedException(String message) {
        super(message);
    }

    public PasteAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}