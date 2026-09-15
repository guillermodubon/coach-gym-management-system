package io.github.guillermodubon.coachgym.equipment;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.UUID;

/** Public equipment boundary used by maintenance work-order coordination. */
public interface EquipmentMaintenanceOperations {

    EquipmentDetails startMaintenance(
            UUID equipmentId,
            String maintenanceReference,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt);

    EquipmentDetails completeMaintenance(
            UUID equipmentId,
            String maintenanceReference,
            EquipmentStatus resultingStatus,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt);

    EquipmentDetails cancelInProgressMaintenance(
            UUID equipmentId,
            String maintenanceReference,
            EquipmentStatus resultingStatus,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt);
}
