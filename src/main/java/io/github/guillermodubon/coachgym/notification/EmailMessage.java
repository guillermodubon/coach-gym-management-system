package io.github.guillermodubon.coachgym.notification;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValuePolicy;

/** Provider-neutral email envelope with one approved canonical attachment. */
public record EmailMessage(
        String recipient,
        String fromAddress,
        String fromName,
        String replyTo,
        String subject,
        String plainTextBody,
        String htmlBody,
        EmailAttachment attachment) {

    private static final int MAX_BODY_LENGTH = 100_000;

    public EmailMessage {
        recipient = EmailDeliveryValuePolicy.normalizeRecipient(recipient);
        fromAddress = EmailDeliveryValuePolicy.normalizeAddress(fromAddress, "Email sender address");
        fromName = EmailDeliveryValuePolicy.normalizeOptionalText(
                fromName, "Email sender name", EmailDeliveryValuePolicy.MAX_SUBJECT_LENGTH);
        if (fromName != null) {
            EmailDeliveryValuePolicy.rejectHeaderInjection(fromName, "Email sender name");
        }
        replyTo = EmailDeliveryValuePolicy.normalizeOptionalAddress(replyTo, "Email reply-to address");
        subject = EmailDeliveryValuePolicy.normalizeSubject(subject);
        plainTextBody = requiredBody(plainTextBody, "Email plain-text body");
        htmlBody = requiredBody(htmlBody, "Email HTML body");
        if (attachment == null) {
            throw new IllegalArgumentException("Email attachment is required.");
        }
    }

    @Override
    public String toString() {
        return "EmailMessage[recipient=" + EmailDeliveryValuePolicy.maskRecipient(recipient)
                + ", fromAddress=" + EmailDeliveryValuePolicy.maskRecipient(fromAddress)
                + ", fromNamePresent=" + (fromName != null)
                + ", replyToPresent=" + (replyTo != null)
                + ", subjectPresent=true"
                + ", plainTextBodyLength=" + plainTextBody.length()
                + ", htmlBodyLength=" + htmlBody.length()
                + ", attachment=" + attachment
                + ']';
    }

    private static String requiredBody(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException(field + " must not exceed " + MAX_BODY_LENGTH + " characters.");
        }
        return normalized;
    }
}
