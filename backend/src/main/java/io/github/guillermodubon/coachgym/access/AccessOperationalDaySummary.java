package io.github.guillermodubon.coachgym.access;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** Minimal access aggregate retained for the existing receptionist projection. */
public record AccessOperationalDaySummary(
        LocalDate day,
        ZoneId timezone,
        long totalAttempts,
        long allowedAttempts,
        long deniedAttempts) {

    public AccessOperationalDaySummary {
        Objects.requireNonNull(day, "Access operational day is required.");
        Objects.requireNonNull(timezone, "Access reporting timezone is required.");
        if (totalAttempts < 0 || allowedAttempts < 0 || deniedAttempts < 0
                || Math.addExact(allowedAttempts, deniedAttempts) != totalAttempts) {
            throw new IllegalArgumentException(
                    "Operational-day access counts must be non-negative and reconcile.");
        }
    }
}
