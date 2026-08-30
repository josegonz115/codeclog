package com.codeclog.api.common.config;

/** Shared name for the request correlation id, used by the filter, the MDC and CORS exposure. */
public final class CorrelationIdConstants {

    /** Inbound and outbound HTTP header carrying the correlation id. */
    public static final String HEADER = "X-Correlation-Id";

    /** SLF4J MDC key; also referenced by the structured logging config. */
    public static final String MDC_KEY = "correlationId";

    private CorrelationIdConstants() {}
}
