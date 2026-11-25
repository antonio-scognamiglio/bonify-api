package com.bonifybank.bonify.config;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Decides only what is public and what requires authentication.
 * Role-based permissions (CUSTOMER / ADMIN) live on the controller methods
 * via @PreAuthorize
 * (enabled by @EnableMethodSecurity); see functional-analysis.md section 2.1
 * and the endpoint table.
 * 401/403 from the security filter chain are returned as RFC 7807 ProblemDetail
 * by the
 * exceptionHandling entry point / access denied handler below.
 *
 * Authentication is a JWT in the "Authorization: Bearer" header, validated by
 * the OAuth2 resource server
 * support (beans in {@link JwtConfig}). Missing, malformed or expired token ->
 * 401; wrong role -> 403.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // springdoc endpoints: the Swagger UI and the generated OpenAPI spec
    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    private static final Pattern PUBLIC_AUTH_PATH =
            Pattern.compile("^/api/v[^/]+/auth/(register|login|refresh|logout)$");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http
                // Stateless REST API authenticated by JWT: no session, hence no CSRF
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SWAGGER_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v*/auth/register", "/api/v*/auth/login",
                                "/api/v*/auth/refresh", "/api/v*/auth/logout").permitAll()
                        // Error dispatch: without it a guest would get 401 instead of the real error
                        // (400, 409...)
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver())
                        // Invalid or expired token: the bearer filter answers by itself, with its own
                        // entry point (empty body) unless the RFC 7807 one is set here too
                        .authenticationEntryPoint(this::unauthorized)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(this::unauthorized)
                        .accessDeniedHandler(this::forbidden));
        return http.build();
    }

    /**
     * The public auth endpoints ignore the Authorization header. Otherwise an expired access token,
     * attached by a client-side HTTP interceptor, would make /auth/refresh fail with 401 before
     * reaching the controller: the bearer filter rejects invalid tokens even on permitAll paths.
     */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> PUBLIC_AUTH_PATH.matcher(request.getRequestURI()).matches()
                ? null
                : delegate.resolve(request);
    }

    // Token missing/malformed/expired → 401 UNAUTHORIZED (RFC 7807)
    private void unauthorized(HttpServletRequest request, HttpServletResponse response, Exception ex)
            throws IOException {
        writeProblemDetail(response, 401, "UNAUTHORIZED", "Unauthorized", "Authentication required",
                request.getRequestURI());
    }

    // Role or ownership check failed → 403 FORBIDDEN (RFC 7807)
    private void forbidden(HttpServletRequest request, HttpServletResponse response, Exception ex) throws IOException {
        writeProblemDetail(response, 403, "FORBIDDEN", "Forbidden", "Access denied", request.getRequestURI());
    }

    private static void writeProblemDetail(HttpServletResponse response, int status, String code, String title,
            String detail, String instance) throws IOException {
        String type = "https://bonify.example/errors/" + code.toLowerCase(Locale.ROOT).replace('_', '-');
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write(
                "{\"type\":\"" + type + "\""
                        + ",\"title\":\"" + title + "\""
                        + ",\"status\":" + status
                        + ",\"detail\":\"" + detail + "\""
                        + ",\"instance\":\"" + instance + "\""
                        + ",\"code\":\"" + code + "\""
                        + ",\"timestamp\":\"" + Instant.now() + "\"}");
    }
}
