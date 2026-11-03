package com.personalai.platform.writing.email;

public record SendEmailRequest(String previewId, boolean confirmed, String idempotencyKey) { }
