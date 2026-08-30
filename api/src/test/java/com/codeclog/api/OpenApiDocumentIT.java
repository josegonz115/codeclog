package com.codeclog.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeclog.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;

class OpenApiDocumentIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate rest;

    @Test
    @DisplayName("OpenAPI document is generated and describes CodecLog")
    void apiDocsAreServed() {
        var response = rest.getForEntity(url("/v3/api-docs"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"title\":\"CodecLog API\"").contains("\"openapi\":\"3.");
    }

    @Test
    @DisplayName("Swagger UI is reachable at /docs")
    void swaggerUiIsReachable() {
        var response = rest.getForEntity(url("/docs"), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is3xxRedirection())
                .isTrue();
    }
}
