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
 * Qui si decide solo cosa e' pubblico e cosa richiede login.
 * I permessi per ruolo (CUSTOMER / ADMIN) sono sui singoli metodi dei controller con @PreAuthorize
 * (abilitato da @EnableMethodSecurity), vedi functional-analysis.md sez. 2.1 e tabella endpoint.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // Endpoint di springdoc: la UI di Swagger e la specifica OpenAPI generata
    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API REST stateless (JWT in arrivo): niente sessione, quindi niente CSRF
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SWAGGER_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v*/auth/register", "/api/v*/auth/login").permitAll()
                        .anyRequest().authenticated())
                // Temporaneo: da sostituire con il filtro JWT quando sara' implementato il login
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
