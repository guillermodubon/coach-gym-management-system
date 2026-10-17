package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.AccessDashboardDetails;

public record AccessDashboardResponse(long allowedToday, long deniedToday) {
    static AccessDashboardResponse from(AccessDashboardDetails details) {
        return new AccessDashboardResponse(details.allowedToday(), details.deniedToday());
    }
}
