package com.bonifybank.bonify.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * FR-PLT-01: declares the bearer scheme, so Swagger UI shows the "Authorize" button and sends
 * the access token returned by login in the Authorization header of every call.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Bonify API", version = "v1", description = "REST API of a simplified retail bank"),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
@SecurityScheme(name = OpenApiConfig.BEARER_SCHEME, type = SecuritySchemeType.HTTP, scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";
}
