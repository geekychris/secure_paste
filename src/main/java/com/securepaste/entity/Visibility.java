package com.securepaste.entity;

/**
 * Enum representing the visibility levels for pastes.
 */
public enum Visibility {
    
    /**
     * Paste is publicly visible and searchable.
     */
    PUBLIC("Public"),
    
    /**
     * Paste is accessible only via direct URL.
     */
    UNLISTED("Unlisted"),
    
    /**
     * Paste is private and requires authentication.
     */
    PRIVATE("Private");

    private final String displayName;

    Visibility(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}