package com.pos.identityaccess.api;

import com.pos.identityaccess.api.dto.ApiMeta;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public final class RequestContext {

    private static final String REQUEST_ID_ATTRIBUTE = RequestContext.class.getName() + ".requestId";

    private RequestContext() {
    }

    public static void ensureRequestId(HttpServletRequest request) {
        if (request.getAttribute(REQUEST_ID_ATTRIBUTE) == null) {
            request.setAttribute(REQUEST_ID_ATTRIBUTE, "req_" + UUID.randomUUID());
        }
    }

    public static void ensureCorrelationId(HttpServletRequest request) {
        if (request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE) == null) {
            request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "cor_" + UUID.randomUUID());
        }
    }

    public static ApiMeta meta(HttpServletRequest request) {
        ensureRequestId(request);
        ensureCorrelationId(request);
        String correlationId = (String) request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        String requestId = (String) request.getAttribute(REQUEST_ID_ATTRIBUTE);
        return new ApiMeta(requestId, correlationId);
    }

    public static long version(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new com.pos.identityaccess.application.exception.ValidationException(
                    "If-Match header is required");
        }
        String value = ifMatch.trim().replace("\"", "");
        int separator = value.lastIndexOf('-');
        String number = separator >= 0 ? value.substring(separator + 1) : value;
        try {
            long version = Long.parseLong(number);
            if (version < 1) {
                throw new NumberFormatException("non-positive");
            }
            return version;
        } catch (NumberFormatException exception) {
            throw new com.pos.identityaccess.application.exception.ValidationException(
                    "If-Match header must contain a positive version");
        }
    }

    public static String idempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new com.pos.identityaccess.application.exception.ValidationException(
                    "Idempotency-Key header is required");
        }
        return key;
    }
}
