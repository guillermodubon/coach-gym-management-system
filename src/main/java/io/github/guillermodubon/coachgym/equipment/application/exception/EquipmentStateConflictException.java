package io.github.guillermodubon.coachgym.equipment.application.exception;

import io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus;

/**
 * Thrown when an equipment status transition is rejected by the domain policy
 * (e.g. same-status, RETIRED terminal, MAINTENANCE reserved, non-catalog path).
 * Maps to HTTP 409.
 */
public class EquipmentStateConflictException extends RuntimeException {

    private final EquipmentStatus currentStatus;
    private final EquipmentStatus requestedStatus;

    public EquipmentStateConflictException(EquipmentStatus currentStatus,
                                           EquipmentStatus requestedStatus,
                                           String message) {
        super(message);
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public EquipmentStatus getCurrentStatus() {
        return currentStatus;
    }

    public EquipmentStatus getRequestedStatus() {
        return requestedStatus;
    }
}
