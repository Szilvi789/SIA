package com.personalai.platform.writing;

public record DetectedLanguage(Language language, Double confidence, boolean mock, String method) {
    public enum Language { EN, DE, HU, UNKNOWN, MIXED }
}
