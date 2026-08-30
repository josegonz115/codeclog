package com.codeclog.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeclogApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeclogApiApplication.class, args);
    }
}
