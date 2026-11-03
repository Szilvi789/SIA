package com.personalai.platform.writing;

public enum WritingWorkflow {
    GENERAL("General writing", true), EMAIL("Email writing", true), TRANSLATION("Translation", true),
    CV("CV / résumé", false), COVER_LETTER("Cover letter", false),
    JOB_APPLICATION("Job application package", false), CUSTOM_DOCUMENT("Custom document", true);

    private final String label;
    private final boolean supported;

    WritingWorkflow(String label, boolean supported) { this.label = label; this.supported = supported; }
    public String label() { return label; }
    public boolean supported() { return supported; }
}
