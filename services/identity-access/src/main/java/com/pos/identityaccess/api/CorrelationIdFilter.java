package com.pos.identityaccess.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public final class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".correlationId";
    public static final String TRACEPARENT_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".traceparent";
    private static final Pattern SAFE_CORRELATION_ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}");
    private static final Pattern TRACEPARENT =
            Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String correlationId = incoming == null || !SAFE_CORRELATION_ID.matcher(incoming).matches()
                ? "cor_" + UUID.randomUUID()
                : incoming;
        String incomingTraceparent = request.getHeader(TRACEPARENT_HEADER);
        String traceparent = TRACEPARENT.matcher(incomingTraceparent == null ? "" : incomingTraceparent).matches()
                ? incomingTraceparent
                : generatedTraceparent();
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        request.setAttribute(TRACEPARENT_ATTRIBUTE, traceparent);
        response.setHeader(HEADER, correlationId);
        response.setHeader(TRACEPARENT_HEADER, traceparent);
        RequestContext.ensureRequestId(request);
        MDC.put("correlation_id", correlationId);
        MDC.put("traceparent", traceparent);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlation_id");
            MDC.remove("traceparent");
        }
    }

    private static String generatedTraceparent() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return "00-" + traceId + "-" + spanId + "-01";
    }
}
