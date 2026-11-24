package com.bonifybank.bonify.config;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Decides only what is public and what requires authentication.
 * Role-based permissions (CUSTOMER / ADMIN) live on the controller methods via @PreAuthorize
 * (enabled by @EnableMethodSecurity); see functional-analysis.md section 2.1 and the endpoint table.
 * 401/403 from the security filter chain are returned as RFC 7807 ProblemDetail by the
 * exceptionHandling entry point / access denied handler below.
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

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless REST API (JWT coming): no session, hence no CSRF
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SWAGGER_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v*/auth/register", "/api/v*/auth/login").permitAll()
                        .anyRequest().authenticated())
                // Temporary: to be replaced by the JWT filter once login is implemented
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(this::unauthorized)
                        .accessDeniedHandler(this::forbidden));
        return http.build();
    }

    // Token missing/malformed/expired → 401 UNAUTHORIZED (RFC 7807)
    private void unauthorized(HttpServletRequest request, HttpServletResponse response, Exception ex) throws IOException {
        writeProblemDetail(response, 401, "UNAUTHORIZED", "Unauthorized", "Authentication required", request.getRequestURI());
    }

    // Role or ownership check failed → 403 FORBIDDEN (RFC 7807)
    private void forbidden(HttpServletRequest request, HttpServletResponse response, Exception ex) throws IOException {
        writeProblemDetail(response, 403, "FORBIDDEN", "Forbidden", "Access denied", request.getRequestURI());
    }

    private static void writeProblemDetail(HttpServletResponse response, int status, String code, String title, String detail, String instance) throws IOException {
        String type = "https://bonify.example/errors/" + code.toLowerCase();
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write(
                "{\"type\":\"" + type + "\""
                + ",\"title\":\"" + title + "\""
                + ",\"status\":" + status
                + ",\"detail\":\"" + detail + "\""
                + ",\"instance\":\"" + instance + "\""
                + ",\"code\":\"" + code + "\""
                + ",\"timestamp\":\"" + LocalDateTime.now() + "\"}"
        );
    }
}
