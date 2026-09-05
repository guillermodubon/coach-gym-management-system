package io.github.guillermodubon.coachgym.maintenance.domain;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;

/** Central policy for all allowed maintenance work-order transitions. */
public final class MaintenanceStatusPolicy {

    public MaintenanceStatusTransition start(
            MaintenanceStatus currentStatus,
            String reason) {
        return transition(currentStatus, MaintenanceStatus.IN_PROGRESS, reason);
    }

    public MaintenanceStatusTransition complete(
            MaintenanceStatus currentStatus,
            String reason) {
        return transition(currentStatus, MaintenanceStatus.COMPLETED, reason);
    }

    public MaintenanceStatusTransition cancel(
            MaintenanceStatus currentStatus,
            String reason) {
        return transition(currentStatus, MaintenanceStatus.CANCELLED, reason);
    }

    public MaintenanceStatusTransition transition(
            MaintenanceStatus currentStatus,
            MaintenanceStatus targetStatus,
            String reason) {
        if (currentStatus == null) {
            throw new MaintenanceValidationException("Current status is required.");
        }
        if (targetStatus == null) {
            throw new MaintenanceValidationException("Target status is required.");
        }
        if (!isAllowed(currentStatus, targetStatus)) {
            throw new MaintenanceValidationException(
                    "Maintenance transition from " + currentStatus
                            + " to " + targetStatus + " is not allowed.");
        }
        return new MaintenanceStatusTransition(currentStatus, targetStatus, reason);
    }

    public boolean isAllowed(
            MaintenanceStatus currentStatus,
            MaintenanceStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        return switch (currentStatus) {
            case SCHEDULED -> targetStatus == MaintenanceStatus.IN_PROGRESS
                    || targetStatus == MaintenanceStatus.CANCELLED;
            case IN_PROGRESS -> targetStatus == MaintenanceStatus.COMPLETED
                    || targetStatus == MaintenanceStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
