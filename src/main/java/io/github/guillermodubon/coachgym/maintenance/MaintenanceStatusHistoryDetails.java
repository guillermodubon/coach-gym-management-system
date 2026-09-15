package io.github.guillermodubon.coachgym.maintenance;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only public projection of an append-only work-order status transition.
 *
 * @param id history entry identifier
 * @param maintenanceId maintenance work-order identifier
 * @param previousStatus prior status, null for the initial SCHEDULED entry
 * @param newStatus resulting status
 * @param reason business reason recorded for the transition
 * @param occurredAt transition timestamp
 * @param changedByUserId actor identifier, nullable only when retained history
 *                        outlives a deleted user reference
 */
public record MaintenanceStatusHistoryDetails(
        UUID id,
        UUID maintenanceId,
        MaintenanceStatus previousStatus,
        MaintenanceStatus newStatus,
        String reason,
        Instant occurredAt,
        UUID changedByUserId) {
}
