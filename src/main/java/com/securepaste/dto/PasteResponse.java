package com.securepaste.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.securepaste.entity.Visibility;

import java.time.LocalDateTime;

/**
 * DTO for paste responses.
 */
public class PasteResponse {

    private String id;
    private String title;
    private String content;
    private String language;
    private String authorName;
    private Visibility visibility;
    private Long viewCount;
    private boolean passwordProtected;

    /** The view limit, or null when unlimited. */
    private Integer maxViews;

    /** Views left after this response, or null when unlimited. */
    private Integer viewsRemaining;

    /**
     * True when content was withheld: a view-limited paste read by GET (or
     * listed) shows metadata only; POST .../reveal returns the content.
     */
    private boolean revealRequired;

    /** True when this reveal used the last view and the paste is now deleted. */
    private boolean burned;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    // Constructors
    public PasteResponse() {}

    // Getters and Setters
    public Integer getMaxViews() {
        return maxViews;
    }

    public void setMaxViews(Integer maxViews) {
        this.maxViews = maxViews;
    }

    public Integer getViewsRemaining() {
        return viewsRemaining;
    }

    public void setViewsRemaining(Integer viewsRemaining) {
        this.viewsRemaining = viewsRemaining;
    }

    public boolean isRevealRequired() {
        return revealRequired;
    }

    public void setRevealRequired(boolean revealRequired) {
        this.revealRequired = revealRequired;
    }

    public boolean isBurned() {
        return burned;
    }

    public void setBurned(boolean burned) {
        this.burned = burned;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public boolean isPasswordProtected() {
        return passwordProtected;
    }

    public void setPasswordProtected(boolean passwordProtected) {
        this.passwordProtected = passwordProtected;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public String toString() {
        return "PasteResponse{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", language='" + language + '\'' +
                ", authorName='" + authorName + '\'' +
                ", visibility=" + visibility +
                ", viewCount=" + viewCount +
                ", passwordProtected=" + passwordProtected +
                ", createdAt=" + createdAt +
                '}';
    }
}