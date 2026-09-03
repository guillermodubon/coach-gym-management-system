package io.github.guillermodubon.coachgym.equipment.application;

import io.github.guillermodubon.coachgym.equipment.*;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentStateConflictException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentVersionConflictException;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatusPolicy;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatusTransition;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentValidationException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements the public, incident-specific equipment boundary. */
@Service
public class EquipmentIncidentApplicationService
        implements EquipmentIncidentOperations {

    private final EquipmentStore equipmentStore;
    private final ApplicationEventPublisher eventPublisher;

    public EquipmentIncidentApplicationService(
            EquipmentStore equipmentStore,
            ApplicationEventPublisher eventPublisher) {
        this.equipmentStore = equipmentStore;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EquipmentDetails takeOutOfServiceForIncident(
            UUID equipmentId,
            String incidentReference,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {

        Objects.requireNonNull(equipmentId, "Equipment id is required.");
        Objects.requireNonNull(actor, "Authenticated actor is required.");
        Objects.requireNonNull(actor.id(), "Authenticated actor id is required.");
        requireText(actor.username(), "Authenticated actor username is required.");
        Objects.requireNonNull(occurredAt, "Occurrence timestamp is required.");

        if (expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "Equipment version cannot be negative.");
        }

        String normalizedReference = requireText(
                incidentReference,
                "Incident reference is required.");

        EquipmentDetails current = equipmentStore.findById(equipmentId)
                .orElseThrow(
                        () -> new EquipmentIncidentNotFoundException(
                                equipmentId));

        if (current.status() == EquipmentStatus.RETIRED) {
            throw new EquipmentIncidentStateConflictException(
                    equipmentId,
                    "Retired equipment cannot accept new incidents.");
        }

        if (current.status() == EquipmentStatus.OUT_OF_SERVICE
                || current.status() == EquipmentStatus.MAINTENANCE) {
            return current;
        }

        io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus currentDomain =
                io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus.valueOf(
                        current.status().name());
        io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus targetDomain =
                io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus.OUT_OF_SERVICE;

        String reason = "Incident " + normalizedReference
                + " requires equipment withdrawal from service.";

        EquipmentStatusTransition transition;
        try {
            transition = EquipmentStatusPolicy.validate(
                    currentDomain,
                    targetDomain,
                    reason);
        } catch (EquipmentValidationException exception) {
            throw new EquipmentIncidentStateConflictException(
                    equipmentId,
                    exception.getMessage());
        }

        EquipmentDetails updated;

        try {
            updated = equipmentStore.applyTransition(
                    equipmentId,
                    transition,
                    actor,
                    expectedVersion,
                    occurredAt);
        } catch (EquipmentVersionConflictException exception) {
            throw new EquipmentIncidentVersionConflictException(
                    equipmentId);
        }

        eventPublisher.publishEvent(new EquipmentStatusChangedEvent(
                updated.id(),
                updated.equipmentCode(),
                current.status(),
                updated.status(),
                transition.reason(),
                actor.id(),
                actor.username(),
                occurredAt));

        return updated;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
