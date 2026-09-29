package io.github.guillermodubon.coachgym.access;

import java.time.LocalDate;
import java.util.Objects;

/** One bounded local-calendar day of physical access activity. */
public record AccessDailyTrendPoint(
        LocalDate day,
        long totalAttempts,
        long allowedAttempts,
        long deniedAttempts,
        long manualAttempts,
        long qrAttempts,
        long unknownSourceAttempts) {

    public AccessDailyTrendPoint {
        Objects.requireNonNull(day, "Access trend day is required.");
        if (totalAttempts < 0 || allowedAttempts < 0 || deniedAttempts < 0
                || manualAttempts < 0 || qrAttempts < 0 || unknownSourceAttempts < 0
                || allowedAttempts + deniedAttempts != totalAttempts
                || manualAttempts + qrAttempts + unknownSourceAttempts != totalAttempts) {
            throw new IllegalArgumentException("Access daily metrics must be non-negative and reconcile.");
        }
    }
}
