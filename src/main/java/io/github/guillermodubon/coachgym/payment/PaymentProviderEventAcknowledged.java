package io.github.guillermodubon.coachgym.payment;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Privacy-safe notification that a provider event was acknowledged as a
 * duplicate. Provider event references and payloads are intentionally absent.
 */
public record PaymentProviderEventAcknowledged(
        UUID paymentAttemptId,
        PaymentProvider provider,
        String eventType,
        String processingResult,
        boolean duplicate,
        Instant occurredAt) {

    public PaymentProviderEventAcknowledged {
        if (paymentAttemptId == null || provider == null || occurredAt == null) {
            throw new IllegalArgumentException(
                    "Provider event acknowledgement identity is required.");
        }
        eventType = requiredCode(eventType, "Provider event type");
        processingResult = requiredCode(processingResult, "Provider event result");
        if (!duplicate) {
            throw new IllegalArgumentException(
                    "Only duplicate provider events may be acknowledged.");
        }
    }

    private static String requiredCode(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z_]{2,40}")) {
            throw new IllegalArgumentException(label + " must be a safe code.");
        }
        return normalized;
    }
}
