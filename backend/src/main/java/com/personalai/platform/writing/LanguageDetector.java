package com.personalai.platform.writing;

public interface LanguageDetector {
    DetectedLanguage detect(String text);
}
