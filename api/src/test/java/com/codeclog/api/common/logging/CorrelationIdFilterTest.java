package com.codeclog.api.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeclog.api.common.config.CorrelationIdConstants;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    @DisplayName("a well-formed inbound id is reused so a request can be traced end to end")
    void reusesInboundId() {
        assertThat(CorrelationIdFilter.resolve("abc-123_DEF.4")).isEqualTo("abc-123_DEF.4");
    }

    @ParameterizedTest
    @DisplayName("anything that could poison a log line is replaced, not trusted")
    @ValueSource(
            strings = {
                "has spaces",
                "line\nbreak",
                "semi;colon",
                "",
                "0123456789012345678901234567890123456789012345678901234567890123456789"
            })
    void replacesUnsafeInboundId(String inbound) {
        String resolved = CorrelationIdFilter.resolve(inbound);

        assertThat(resolved).isNotEqualTo(inbound);
        assertThat(UUID.fromString(resolved)).isNotNull();
    }

    @Test
    @DisplayName("a missing id is generated")
    void generatesWhenAbsent() {
        assertThat(UUID.fromString(CorrelationIdFilter.resolve(null))).isNotNull();
    }

    @Test
    @DisplayName("the id reaches the MDC during the request and is echoed on the response")
    void publishesIdToMdcAndResponse() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader(CorrelationIdConstants.HEADER, "trace-42");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain() {
            String seenInsideChain;

            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInsideChain = MDC.get(CorrelationIdConstants.MDC_KEY);
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(chain.seenInsideChain).isEqualTo("trace-42");
        assertThat(response.getHeader(CorrelationIdConstants.HEADER)).isEqualTo("trace-42");
    }

    @Test
    @DisplayName("the MDC is cleared afterwards so ids never bleed between pooled threads")
    void clearsMdcAfterRequest() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }
}
