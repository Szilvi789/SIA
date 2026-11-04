package com.personalai.platform.writing.export;

import java.util.Locale;

public enum ExportFormat {
    TXT, PDF, DOCX, PNG;

    public String extension() { return name().toLowerCase(Locale.ROOT); }
}
