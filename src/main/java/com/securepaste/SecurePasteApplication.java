package com.securepaste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Main Spring Boot application class for SecurePaste.
 * 
 * This application provides a secure pastebin service with both REST API
 * and web UI functionality using Vaadin Flow.
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
public class SecurePasteApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurePasteApplication.class, args);
    }
}