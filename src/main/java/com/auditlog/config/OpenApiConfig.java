package com.auditlog.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API documentation deliverable (assignment framing: "runnable, with setup instructions").
 * Registers a Bearer-JWT security scheme so Swagger UI's "Authorize" button lets a caller
 * paste a token from POST /dev/auth/token once and have it attached to every "Try it out"
 * call, rather than needing to add the Authorization header on each request individually.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI auditLogServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Audit Log Service")
                        .description("Tamper-evident audit log service. Get a token from POST /dev/auth/token "
                                + "(NOT a real auth endpoint -- see docs/SECURITY.md), then Authorize with it below.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
