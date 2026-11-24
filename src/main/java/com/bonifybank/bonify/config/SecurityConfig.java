package com.bonifybank.bonify.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Decides only what is public and what requires authentication.
 * Role-based permissions (CUSTOMER / ADMIN) live on the controller methods via @PreAuthorize
 * (enabled by @EnableMethodSecurity); see functional-analysis.md section 2.1 and the endpoint table.
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
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
