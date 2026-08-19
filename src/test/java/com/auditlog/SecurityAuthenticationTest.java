package com.auditlog;

import com.auditlog.dto.ErrorResponse;
import com.auditlog.security.Roles;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-level coverage for docs/EVALUATION_CLOSURE_MATRIX.md item 16 (SEC-02), exercised
 * against the real HTTP filter chain (JwtAuthenticationFilter), not just JwtService in
 * isolation (see JwtServiceTest for the unit-level equivalents).
 */
class SecurityAuthenticationTest extends AbstractApiIntegrationTest {

    @Value("${audit.security.jwt.secret}")
    private String configuredSecret;

    @Value("${audit.security.jwt.issuer}")
    private String issuer;

    @Value("${audit.security.jwt.audience}")
    private String audience;

    @Test
    void missingTokenIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                baseUrl("/audit/events"), HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void malformedTokenIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("not-a-real-jwt");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                baseUrl("/audit/events"), HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = jwtService.issueToken("subj", DEFAULT_TENANT, Set.of(Roles.USER), Duration.ofSeconds(-30));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(expired);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                baseUrl("/audit/events"), HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forgedTokenSignedWithWrongKeyIsRejected() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "a-completely-different-secret-key-not-known-to-the-server".getBytes(StandardCharsets.UTF_8));
        String forged = tokenWith(wrongKey, issuer, audience);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(forged);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                baseUrl("/audit/verify"), HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void incorrectIssuerIsRejected() {
        SecretKey key = Keys.hmacShaKeyFor(configuredSecret.getBytes(StandardCharsets.UTF_8));
        String token = tokenWith(key, "some-other-issuer", audience);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                baseUrl("/audit/events"), HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void incorrectAudienceIsRejected() {
        SecretKey key = Keys.hmacShaKeyFor(configuredSecret.getBytes(StandardCharsets.UTF_8));
        String token = tokenWith(key, issuer, "some-other-api");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                baseUrl("/audit/events"), HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String tokenWith(SecretKey key, String tokenIssuer, String tokenAudience) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("subj")
                .issuer(tokenIssuer)
                .audience().add(tokenAudience).and()
                .claim("tenantId", DEFAULT_TENANT)
                .claim("roles", Set.of(Roles.AUDITOR))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(1))))
                .signWith(key)
                .compact();
    }
}
