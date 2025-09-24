package com.securepaste.dto;

import com.securepaste.entity.Visibility;
import jakarta.validation.constraints.Size;

/**
 * DTO for updating an existing paste.
 */
public class PasteUpdateRequest {

    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @Size(max = 1000000, message = "Content must not exceed 1MB")
    private String content;

    @Size(max = 50, message = "Language must not exceed 50 characters")
    private String language;

    private Visibility visibility;

    // Constructors
    public PasteUpdateRequest() {}

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    @Override
    public String toString() {
        return "PasteUpdateRequest{" +
                "title='" + title + '\'' +
                ", language='" + language + '\'' +
                ", visibility=" + visibility +
                '}';
    }
}