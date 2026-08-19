package com.auditlog.security;

import com.auditlog.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Requires a valid Bearer JWT on every request except the dev token-issuance endpoint itself.
 * Registered automatically by Spring Boot (any Filter bean is auto-registered for all paths).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of("/dev/auth/token", "/actuator/health");

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (PUBLIC_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response, "Missing or malformed Authorization header");
            return;
        }

        String token = header.substring("Bearer ".length()).trim();
        try {
            AuthenticatedPrincipal principal = jwtService.parse(token);
            AuditSecurityContext.set(principal);
            chain.doFilter(request, response);
        } catch (JwtInvalidException e) {
            writeUnauthorized(response, e.getMessage());
        } finally {
            AuditSecurityContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        ErrorResponse body = new ErrorResponse(OffsetDateTime.now(), 401, "Unauthorized", List.of(message));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
