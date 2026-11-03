package com.personalai.platform.common;

public class InvalidConfigurationException extends RuntimeException {
    private final String field;

    public InvalidConfigurationException(String message) {
        this(null, message);
    }

    public InvalidConfigurationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
