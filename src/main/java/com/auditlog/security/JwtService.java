package com.auditlog.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * HMAC-signed JWT issuance and validation. Stands in for real OIDC/JWKS-based verification
 * against an external identity provider -- see docs/DECISIONS.md ADR-008 and docs/SECURITY.md
 * for why, and what a real deployment would need instead.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final String audience;

    public JwtService(@Value("${audit.security.jwt.secret}") String secret,
                       @Value("${audit.security.jwt.issuer}") String issuer,
                       @Value("${audit.security.jwt.audience}") String audience) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
    }

    public String issueToken(String subjectId, String tenantId, Set<String> roles, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subjectId)
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("tenantId", tenantId)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public AuthenticatedPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (claims.getAudience() == null || !claims.getAudience().contains(audience)) {
                throw new JwtInvalidException("Token audience does not match this API");
            }

            String subject = claims.getSubject();
            String tenantId = claims.get("tenantId", String.class);
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);

            if (subject == null || subject.isBlank() || tenantId == null || tenantId.isBlank()) {
                throw new JwtInvalidException("Token missing required claims (subject/tenantId)");
            }

            return new AuthenticatedPrincipal(subject, tenantId, roles == null ? Set.of() : new HashSet<>(roles));
        } catch (ExpiredJwtException e) {
            throw new JwtInvalidException("Token expired", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtInvalidException("Invalid token", e);
        }
    }
}
