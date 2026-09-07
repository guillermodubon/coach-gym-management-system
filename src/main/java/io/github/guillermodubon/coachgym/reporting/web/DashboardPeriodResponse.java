package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.DashboardPeriodDetails;
import java.time.LocalDate;

public record DashboardPeriodResponse(LocalDate from, LocalDate until) {
    static DashboardPeriodResponse from(DashboardPeriodDetails details) {
        return new DashboardPeriodResponse(details.from(), details.until());
    }
}
