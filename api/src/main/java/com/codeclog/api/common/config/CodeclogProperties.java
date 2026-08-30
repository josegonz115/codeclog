package com.codeclog.api.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application-owned configuration, bound from the {@code codeclog.*} namespace.
 *
 * <p>Everything environment-specific lives here rather than being read ad hoc, so the set of knobs
 * an operator has to care about is enumerable from one class — and so a missing or malformed value
 * fails at startup rather than at the first request that happens to need it.
 *
 * @param allowedOrigins origins permitted by CORS; the SPA deploys separately from the API
 * @param publicUrl the externally reachable base URL, advertised as the server in the OpenAPI
 *     document so generated clients point at the right host
 */
@Validated
@ConfigurationProperties(prefix = "codeclog")
public record CodeclogProperties(@NotEmpty List<String> allowedOrigins, @NotBlank String publicUrl) {}
