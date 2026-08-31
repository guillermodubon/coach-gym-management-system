package io.github.guillermodubon.coachgym.equipment.application;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentDefinition;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatusTransition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for equipment items.
 *
 * <p>Implementations live in the {@code infrastructure.persistence} package and must
 * not be imported by controllers or domain objects.
 *
 * <p>The {@code occurredAt} parameter on mutating methods receives the
 * application-layer {@link java.time.Clock}-derived timestamp; persistence
 * implementations must not call {@code Instant.now()} independently.
 */
public interface EquipmentStore {

    /**
     * Persists a new piece of equipment with status {@code AVAILABLE} and returns
     * the resulting details. {@code equipmentNumber} and {@code equipmentCode} are
     * populated by the DB and must be read from the returned record.
     *
     * @param id         the application-generated UUID
     * @param definition the validated equipment definition
     * @param actor      the authenticated user performing the registration
     * @param occurredAt the application-layer timestamp for audit
     * @return the persisted equipment details including DB-generated code and timestamps
     */
    EquipmentDetails register(
            UUID id,
            EquipmentDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt);

    /**
     * Updates the allowlisted mutable fields of an existing equipment item using
     * optimistic locking.
     *
     * @param equipmentId the equipment UUID
     * @param definition  the validated new values
     * @param actor       the authenticated user performing the update
     * @param version     the caller's expected version; throws
     *                    {@link io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentVersionConflictException}
     *                    on mismatch
     * @param occurredAt  the application-layer timestamp for audit
     * @return the updated equipment details
     */
    EquipmentDetails update(
            UUID equipmentId,
            EquipmentDefinition definition,
            AuthenticatedActor actor,
            long version,
            Instant occurredAt);

    /**
     * Applies a validated status transition and inserts an append-only history row
     * atomically. For {@code RETIRED} transitions, also sets the retirement columns
     * ({@code retired_at}, {@code retired_by_user_id}, {@code retirement_reason}).
     *
     * @param equipmentId the equipment UUID
     * @param transition  the validated transition value object from the domain policy
     * @param actor       the authenticated user performing the transition
     * @param version     the caller's expected version for optimistic locking
     * @param occurredAt  the application-layer timestamp recorded in history
     * @return the updated equipment details
     */
    EquipmentDetails applyTransition(
            UUID equipmentId,
            EquipmentStatusTransition transition,
            AuthenticatedActor actor,
            long version,
            Instant occurredAt);

    /**
     * Returns the equipment details for the given ID, or empty if not found.
     */
    Optional<EquipmentDetails> findById(UUID equipmentId);

    /**
     * Returns a paginated list of equipment matching the query.
     */
    EquipmentPage findAll(EquipmentSearchQuery query);

    /**
     * Returns {@code true} if any equipment (other than {@code excludeId}) has the
     * given serial number (case-insensitive). Use {@code excludeId = null} when
     * checking for a new registration.
     */
    boolean existsBySerialNumberIgnoreCase(String serialNumber, UUID excludeId);
}
