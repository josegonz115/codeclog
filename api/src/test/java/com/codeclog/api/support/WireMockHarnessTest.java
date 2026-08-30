package com.codeclog.api.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Proves the WireMock harness works before M3 depends on it, the same way the Testcontainers
 * harness is proven by {@code SchemaBaselineIT}. A stubbing harness discovered to be broken halfway
 * through building the sync pipeline is a bad day.
 *
 * <p>The stub deliberately mirrors the shape of the real {@code GetOwnedGames} response (§5.1), so
 * this doubles as the reference for the fixtures M3 will grow.
 */
class WireMockHarnessTest {

    @RegisterExtension
    static WireMockExtension steam =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Test
    @DisplayName("a stubbed Steam response is served over real HTTP")
    void servesStubbedSteamResponse() throws Exception {
        steam.stubFor(get(urlPathEqualTo("/IPlayerService/GetOwnedGames/v1/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {
                                  "response": {
                                    "game_count": 1,
                                    "games": [
                                      {
                                        "appid": 620,
                                        "name": "Portal 2",
                                        "playtime_forever": 1337,
                                        "rtime_last_played": 1609459200
                                      }
                                    ]
                                  }
                                }
                                """)));

        HttpResponse<String> response = call(
                "/IPlayerService/GetOwnedGames/v1/?steamid=76561197960287930&include_appinfo=1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Portal 2").contains("\"playtime_forever\": 1337");
    }

    @Test
    @DisplayName("an unstubbed path is a 404, so a missing stub fails loudly")
    void unstubbedPathIsNotFound() throws Exception {
        assertThat(call("/ISteamUser/GetPlayerSummaries/v2/").statusCode()).isEqualTo(404);
    }

    private static HttpResponse<String> call(String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(steam.baseUrl() + path))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
