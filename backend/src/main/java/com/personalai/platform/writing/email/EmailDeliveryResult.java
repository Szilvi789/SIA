package com.personalai.platform.writing.email;

public record EmailDeliveryResult(String previewId, EmailDeliveryState state, String message, boolean mock) { }
