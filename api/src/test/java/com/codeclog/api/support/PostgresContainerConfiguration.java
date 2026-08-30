package com.codeclog.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The one place a Postgres container is defined.
 *
 * <p>Declaring it as a bean (rather than a per-class {@code @Container}) means every test that
 * imports this configuration shares a Spring context <em>and</em> a container, so the suite pays the
 * container start cost once. {@code @ServiceConnection} wires the JDBC URL, so no test carries
 * datasource properties of its own.
 *
 * <p>The image is pinned: the point of an integration test is to run against the same major version
 * production runs (§3).
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfiguration {

    static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18.6-alpine");

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource") // Testcontainers stops the container via its own JVM shutdown hook.
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName("codeclog")
                .withUsername("codeclog")
                .withPassword("codeclog")
                .withReuse(true);
    }
}
