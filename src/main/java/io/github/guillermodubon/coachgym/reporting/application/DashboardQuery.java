package io.github.guillermodubon.coachgym.reporting.application;

import java.time.LocalDate;

/** Optional date inputs supplied when requesting the operational dashboard. */
public record DashboardQuery(LocalDate from, LocalDate until) {

    public static DashboardQuery defaults() {
        return new DashboardQuery(null, null);
    }
}
