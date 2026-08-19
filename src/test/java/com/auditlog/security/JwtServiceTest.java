package com.auditlog.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-level coverage for docs/EVALUATION_CLOSURE_MATRIX.md item 16 (SEC-02): missing (see
 * integration-level SecurityAuthenticationTest), invalid, expired, forged, and wrong
 * issuer/audience tokens must all be rejected.
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-not-used-anywhere-else-8f3c1a";
    private static final String ISSUER = "test-issuer";
    private static final String AUDIENCE = "test-audience";

    private final JwtService jwtService = new JwtService(SECRET, ISSUER, AUDIENCE);

    @Test
    void issuedTokenRoundTripsCorrectly() {
        String token = jwtService.issueToken("subject-1", "tenant-1", Set.of(Roles.USER), Duration.ofMinutes(5));

        AuthenticatedPrincipal principal = jwtService.parse(token);

        assertThat(principal.subjectId()).isEqualTo("subject-1");
        assertThat(principal.tenantId()).isEqualTo("tenant-1");
        assertThat(principal.hasRole(Roles.USER)).isTrue();
    }

    @Test
    void expiredTokenIsRejected() {
        String token = jwtService.issueToken("subject-1", "tenant-1", Set.of(Roles.USER), Duration.ofSeconds(-10));

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(JwtInvalidException.class);
    }

    @Test
    void tokenSignedWithWrongKeyIsRejected() {
        SecretKey wrongKey = Keys.hmacShaKeyFor("a-totally-different-secret-not-known-to-the-service".getBytes(StandardCharsets.UTF_8));
        String forged = tokenWith(wrongKey, ISSUER, AUDIENCE);

        assertThatThrownBy(() -> jwtService.parse(forged)).isInstanceOf(JwtInvalidException.class);
    }

    @Test
    void tokenWithWrongIssuerIsRejected() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = tokenWith(key, "some-other-issuer", AUDIENCE);

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(JwtInvalidException.class);
    }

    @Test
    void tokenWithWrongAudienceIsRejected() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = tokenWith(key, ISSUER, "some-other-audience");

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(JwtInvalidException.class);
    }

    @Test
    void malformedTokenIsRejected() {
        assertThatThrownBy(() -> jwtService.parse("not-a-jwt-at-all")).isInstanceOf(JwtInvalidException.class);
    }

    private String tokenWith(SecretKey key, String issuer, String audience) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("subject-1")
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("tenantId", "tenant-1")
                .claim("roles", Set.of(Roles.AUDITOR))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(1))))
                .signWith(key)
                .compact();
    }
}
