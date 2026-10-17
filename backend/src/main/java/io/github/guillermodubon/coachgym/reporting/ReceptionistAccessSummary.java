package io.github.guillermodubon.coachgym.reporting;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** Only allowed and denied operational-day counts are exposed to receptionists. */
public record ReceptionistAccessSummary(
        LocalDate day,
        ZoneId timezone,
        long allowedAttempts,
        long deniedAttempts) {

    public ReceptionistAccessSummary {
        Objects.requireNonNull(day, "Access reporting day is required.");
        Objects.requireNonNull(timezone, "Access reporting timezone is required.");
        if (allowedAttempts < 0 || deniedAttempts < 0) {
            throw new IllegalArgumentException(
                    "Receptionist access counts must be non-negative.");
        }
    }
}
