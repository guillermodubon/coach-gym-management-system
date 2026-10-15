package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.MaintenanceDashboardDetails;

public record MaintenanceDashboardResponse(long scheduled, long inProgress, long overdue) {
    static MaintenanceDashboardResponse from(MaintenanceDashboardDetails details) {
        return new MaintenanceDashboardResponse(
                details.scheduled(), details.inProgress(), details.overdue());
    }
}
