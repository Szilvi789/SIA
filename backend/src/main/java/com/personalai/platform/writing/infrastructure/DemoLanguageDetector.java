package com.personalai.platform.writing.infrastructure;

import com.personalai.platform.writing.DetectedLanguage;
import com.personalai.platform.writing.DetectedLanguage.Language;
import com.personalai.platform.writing.LanguageDetector;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Deliberately modest demo heuristic, not an accurate language detector. No invented confidence. */
@Component
public class DemoLanguageDetector implements LanguageDetector {
    @Override public DetectedLanguage detect(String text) {
        var words = new java.util.HashSet<>(java.util.Arrays.asList(text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+", -1)));
        return detectWords(words);
    }
    private DetectedLanguage detectWords(Set<String> words) {
        boolean en = words.stream().anyMatch(Set.of("the", "hello", "please", "dear", "thank", "write")::contains);
        boolean de = words.stream().anyMatch(Set.of("der", "die", "das", "bitte", "hallo", "danke", "schreiben")::contains);
        boolean hu = words.stream().anyMatch(Set.of("kérem", "szia", "köszönöm", "egy", "és", "levél", "szeretnék")::contains);
        int matches = (en ? 1 : 0) + (de ? 1 : 0) + (hu ? 1 : 0);
        var language = matches > 1 ? Language.MIXED : hu ? Language.HU : de ? Language.DE : en ? Language.EN : Language.UNKNOWN;
        return new DetectedLanguage(language, null, true, "Demo keyword heuristic; not reliable language detection.");
    }
}
