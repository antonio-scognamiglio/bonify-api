package com.bonifybank.bonify.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Common prefix for the project REST controllers (modules package): /api/{version}/...
 * The version is read from the path segment (see spring.mvc.apiversion.* in application.properties)
 * and used by Spring to select the handler method through the "version" attribute of the mappings.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // The "v" is in the pattern (not in the variable) so Swagger also generates /api/v1/... instead of /api/1/...
    public static final String API_PREFIX = "/api/v{version}";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Project controllers only: without the package filter, @RestControllers from external
        // libraries (e.g. springdoc /v3/api-docs) would also end up under /api/{version}.
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forBasePackage("com.bonifybank.bonify.modules")
                .and(HandlerTypePredicate.forAnnotation(RestController.class)));
    }
}
