package com.securepaste.dto;

import com.securepaste.entity.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * DTO for creating a new paste.
 */
public class PasteCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Content is required")
    @Size(max = 1000000, message = "Content must not exceed 1MB")
    private String content;

    @Size(max = 50, message = "Language must not exceed 50 characters")
    private String language;

    @Size(max = 100, message = "Author name must not exceed 100 characters")
    private String authorName;

    @Email(message = "Invalid email format")
    @Size(max = 200, message = "Email must not exceed 200 characters")
    private String authorEmail;

    private Visibility visibility = Visibility.PUBLIC;

    @Min(value = 1, message = "Expiration must be at least 1 minute")
    @Max(value = 525600, message = "Expiration must not exceed 1 year (525600 minutes)")
    private Integer expirationMinutes;

    @Size(max = 100, message = "Password must not exceed 100 characters")
    private String password;

    // Constructors
    public PasteCreateRequest() {}

    public PasteCreateRequest(String title, String content) {
        this.title = title;
        this.content = content;
    }

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

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public Integer getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(Integer expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "PasteCreateRequest{" +
                "title='" + title + '\'' +
                ", language='" + language + '\'' +
                ", authorName='" + authorName + '\'' +
                ", visibility=" + visibility +
                ", expirationMinutes=" + expirationMinutes +
                ", hasPassword=" + (password != null && !password.isEmpty()) +
                '}';
    }
}