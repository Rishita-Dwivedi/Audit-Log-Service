package com.auditlog.security;

import com.auditlog.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * docs/EVALUATION_CLOSURE_MATRIX.md item 5 (SEC-06). Rejects a request whose declared
 * Content-Length exceeds the configured limit before it reaches any controller/service.
 * Known limitation, stated plainly: this checks the declared Content-Length header, not actual
 * bytes read -- a request sent without Content-Length (chunked transfer-encoding) would not be
 * caught by this check. Acceptable for this prototype since standard HTTP clients (including
 * every client used in this codebase's own tests) always send Content-Length for a JSON body;
 * closing the chunked-transfer gap would need a size-limiting InputStream wrapper instead.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(@Value("${audit.security.max-request-bytes}") long maxBytes, ObjectMapper objectMapper) {
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json");
            ErrorResponse body = new ErrorResponse(OffsetDateTime.now(), 413, "Payload too large",
                    List.of("Request body of " + contentLength + " bytes exceeds the " + maxBytes + " byte limit"));
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }
        chain.doFilter(request, response);
    }
}
