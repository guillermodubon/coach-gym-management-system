package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatusHistoryDetails;
import java.time.Instant;
import java.util.UUID;

public record IncidentStatusHistoryResponse(
        UUID id,
        UUID incidentId,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String reason,
        Instant occurredAt,
        UUID changedByUserId) {

    static IncidentStatusHistoryResponse from(
            IncidentStatusHistoryDetails details) {
        return new IncidentStatusHistoryResponse(
                details.id(), details.incidentId(),
                details.previousStatus(), details.newStatus(),
                details.reason(), details.occurredAt(),
                details.changedByUserId());
    }
}
