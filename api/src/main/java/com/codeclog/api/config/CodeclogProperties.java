package com.codeclog.api.config;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application-owned configuration, bound from the {@code codeclog.*} namespace.
 *
 * <p>Everything environment-specific lives here rather than being read ad hoc, so the set of knobs
 * an operator has to care about is enumerable from one class.
 */
@Validated
@ConfigurationProperties(prefix = "codeclog")
public record CodeclogProperties(@NotEmpty List<String> allowedOrigins) {}
