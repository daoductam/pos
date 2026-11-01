package com.pos.identityaccess.api.dto;

public record ApiEnvelope<T>(T data, ApiMeta meta) {
}
