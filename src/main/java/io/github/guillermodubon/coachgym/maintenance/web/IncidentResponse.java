package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.IncidentDetails;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        long incidentNumber,
        String incidentCode,
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        IncidentStatus status,
        IncidentPriority priority,
        String description,
        Instant reportedAt,
        UUID reportedByUserId,
        UUID assignedToUserId,
        Instant resolvedAt,
        UUID resolvedByUserId,
        String resolutionNotes,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static IncidentResponse from(IncidentDetails details) {
        return new IncidentResponse(
                details.id(), details.incidentNumber(), details.incidentCode(),
                details.equipmentId(), details.equipmentCode(), details.equipmentName(),
                details.status(), details.priority(), details.description(),
                details.reportedAt(), details.reportedByUserId(), details.assignedToUserId(),
                details.resolvedAt(), details.resolvedByUserId(), details.resolutionNotes(),
                details.createdAt(), details.updatedAt(), details.version());
    }
}
