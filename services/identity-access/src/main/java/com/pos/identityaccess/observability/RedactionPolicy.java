package com.pos.identityaccess.observability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RedactionPolicy {

    private static final List<String> SECRET_KEYS = List.of(
            "authorization", "password", "pin", "token", "secret", "deviceproof");

    private RedactionPolicy() {
    }

    public static Map<String, Object> redact(Map<String, ?> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalized = key.toLowerCase(Locale.ROOT).replace("_", "");
            if (SECRET_KEYS.stream().anyMatch(normalized::contains)) {
                return;
            }
            if (normalized.contains("email")) {
                result.put(key, maskEmail(value == null ? null : value.toString()));
            } else if (normalized.contains("phone")) {
                result.put(key, maskPhone(value == null ? null : value.toString()));
            } else {
                result.put(key, redactNested(value));
            }
        });
        return Collections.unmodifiableMap(result);
    }

    private static Object redactNested(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> nested.put(String.valueOf(key), nestedValue));
            return redact(nested);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(RedactionPolicy::redactNested).toList();
        }
        return value;
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        int visible = Math.min(3, phone.length());
        return "*".repeat(phone.length() - visible) + phone.substring(phone.length() - visible);
    }
}
