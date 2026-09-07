package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.DashboardNotificationDetails;
import io.swagger.v3.oas.annotations.media.Schema;

public record DashboardNotificationResponse(
        @Schema(minimum = "0", example = "6") long unread) {
    static DashboardNotificationResponse from(DashboardNotificationDetails details) {
        return new DashboardNotificationResponse(details.unread());
    }
}
