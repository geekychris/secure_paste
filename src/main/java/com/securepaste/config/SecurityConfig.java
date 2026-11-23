package com.securepaste.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

/**
 * Security configuration for SecurePaste.
 * 
 * Configures authentication, authorization, and password encoding.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Sent on every response except the H2 console's. */
    static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; object-src 'none'; base-uri 'self'; "
            + "form-action 'self'; frame-ancestors 'self'";

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
            // CSRF protects the browser form POSTs (the Reveal button on
            // /paste/{id}/reveal, login and logout). The token lives in a
            // cookie (double submit), so no server session is needed and a
            // restart between the interstitial and Reveal doesn't break it.
            //
            // /api/** is exempt: it is a stateless JSON API for scripts and
            // the pages' fetch() calls. It has no cookie or session
            // authentication, so a forged cross-site request carries no
            // authority the attacker lacks; anyone who knows a paste id can
            // already call it directly. The H2 console (off in deployments)
            // can't send the token.
            .csrf(csrf -> csrf
                .csrfTokenRepository(new CookieCsrfTokenRepository())
                .ignoringRequestMatchers("/api/**")
                .ignoringRequestMatchers("/h2-console/**")
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin()) // Allow H2 console frames
                // Paste URLs are secrets: don't send them to other sites.
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                // Pastes are untrusted text: even if something renders one as
                // HTML, no injected script may run. The pages keep their
                // script in /js/*.js (no inline script or on* handlers);
                // inline styles remain, so style-src allows them. The H2
                // console (development only) needs inline script, so it is
                // left out.
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                    new NegatedRequestMatcher(new AntPathRequestMatcher("/h2-console/**")),
                    new StaticHeadersWriter("Content-Security-Policy", CONTENT_SECURITY_POLICY)))
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