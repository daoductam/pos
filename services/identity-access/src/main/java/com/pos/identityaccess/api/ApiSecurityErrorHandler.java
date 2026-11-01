package com.pos.identityaccess.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.identityaccess.api.dto.ApiMeta;
import com.pos.identityaccess.api.dto.ErrorBody;
import com.pos.identityaccess.api.dto.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public final class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ApiSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(response, 401, "unauthorized", "Authentication is required", request);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, 403, "forbidden", "The caller is not allowed to access this resource", request);
    }

    private void write(HttpServletResponse response, int status, String code, String message,
                       HttpServletRequest request) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiMeta meta = RequestContext.meta(request);
        objectMapper.writeValue(response.getWriter(), new ErrorEnvelope(
                new ErrorBody(code, message, List.of(), meta.requestId(), meta.correlationId())));
    }
}
