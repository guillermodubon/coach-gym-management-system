package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import java.util.UUID;

/** Bounded query for attempts belonging to one membership period. */
public record PaymentAttemptSearchQuery(
        UUID membershipId,
        UUID membershipPeriodId,
        UUID clientId,
        PaymentAttemptStatus status,
        int page,
        int size) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public PaymentAttemptSearchQuery {
        if (membershipId == null || membershipPeriodId == null) {
            throw new PaymentAttemptQueryValidationException(
                    "Membership and membership-period filters are required.");
        }
        if (page < 0) {
            throw new PaymentAttemptQueryValidationException(
                    "Payment-attempt page must not be negative.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new PaymentAttemptQueryValidationException(
                    "Payment-attempt page size must be between 1 and 100.");
        }
    }

    public long offset() {
        return (long) page * size;
    }
}
