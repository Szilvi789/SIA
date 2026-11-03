package com.personalai.platform.writing.infrastructure;

import com.personalai.platform.providers.ProviderRequest;
import com.personalai.platform.writing.WritingPreferences.OutputLanguage;
import com.personalai.platform.writing.WritingWorkflow;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Fixed, explicit demo samples. This adapter does not infer, translate, or apply Markdown semantics. */
public final class MockWritingRenderer {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private MockWritingRenderer() { }

    public static String render(ProviderRequest request) {
        var context = request.writingContext();
        var language = context.preferences().outputLanguage();
        boolean email = context.workflow() == WritingWorkflow.EMAIL;
        String label = switch (language) {
            case DE -> "DEMO – keine KI-Generierung oder echte Übersetzung. Bitte prüfen und bearbeiten.";
            case HU -> "DEMÓ – nem valódi MI-generálás vagy fordítás. Kérjük, ellenőrizze és szerkessze.";
            case EN -> "DEMO – no AI generation or actual translation. Review and edit this sample.";
        };
        String sample = switch (language) {
            case DE -> "[Entwurf]\n\nBitte ergänzen Sie hier Ihre geprüften Angaben und den gewünschten nächsten Schritt.";
            case HU -> "[Piszkozat]\n\nEgészítse ki az ellenőrzött adatokkal és a kívánt következő lépéssel.";
            case EN -> "[Draft]\n\nAdd your verified details and the requested next step here.";
        };
        String subject = email ? switch (language) {
            case DE -> "Demo: Ihre Nachricht";
            case HU -> "Demó: az Ön üzenete";
            case EN -> "Demo: your message";
        } : "";
        String sourceLabel = switch (language) {
            case DE -> "Originaleingabe (unverändert; nicht übersetzt):";
            case HU -> "Eredeti szöveg (változatlan; nincs lefordítva):";
            case EN -> "Original input (unchanged; not translated):";
        };
        // No greeting or closing is invented: presets can explicitly prohibit them.
        String content = label + "\n\n" + sample + "\n\n" + sourceLabel + "\n" + request.message();
        return JSON.writeValueAsString(Map.of("title", context.workflow().label() + " · demo", "content", content, "subject", subject));
    }
}
