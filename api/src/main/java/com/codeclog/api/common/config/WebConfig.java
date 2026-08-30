package com.codeclog.api.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is restricted to the known frontend origins (§10). The API and the SPA deploy separately, so
 * this is a real cross-origin setup, not a formality.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CodeclogProperties properties;

    public WebConfig(CodeclogProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                .addMapping("/api/**")
                .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(CorrelationIdConstants.HEADER)
                .allowCredentials(true)
                .maxAge(3600);
    }
}
