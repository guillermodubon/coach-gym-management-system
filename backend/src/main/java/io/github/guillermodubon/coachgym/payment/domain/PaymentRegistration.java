package io.github.guillermodubon.coachgym.payment.domain;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Validated, normalized payment registration value object.
 *
 * <p>Enforces field-level invariants only. Cross-object rules
 * (membership ownership, amount match, currency match, future paidAt)
 * are the responsibility of {@link PaymentRegistrationPolicy}.</p>
 *
 * <p>Security note: externalReference must never contain full card
 * numbers, CVV values, passwords, tokens, or sensitive banking data.
 * This is a documentation constraint; enforcement is the caller's
 * responsibility.</p>
 */
public record PaymentRegistration(
        UUID clientId,
        UUID membershipId,
        UUID membershipPeriodId,
        BigDecimal amount,
        String currency,
        PaymentMethod paymentMethod,
        String externalReference,
        Instant paidAt) {

    private static final int MONEY_SCALE = 2;
    private static final int MAX_EXTERNAL_REFERENCE_LENGTH = 128;

    public PaymentRegistration {

        if (clientId == null) {
            throw new PaymentValidationException(
                    "Payment client identifier must be provided.");
        }

        if (membershipId == null) {
            throw new PaymentValidationException(
                    "Payment membership identifier must be provided.");
        }

        if (membershipPeriodId == null) {
            throw new PaymentValidationException(
                    "Payment membership period identifier must be provided.");
        }

        amount = normalizeAmount(amount);

        currency = normalizeCurrency(currency);

        if (paymentMethod == null) {
            throw new PaymentValidationException(
                    "Payment method must be provided.");
        }

        externalReference =
                normalizeExternalReference(externalReference);

        if (paidAt == null) {
            throw new PaymentValidationException(
                    "Payment paid-at timestamp must be provided.");
        }
    }

    private static BigDecimal normalizeAmount(
            BigDecimal value) {

        if (value == null) {
            throw new PaymentValidationException(
                    "Payment amount must be provided.");
        }

        if (value.signum() <= 0) {
            throw new PaymentValidationException(
                    "Payment amount must be greater than zero.");
        }

        // Reject values with more than 2 significant decimal places.
        // stripTrailingZeros().scale() gives the minimal scale after
        // removing insignificant zeros; any value > MONEY_SCALE has
        // genuine sub-cent precision that must be rejected.
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() > MONEY_SCALE) {
            throw new PaymentValidationException(
                    "Payment amount must not exceed two decimal places.");
        }

        return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static String normalizeCurrency(
            String value) {

        if (value == null || value.isBlank()) {
            throw new PaymentValidationException(
                    "Payment currency must be provided.");
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);

        if (!normalized.matches("[A-Z]{3}")) {
            throw new PaymentValidationException(
                    "Payment currency must be a three-letter ISO code.");
        }

        return normalized;
    }

    private static String normalizeExternalReference(
            String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.length() > MAX_EXTERNAL_REFERENCE_LENGTH) {
            throw new PaymentValidationException(
                    "Payment external reference must not exceed "
                            + MAX_EXTERNAL_REFERENCE_LENGTH
                            + " characters.");
        }

        return trimmed;
    }
}
