package com.codeclog.api.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeclog.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;

/**
 * The operational surface the deployment platform depends on (§10). If these move or stop reporting
 * the database, a bad deploy stays in rotation — worth a test even though nothing here is "business
 * logic".
 *
 * <p>Actuator lives on a separate management port so the deploy platform never exposes it publicly;
 * the split itself is part of the contract, so both sides of it are asserted here. {@code
 * RANDOM_PORT} randomises the management port too when one is configured.
 */
// @SpringBootTest disables metrics export by default; the scrape endpoint is part of the
// operational contract, so this test opts back in.
@AutoConfigureMetrics
class ActuatorEndpointsIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate rest;

    @LocalManagementPort private int managementPort;

    private String managementUrl(String path) {
        return "http://localhost:" + managementPort + path;
    }

    @Test
    @DisplayName("health is UP and, on default config, leaks no component detail")
    void healthIsUp() {
        var response = rest.getForEntity(managementUrl("/actuator/health"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        // show-details defaults to `never`: an unauthenticated caller learns liveness, nothing more.
        assertThat(response.getBody()).doesNotContain("components");
    }

    @Test
    @DisplayName("liveness and readiness probes are exposed on the management port")
    void probesAreExposed() {
        assertThat(rest.getForEntity(managementUrl("/actuator/health/liveness"), String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity(managementUrl("/actuator/health/readiness"), String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("probes are republished on the public port for platform health checks")
    void probesAreRepublishedOnPublicPort() {
        // The platform's health check and the Docker HEALTHCHECK hit the public port; these are
        // the only operational paths that exist there.
        assertThat(rest.getForEntity(url("/livez"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity(url("/readyz"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("prometheus scrape endpoint serves metrics tagged with the application name")
    void prometheusEndpointServesMetrics() {
        var response = rest.getForEntity(managementUrl("/actuator/prometheus"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("application=\"codeclog-api\"");
    }

    @Test
    @DisplayName("no actuator endpoint is reachable through the public port")
    void actuatorIsAbsentFromPublicPort() {
        // This is the security property the management port exists for: the deploy platform
        // exposes only the app port, so nothing under /actuator may be served there.
        for (String path :
                new String[] {
                    "/actuator", "/actuator/health", "/actuator/prometheus", "/actuator/metrics"
                }) {
            assertThat(rest.getForEntity(url(path), String.class).getStatusCode())
                    .as("public port must not serve %s", path)
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    @DisplayName("only the intended endpoints are exposed")
    void unexposedEndpointsStayClosed() {
        assertThat(rest.getForEntity(managementUrl("/actuator/env"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity(managementUrl("/actuator/beans"), String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
