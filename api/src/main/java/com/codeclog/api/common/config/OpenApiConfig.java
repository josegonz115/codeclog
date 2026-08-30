package com.codeclog.api.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The licence name only, deliberately: see the "OpenAPI licence URL" entry in DECISIONS.md. The
 * repo's LICENSE file is what actually backs the claim.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI codeclogOpenApi(CodeclogProperties properties) {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("CodecLog API")
                                .version("v1")
                                .description(
                                        "Social game backlog tracker. Steam library sync, reviews, follows and a feed.")
                                .license(new License().name("MIT")))
                .servers(List.of(new Server().url(properties.publicUrl()).description("CodecLog API")));
    }
}
