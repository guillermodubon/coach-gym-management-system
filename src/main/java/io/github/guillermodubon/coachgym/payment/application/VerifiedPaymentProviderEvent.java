package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Internal, verified provider outcome. Raw requests and signatures are deliberately excluded.
 */
public record VerifiedPaymentProviderEvent(
        PaymentProvider provider,
        String providerEventReference,
        PaymentProviderEventType eventType,
        UUID paymentAttemptId,
        String checkoutReference,
        String providerPaymentReference,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {

    public VerifiedPaymentProviderEvent {
        if (provider == null || eventType == null) {
            throw new IllegalArgumentException("Provider and event type are required.");
        }
        providerEventReference = requiredText(providerEventReference, "Provider event reference");
        if (paymentAttemptId == null) {
            throw new IllegalArgumentException("Payment attempt id is required.");
        }
        if (eventType == PaymentProviderEventType.CHECKOUT_COMPLETED) {
            checkoutReference = requiredText(checkoutReference, "Checkout reference");
            providerPaymentReference = requiredText(
                    providerPaymentReference, "Provider payment reference");
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("Verified payment amount must be positive.");
            }
            currency = normalizeCurrency(currency);
        } else if (checkoutReference != null && checkoutReference.isBlank()) {
            checkoutReference = null;
        } else if (providerPaymentReference != null || amount != null || currency != null) {
            throw new IllegalArgumentException(
                    "Only a completed checkout may include payment confirmation data.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Provider event timestamp is required.");
        }
    }

    private static String requiredText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.strip();
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Verified payment currency is required.");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Verified payment currency must be a three-letter code.");
        }
        return normalized;
    }
}
