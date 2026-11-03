package com.personalai.platform.writing;

import java.util.List;

/** Structured preferences are distinct from task text and editable Markdown configuration. */
public record WritingPreferences(String presetId, InputLanguage inputLanguage, OutputLanguage outputLanguage,
        TranslationMode translationMode, Formality formality, Tone tone, Length length, String audience,
        String purpose, String customInstructions, List<String> selectedRuleIds) {
    public enum InputLanguage { AUTO, EN, DE, HU }
    public enum OutputLanguage { EN, DE, HU }
    public enum TranslationMode { NATURAL, LITERAL, PROFESSIONAL, REWRITE, GRAMMAR }
    public enum Formality { NEUTRAL, FORMAL, INFORMAL }
    public enum Tone { PROFESSIONAL, FRIENDLY, DIPLOMATIC, CONCISE, CUSTOM }
    public enum Length { SHORT, MEDIUM, LONG }

    public WritingPreferences {
        presetId = WritingValidation.text(presetId, 80, "presetId");
        inputLanguage = inputLanguage == null ? InputLanguage.AUTO : inputLanguage;
        outputLanguage = outputLanguage == null ? OutputLanguage.EN : outputLanguage;
        translationMode = translationMode == null ? TranslationMode.NATURAL : translationMode;
        formality = formality == null ? Formality.NEUTRAL : formality;
        tone = tone == null ? Tone.PROFESSIONAL : tone;
        length = length == null ? Length.MEDIUM : length;
        audience = WritingValidation.text(audience, 500, "audience");
        purpose = WritingValidation.text(purpose, 1000, "purpose");
        customInstructions = WritingValidation.text(customInstructions, 8000, "customInstructions");
        selectedRuleIds = WritingValidation.ids(selectedRuleIds);
    }
    public static WritingPreferences defaults() {
        return new WritingPreferences(null, null, null, null, null, null, null, null, null, null, null);
    }
}
