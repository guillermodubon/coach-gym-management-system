package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.OperationalDashboardDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record OperationalDashboardResponse(
        Instant generatedAt,
        DashboardPeriodResponse period,
        MembershipDashboardResponse memberships,
        AccessDashboardResponse access,
        @Schema(nullable = true) PaymentDashboardResponse payments,
        @Schema(nullable = true) EquipmentDashboardResponse equipment,
        @Schema(nullable = true) IncidentDashboardResponse incidents,
        @Schema(nullable = true) MaintenanceDashboardResponse maintenance,
        DashboardNotificationResponse notifications) {

    static OperationalDashboardResponse from(OperationalDashboardDetails details) {
        return new OperationalDashboardResponse(
                details.generatedAt(),
                DashboardPeriodResponse.from(details.period()),
                MembershipDashboardResponse.from(details.memberships()),
                AccessDashboardResponse.from(details.access()),
                details.payments() == null ? null : PaymentDashboardResponse.from(details.payments()),
                details.equipment() == null ? null : EquipmentDashboardResponse.from(details.equipment()),
                details.incidents() == null ? null : IncidentDashboardResponse.from(details.incidents()),
                details.maintenance() == null ? null : MaintenanceDashboardResponse.from(details.maintenance()),
                DashboardNotificationResponse.from(details.notifications()));
    }
}
