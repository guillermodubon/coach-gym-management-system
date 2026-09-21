package io.github.guillermodubon.coachgym.organization.application;

import io.github.guillermodubon.coachgym.organization.ChangeGymBranchStatusCommand;
import io.github.guillermodubon.coachgym.organization.CreateGymBranchCommand;
import io.github.guillermodubon.coachgym.organization.GymBranchCreated;
import io.github.guillermodubon.coachgym.organization.GymBranchDetails;
import io.github.guillermodubon.coachgym.organization.GymBranchStatusChanged;
import io.github.guillermodubon.coachgym.organization.GymBranchUpdated;
import io.github.guillermodubon.coachgym.organization.GymBranchValidationException;
import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import io.github.guillermodubon.coachgym.organization.UpdateGymBranchCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application use cases for canonical gym branches. */
@Service
public class GymBranchApplicationService {

    private final GymBranchQuery branchQuery;
    private final GymBranchStore branchStore;
    private final OrganizationQuery organizationQuery;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public GymBranchApplicationService(
            GymBranchQuery branchQuery,
            GymBranchStore branchStore,
            OrganizationQuery organizationQuery,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.branchQuery = Objects.requireNonNull(branchQuery);
        this.branchStore = Objects.requireNonNull(branchStore);
        this.organizationQuery = Objects.requireNonNull(organizationQuery);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public GymBranchPage findAll(GymBranchSearchQuery query) {
        return branchQuery.findAll(Objects.requireNonNull(
                query, "Branch search query is required."));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public GymBranchDetails findById(UUID id) {
        return branchQuery.findById(id)
                .orElseThrow(() -> new GymBranchNotFoundException(id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public GymBranchDetails create(
            CreateGymBranchCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Branch creation command is required.");
        requireActor(actor);
        OrganizationDetails organization = organizationQuery.findCanonical()
                .orElseThrow(OrganizationNotFoundException::new);
        CreateGymBranchCommand resolved = withDefaultTimezone(command, organization);
        Instant occurredAt = clock.instant();
        GymBranchDetails created = branchStore.create(resolved, occurredAt);
        eventPublisher.publishEvent(new GymBranchCreated(
                created.id(),
                created.organizationId(),
                created.code(),
                actor.id(),
                actor.username(),
                occurredAt));
        return created;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public GymBranchDetails update(
            UUID id,
            UpdateGymBranchCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Branch update command is required.");
        requireActor(actor);
        GymBranchDetails current = findRequired(id);
        requireExpectedVersion(current.version(), command.expectedVersion());
        Set<String> changedFields = changedFields(current, command);
        if (changedFields.isEmpty()) {
            return current;
        }

        Instant occurredAt = clock.instant();
        GymBranchDetails updated = branchStore.update(id, command, occurredAt);
        eventPublisher.publishEvent(new GymBranchUpdated(
                updated.id(),
                updated.organizationId(),
                updated.code(),
                changedFields,
                actor.id(),
                actor.username(),
                occurredAt));
        return updated;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public GymBranchDetails changeStatus(
            UUID id,
            ChangeGymBranchStatusCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Branch status command is required.");
        requireActor(actor);
        GymBranchDetails current = findRequired(id);
        requireExpectedVersion(current.version(), command.expectedVersion());

        Instant occurredAt = clock.instant();
        GymBranchDetails updated = branchStore.changeStatus(id, command, occurredAt);
        eventPublisher.publishEvent(new GymBranchStatusChanged(
                updated.id(),
                updated.organizationId(),
                updated.code(),
                current.status(),
                updated.status(),
                actor.id(),
                actor.username(),
                occurredAt));
        return updated;
    }

    private GymBranchDetails findRequired(UUID id) {
        return branchQuery.findById(id)
                .orElseThrow(() -> new GymBranchNotFoundException(id));
    }

    private static CreateGymBranchCommand withDefaultTimezone(
            CreateGymBranchCommand command,
            OrganizationDetails organization) {
        if (command.timezone() != null) {
            return command;
        }
        return new CreateGymBranchCommand(
                command.code(),
                command.name(),
                command.addressLine1(),
                command.addressLine2(),
                command.city(),
                command.stateOrDepartment(),
                command.postalCode(),
                command.countryCode(),
                command.phone(),
                command.email(),
                organization.defaultTimezone());
    }

    private static Set<String> changedFields(
            GymBranchDetails current,
            UpdateGymBranchCommand command) {
        Set<String> changed = new LinkedHashSet<>();
        if (!Objects.equals(current.name(), command.name())) {
            changed.add("name");
        }
        if (!Objects.equals(current.addressLine1(), command.addressLine1())) {
            changed.add("addressLine1");
        }
        if (!Objects.equals(current.addressLine2(), command.addressLine2())) {
            changed.add("addressLine2");
        }
        if (!Objects.equals(current.city(), command.city())) {
            changed.add("city");
        }
        if (!Objects.equals(current.stateOrDepartment(), command.stateOrDepartment())) {
            changed.add("stateOrDepartment");
        }
        if (!Objects.equals(current.postalCode(), command.postalCode())) {
            changed.add("postalCode");
        }
        if (!Objects.equals(current.countryCode(), command.countryCode())) {
            changed.add("countryCode");
        }
        if (!Objects.equals(current.phone(), command.phone())) {
            changed.add("phone");
        }
        if (!Objects.equals(current.email(), command.email())) {
            changed.add("email");
        }
        if (!Objects.equals(current.timezone(), command.timezone())) {
            changed.add("timezone");
        }
        return Set.copyOf(changed);
    }

    private static void requireExpectedVersion(long current, long expected) {
        if (current != expected) {
            throw new GymBranchVersionConflictException();
        }
    }

    private static void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null
                || actor.username() == null || actor.username().isBlank()) {
            throw new GymBranchValidationException("Authenticated actor is required.");
        }
    }
}
