package com.bonifybank.bonify.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Prefisso comune per i controller REST del progetto (package modules): /api/{version}/...
 * La versione viene letta dal segmento di path (vedi spring.mvc.apiversion.* in application.properties)
 * e usata da Spring per scegliere il metodo tramite l'attributo "version" dei mapping.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // La "v" e' nel pattern (non nella variabile) cosi' anche Swagger genera /api/v1/... e non /api/1/...
    public static final String API_PREFIX = "/api/v{version}";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Solo i controller del progetto: senza il filtro sul package anche i @RestController
        // di librerie esterne (es. springdoc /v3/api-docs) finirebbero sotto /api/{version}.
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forBasePackage("com.bonifybank.bonify.modules")
                .and(HandlerTypePredicate.forAnnotation(RestController.class)));
    }
}
