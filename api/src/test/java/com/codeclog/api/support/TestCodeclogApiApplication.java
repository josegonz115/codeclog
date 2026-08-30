package com.codeclog.api.support;

import com.codeclog.api.CodeclogApiApplication;
import org.springframework.boot.SpringApplication;

/**
 * Runs the app against a throwaway Postgres container: {@code ./gradlew bootTestRun}.
 *
 * <p>Useful when you want the API up without Docker Compose, or want a guaranteed-clean database.
 */
public final class TestCodeclogApiApplication {

    private TestCodeclogApiApplication() {}

    public static void main(String[] args) {
        SpringApplication.from(CodeclogApiApplication::main)
                .with(PostgresContainerConfiguration.class)
                .withAdditionalProfiles("local")
                .run(args);
    }
}
