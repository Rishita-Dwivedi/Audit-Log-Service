package com.auditlog.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * NOT a real authentication endpoint. Issues a JWT for any requested subjectId/tenantId/roles
 * with zero identity verification -- no password, no external check of any kind. This exists
 * solely so the API is demoable and testable end-to-end without a real OIDC identity
 * provider, which this environment does not have. See docs/SECURITY.md: this endpoint must
 * not exist in a real production deployment.
 */
@RestController
@RequestMapping("/dev/auth")
public class DevAuthController {

    private final JwtService jwtService;

    public DevAuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/token")
    public DevTokenResponse issueToken(@Valid @RequestBody DevTokenRequest request) {
        Set<String> roles = (request.roles() == null || request.roles().isEmpty())
                ? Set.of(Roles.USER)
                : new HashSet<>(request.roles());
        String token = jwtService.issueToken(request.subjectId(), request.tenantId(), roles, Duration.ofHours(1));
        return new DevTokenResponse(token);
    }
}

record DevTokenRequest(@NotBlank String subjectId, @NotBlank String tenantId, List<String> roles) {
}

record DevTokenResponse(String accessToken) {
}
