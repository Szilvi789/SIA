package com.personalai.platform.writing.export;

/** A private export reference. Original titles and absolute storage paths never leave this API. */
public record GeneratedExport(String id, ExportFormat format) {}
