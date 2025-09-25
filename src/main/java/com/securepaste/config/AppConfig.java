package com.securepaste.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the SecurePaste application.
 */
@Configuration
@ConfigurationProperties(prefix = "securepaste")
public class AppConfig {
    
    private String baseUrl = "http://localhost:8097";
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    /**
     * Gets the base URL with trailing slash removed if present.
     */
    public String getCleanBaseUrl() {
        if (baseUrl != null && baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}