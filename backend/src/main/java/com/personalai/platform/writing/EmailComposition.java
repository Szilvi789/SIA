package com.personalai.platform.writing;

/** Message state only. Authentication secrets are never part of drafts or browser DTOs. */
public record EmailComposition(String accountId, String recipientName, String to, String cc, String bcc,
        String subject, String body, GenerationPart generationPart) {
    public enum GenerationPart { COMPLETE, SUBJECT, BODY }
    public EmailComposition {
        accountId = WritingValidation.text(accountId, 80, "accountId");
        recipientName = WritingValidation.text(recipientName, 200, "recipientName");
        to = WritingValidation.text(to, 2000, "to");
        cc = WritingValidation.text(cc, 2000, "cc");
        bcc = WritingValidation.text(bcc, 2000, "bcc");
        subject = WritingValidation.text(subject, 500, "subject");
        body = WritingValidation.text(body, 65000, "body");
        generationPart = generationPart == null ? GenerationPart.COMPLETE : generationPart;
    }
    public static EmailComposition empty() { return new EmailComposition(null, null, null, null, null, null, null, null); }
}
