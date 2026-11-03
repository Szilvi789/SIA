package com.personalai.platform.writing;

import com.personalai.platform.common.InvalidConfigurationException;
import java.util.HashSet;
import java.util.List;

final class WritingValidation {
    private WritingValidation() { }
    static String text(String value, int limit, String field) {
        var normalized = value == null ? "" : value;
        if (normalized.length() > limit || normalized.indexOf('\0') >= 0) {
            throw new InvalidConfigurationException(field, field + " exceeds its limit or contains invalid characters.");
        }
        return normalized;
    }
    static String required(String value, int limit, String field) {
        var normalized = text(value, limit, field).strip();
        if (normalized.isBlank()) throw new InvalidConfigurationException(field, field + " is required.");
        return normalized;
    }
    static List<String> ids(List<String> value) {
        if (value == null) return List.of();
        if (value.size() > 50 || value.stream().anyMatch(id -> id == null || !id.matches("[a-z0-9][a-z0-9-]{0,79}"))
                || new HashSet<>(value).size() != value.size()) {
            throw new InvalidConfigurationException("selectedRuleIds", "Select up to 50 unique logical rule IDs.");
        }
        return List.copyOf(value);
    }
}
