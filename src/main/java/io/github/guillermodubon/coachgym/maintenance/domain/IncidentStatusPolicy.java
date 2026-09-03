package io.github.guillermodubon.coachgym.maintenance.domain;

import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import java.util.Objects;

/** Authoritative incident lifecycle policy: OPEN -> IN_PROGRESS -> RESOLVED. */
public final class IncidentStatusPolicy {

    public IncidentStatusTransition validate(IncidentStatus currentStatus,
            IncidentStatus targetStatus, String reason) {
        Objects.requireNonNull(currentStatus, "Current incident status is required.");
        Objects.requireNonNull(targetStatus, "Target incident status is required.");
        IncidentStatusTransition transition = new IncidentStatusTransition(currentStatus, targetStatus, reason);
        if (currentStatus == IncidentStatus.OPEN && targetStatus == IncidentStatus.IN_PROGRESS) return transition;
        if (currentStatus == IncidentStatus.IN_PROGRESS && targetStatus == IncidentStatus.RESOLVED) return transition;
        if (currentStatus == IncidentStatus.RESOLVED) throw new IncidentValidationException("A resolved incident is terminal.");
        throw new IncidentValidationException("Incident transition from " + currentStatus + " to " + targetStatus + " is not allowed.");
    }

    public IncidentStatusTransition startInvestigation(IncidentStatus currentStatus, String reason) {
        return validate(currentStatus, IncidentStatus.IN_PROGRESS, reason);
    }

    public IncidentStatusTransition resolve(IncidentStatus currentStatus, String reason) {
        return validate(currentStatus, IncidentStatus.RESOLVED, reason);
    }
}
