package io.github.guillermodubon.coachgym.equipment;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.UUID;

/**
 * Public equipment boundary for incident-driven operational actions.
 *
 * <p>This contract does not expose the normal equipment lifecycle API. It
 * supports only the constrained action required while reporting an incident.
 */
public interface EquipmentIncidentOperations {

    /**
     * Takes equipment out of service when an incident represents an operational
     * or safety risk.
     *
     * <p>AVAILABLE equipment transitions to OUT_OF_SERVICE. Equipment already
     * OUT_OF_SERVICE or MAINTENANCE remains unchanged. RETIRED equipment rejects
     * the operation.
     *
     * @param equipmentId equipment identifier
     * @param incidentReference temporary or persistent incident reference used in history
     * @param expectedVersion current optimistic-lock version
     * @param actor authenticated staff actor
     * @param occurredAt shared application timestamp
     * @return the current or updated equipment projection
     */
    EquipmentDetails takeOutOfServiceForIncident(
            UUID equipmentId,
            String incidentReference,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt);
}
