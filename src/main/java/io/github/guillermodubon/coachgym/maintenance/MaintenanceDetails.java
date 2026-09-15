package io.github.guillermodubon.coachgym.maintenance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only public projection of a maintenance work order.
 *
 * <p>The projection contains the authoritative work-order fields stored in
 * {@code gym.maintenances} plus public equipment and incident snapshots used
 * by API consumers. It does not expose JPA entities or persistence internals.
 *
 * @param id maintenance identifier
 * @param maintenanceNumber database-generated maintenance number
 * @param maintenanceCode database-generated code in {@code MNT-######} format
 * @param equipmentId equipment identifier
 * @param equipmentCode current public equipment-code snapshot
 * @param equipmentName current public equipment-name snapshot
 * @param incidentId optionally linked corrective incident identifier
 * @param incidentCode optionally linked incident-code snapshot
 * @param maintenanceType preventive or corrective classification
 * @param status current work-order lifecycle status
 * @param scheduledOn scheduled service date
 * @param startedAt actual start timestamp, null until work starts
 * @param completedAt actual completion timestamp, null until completed
 * @param providerName optional external provider name
 * @param technicianName optional technician name
 * @param estimatedCost optional non-negative estimated cost
 * @param actualCost optional non-negative actual cost
 * @param currency ISO-style three-letter uppercase currency code
 * @param actionsTaken actions performed, populated when applicable
 * @param notes optional operational notes
 * @param createdByUserId user who scheduled the work order
 * @param assignedToUserId optional assigned staff user
 * @param completedByUserId user who completed the work order, otherwise null
 * @param createdAt creation timestamp
 * @param updatedAt latest update timestamp
 * @param version optimistic-lock version
 */
public record MaintenanceDetails(
        UUID id,
        long maintenanceNumber,
        String maintenanceCode,
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        UUID incidentId,
        String incidentCode,
        MaintenanceType maintenanceType,
        MaintenanceStatus status,
        LocalDate scheduledOn,
        Instant startedAt,
        Instant completedAt,
        String providerName,
        String technicianName,
        BigDecimal estimatedCost,
        BigDecimal actualCost,
        String currency,
        String actionsTaken,
        String notes,
        UUID createdByUserId,
        UUID assignedToUserId,
        UUID completedByUserId,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
