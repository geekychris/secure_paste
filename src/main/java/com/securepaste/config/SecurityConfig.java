package com.securepaste.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for SecurePaste.
 * 
 * Configures authentication, authorization, and password encoding.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Password encoder bean for hashing passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Security filter chain configuration.
     * 
     * Allows public access to most endpoints while protecting administrative functions.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                // Public API endpoints
                .requestMatchers("/api/pastes/**").permitAll()
                .requestMatchers("/api-docs/**").permitAll()
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/swagger-ui.html").permitAll()
                
                // Vaadin UI endpoints - allow all public access
                .requestMatchers("/", "/paste/**").permitAll()
                .requestMatchers("/VAADIN/**").permitAll()
                .requestMatchers("/vaadinServlet/**").permitAll()
                .requestMatchers("/sw.js").permitAll()
                .requestMatchers("/sw-runtime-resources-precache.js").permitAll()
                .requestMatchers("/manifest.webmanifest").permitAll()
                .requestMatchers("/icons/**").permitAll()
                .requestMatchers("/images/**").permitAll()
                .requestMatchers("/styles/**").permitAll()
                .requestMatchers("/frontend/**").permitAll()
                .requestMatchers("/connect/**").permitAll()
                .requestMatchers("/connect").permitAll()
                
                // Development endpoints
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                
                // Management endpoints (require authentication)
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                
                // Allow everything else for now (we'll secure later if needed)
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
                .ignoringRequestMatchers("/h2-console/**")
                .ignoringRequestMatchers("/VAADIN/**")
                .ignoringRequestMatchers("/connect/**")
                .ignoringRequestMatchers("/?v-r=**")
                .disable()
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin()) // Allow H2 console frames
            )
            .formLogin(form -> form
                .defaultSuccessUrl("/", true)
                .permitAll()
            )
            .logout(logout -> logout
                .permitAll()
            );

        return http.build();
    }
}