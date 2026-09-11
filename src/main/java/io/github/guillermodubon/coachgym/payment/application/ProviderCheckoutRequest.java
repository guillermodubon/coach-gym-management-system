package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;

/** Server-derived data needed to create a hosted provider checkout. */
public record ProviderCheckoutRequest(
        UUID paymentAttemptId,
        PaymentProvider provider,
        BigDecimal amount,
        String currency,
        URI successUrl,
        URI cancelUrl) {

    public ProviderCheckoutRequest {
        paymentAttemptId = PaymentAttemptCommandValidation.requiredIdentifier(
                paymentAttemptId, "Payment attempt id");
        if (provider == null) {
            throw new IllegalArgumentException("Payment provider is required.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Checkout amount must be positive.");
        }
        currency = normalizeCurrency(currency);
        successUrl = requiredAbsoluteUri(successUrl, "Checkout success URL");
        cancelUrl = requiredAbsoluteUri(cancelUrl, "Checkout cancel URL");
    }

    private static String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Checkout currency is required.");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Checkout currency must be a three-letter code.");
        }
        return normalized;
    }

    private static URI requiredAbsoluteUri(URI value, String label) {
        if (value == null || !value.isAbsolute()) {
            throw new IllegalArgumentException(label + " must be absolute.");
        }
        return value;
    }
}
