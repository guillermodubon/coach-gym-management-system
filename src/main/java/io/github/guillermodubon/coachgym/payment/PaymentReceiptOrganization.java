package io.github.guillermodubon.coachgym.payment;

import java.time.ZoneId;

/** Approved organization identity and contact data used on a receipt. */
public record PaymentReceiptOrganization(
        String displayName,
        String legalName,
        String email,
        String phone,
        String address,
        String timeZone) {

    public PaymentReceiptOrganization {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Organization display name is required.");
        }
        if (timeZone == null || timeZone.isBlank()) {
            throw new IllegalArgumentException("Organization time zone is required.");
        }
        try {
            ZoneId.of(timeZone.strip());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Organization time zone is invalid.", exception);
        }
        displayName = normalizeRequired(displayName);
        legalName = normalizeOptional(legalName);
        email = normalizeOptional(email);
        phone = normalizeOptional(phone);
        address = normalizeOptional(address);
        timeZone = timeZone.strip();
    }

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    private static String normalizeRequired(String value) {
        return value.strip();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
