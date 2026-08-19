package com.auditlog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * docs/EVALUATION_CLOSURE_MATRIX.md item 6 (SEC-06). Explicit, not implicit: the assignment
 * states no external browser-based application/consumer is required, so the policy is
 * deliberately empty (no origin is allowed cross-origin access) rather than left unconfigured.
 * An empty allowed-origins list is a stated decision, inspectable and testable, not the same as
 * "we never thought about it." If a real browser-based consumer emerges, add its exact origin
 * here -- never a wildcard.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of());
        configuration.setAllowedMethods(List.of("GET", "POST"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsFilter(source);
    }
}
