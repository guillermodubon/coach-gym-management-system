package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.IncidentDashboardDetails;

public record IncidentDashboardResponse(long open, long inProgress, long criticalOpen) {
    static IncidentDashboardResponse from(IncidentDashboardDetails details) {
        return new IncidentDashboardResponse(
                details.open(), details.inProgress(), details.criticalOpen());
    }
}
