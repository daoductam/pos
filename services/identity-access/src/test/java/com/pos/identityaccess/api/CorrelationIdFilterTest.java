package com.pos.identityaccess.api;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private static final Pattern TRACEPARENT =
            Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");

    @Test
    void shouldPreserveSafeCorrelationAndTraceParent_whenHeadersAreValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "corr-demo-1");
        request.addHeader(CorrelationIdFilter.TRACEPARENT_HEADER,
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        new CorrelationIdFilter().doFilter(request, response, chain(called));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("corr-demo-1");
        assertThat(response.getHeader(CorrelationIdFilter.TRACEPARENT_HEADER))
                .isEqualTo(request.getHeader(CorrelationIdFilter.TRACEPARENT_HEADER));
        assertThat(called).isTrue();
    }

    @Test
    void shouldGenerateSafeIdentifiersAndClearMdc_whenHeadersAreUnsafe() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "bad\r\nX-Leak: true");
        request.addHeader(CorrelationIdFilter.TRACEPARENT_HEADER, "not-a-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorrelationIdFilter().doFilter(request, response, chain(new AtomicBoolean()));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).startsWith("cor_");
        assertThat(response.getHeader(CorrelationIdFilter.TRACEPARENT_HEADER))
                .matches(TRACEPARENT);
        assertThat(org.slf4j.MDC.get("correlation_id")).isNull();
        assertThat(org.slf4j.MDC.get("traceparent")).isNull();
    }

    private static FilterChain chain(AtomicBoolean called) {
        return (request, response) -> called.set(true);
    }
}
