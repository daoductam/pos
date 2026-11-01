package com.pos.identityaccess.api.dto;

import java.util.List;

public record ErrorBody(
        String code,
        String message,
        List<String> details,
        String requestId,
        String correlationId) {
}
