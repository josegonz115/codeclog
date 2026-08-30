package com.codeclog.api.support;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for tests that need the real thing: full context, real Postgres, real Flyway run.
 *
 * <p>Slice tests should not extend this — they exist precisely to avoid it.
 *
 * <p>Spring Boot 4 moved {@code TestRestTemplate} into its own module and no longer registers it
 * implicitly, hence the explicit opt-in.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(PostgresContainerConfiguration.class)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @LocalServerPort protected int port;

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }
}
