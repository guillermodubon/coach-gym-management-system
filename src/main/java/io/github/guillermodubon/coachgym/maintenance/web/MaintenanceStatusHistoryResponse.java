package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatusHistoryDetails;
import java.time.Instant;
import java.util.UUID;

public record MaintenanceStatusHistoryResponse(
        UUID id,
        UUID maintenanceId,
        MaintenanceStatus previousStatus,
        MaintenanceStatus newStatus,
        String reason,
        Instant occurredAt,
        UUID changedByUserId) {

    static MaintenanceStatusHistoryResponse from(
            MaintenanceStatusHistoryDetails details) {
        return new MaintenanceStatusHistoryResponse(
                details.id(), details.maintenanceId(), details.previousStatus(),
                details.newStatus(), details.reason(), details.occurredAt(),
                details.changedByUserId());
    }
}
