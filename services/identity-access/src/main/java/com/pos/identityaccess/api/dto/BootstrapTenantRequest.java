package com.pos.identityaccess.api.dto;

public record BootstrapTenantRequest(String name, String ownerEmail, String ownerDisplayName) {
}
