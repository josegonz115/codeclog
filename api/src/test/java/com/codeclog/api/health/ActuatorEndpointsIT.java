package com.codeclog.api.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeclog.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;

/**
 * The operational surface the deployment platform depends on (§10). If these move or stop reporting
 * the database, a bad deploy stays in rotation — worth a test even though nothing here is "business
 * logic".
 */
// @SpringBootTest disables metrics export by default; the scrape endpoint is part of the
// operational contract, so this test opts back in.
@AutoConfigureMetrics
class ActuatorEndpointsIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate rest;

    @Test
    @DisplayName("health is UP and, on default config, leaks no component detail")
    void healthIsUp() {
        var response = rest.getForEntity(url("/actuator/health"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        // show-details defaults to `never`: an unauthenticated caller learns liveness, nothing more.
        assertThat(response.getBody()).doesNotContain("components");
    }

    @Test
    @DisplayName("liveness and readiness probes are exposed for the platform")
    void probesAreExposed() {
        assertThat(rest.getForEntity(url("/actuator/health/liveness"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity(url("/actuator/health/readiness"), String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("prometheus scrape endpoint serves metrics tagged with the application name")
    void prometheusEndpointServesMetrics() {
        var response = rest.getForEntity(url("/actuator/prometheus"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("application=\"codeclog-api\"");
    }

    @Test
    @DisplayName("only the intended endpoints are exposed")
    void unexposedEndpointsStayClosed() {
        assertThat(rest.getForEntity(url("/actuator/env"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity(url("/actuator/beans"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
