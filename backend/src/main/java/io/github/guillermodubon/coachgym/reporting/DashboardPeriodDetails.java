package io.github.guillermodubon.coachgym.reporting;

import java.time.LocalDate;

/** Inclusive date period represented by the operational dashboard. */
public record DashboardPeriodDetails(LocalDate from, LocalDate until) {

    public DashboardPeriodDetails {
        if (from == null) {
            throw new IllegalArgumentException("Dashboard period start date is required.");
        }
        if (until == null) {
            throw new IllegalArgumentException("Dashboard period end date is required.");
        }
        if (from.isAfter(until)) {
            throw new IllegalArgumentException(
                    "Dashboard period start date must not be after end date.");
        }
    }
}
