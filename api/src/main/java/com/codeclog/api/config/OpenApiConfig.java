package com.codeclog.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI codeclogOpenApi(@Value("${codeclog.public-url:http://localhost:8080}") String publicUrl) {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("CodecLog API")
                                .version("v1")
                                .description(
                                        "Social game backlog tracker. Steam library sync, reviews, follows and a feed.")
                                .license(new License().name("MIT")))
                .servers(List.of(new Server().url(publicUrl).description("CodecLog API")));
    }
}
