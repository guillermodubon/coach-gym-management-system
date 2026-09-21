package io.github.guillermodubon.coachgym.organization.application;

import io.github.guillermodubon.coachgym.organization.ChangeOrganizationStatusCommand;
import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import io.github.guillermodubon.coachgym.organization.OrganizationSummary;
import io.github.guillermodubon.coachgym.organization.OrganizationUpdated;
import io.github.guillermodubon.coachgym.organization.OrganizationValidationException;
import io.github.guillermodubon.coachgym.organization.UpdateOrganizationCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application use cases for the canonical organization. */
@Service
public class OrganizationApplicationService {

    private final OrganizationQuery organizationQuery;
    private final OrganizationStore organizationStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public OrganizationApplicationService(
            OrganizationQuery organizationQuery,
            OrganizationStore organizationStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.organizationQuery = Objects.requireNonNull(organizationQuery);
        this.organizationStore = Objects.requireNonNull(organizationStore);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Full administrative organization projection. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public OrganizationDetails findCanonical() {
        return requireCurrent();
    }

    /** Safe organization summary available to both supported staff roles. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public OrganizationSummary findCanonicalSummary() {
        return OrganizationSummary.from(requireCurrent());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public OrganizationDetails update(
            UpdateOrganizationCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Organization update command is required.");
        requireActor(actor);
        OrganizationDetails current = requireCurrent();
        requireExpectedVersion(current.version(), command.expectedVersion());
        Set<String> changedFields = changedFields(current, command);
        if (changedFields.isEmpty()) {
            return current;
        }

        Instant occurredAt = clock.instant();
        OrganizationDetails updated = organizationStore.update(command, occurredAt);
        publishUpdate(updated, changedFields, actor, occurredAt);
        return updated;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public OrganizationDetails changeStatus(
            ChangeOrganizationStatusCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Organization status command is required.");
        requireActor(actor);
        OrganizationDetails current = requireCurrent();
        requireExpectedVersion(current.version(), command.expectedVersion());

        Instant occurredAt = clock.instant();
        OrganizationDetails updated = organizationStore.changeStatus(
                command.requestedStatus(), command.expectedVersion(), occurredAt);
        publishUpdate(updated, Set.of("status"), actor, occurredAt);
        return updated;
    }

    private OrganizationDetails requireCurrent() {
        return organizationQuery.findCanonical()
                .orElseThrow(OrganizationNotFoundException::new);
    }

    private void publishUpdate(
            OrganizationDetails updated,
            Set<String> changedFields,
            AuthenticatedActor actor,
            Instant occurredAt) {
        eventPublisher.publishEvent(new OrganizationUpdated(
                updated.id(),
                updated.code(),
                changedFields,
                actor.id(),
                actor.username(),
                occurredAt));
    }

    private static Set<String> changedFields(
            OrganizationDetails current,
            UpdateOrganizationCommand command) {
        Set<String> changed = new LinkedHashSet<>();
        if (!Objects.equals(current.legalName(), command.legalName())) {
            changed.add("legalName");
        }
        if (!Objects.equals(current.brandName(), command.brandName())) {
            changed.add("brandName");
        }
        if (!Objects.equals(current.supportEmail(), command.supportEmail())) {
            changed.add("supportEmail");
        }
        if (!Objects.equals(current.supportPhone(), command.supportPhone())) {
            changed.add("supportPhone");
        }
        if (!Objects.equals(current.defaultTimezone(), command.defaultTimezone())) {
            changed.add("defaultTimezone");
        }
        if (!Objects.equals(current.defaultCurrency(), command.defaultCurrency())) {
            changed.add("defaultCurrency");
        }
        return Set.copyOf(changed);
    }

    private static void requireExpectedVersion(long current, long expected) {
        if (current != expected) {
            throw new OrganizationVersionConflictException();
        }
    }

    private static void requireActor(
            AuthenticatedActor actor) {
        if (actor == null || actor.id() == null
                || actor.username() == null || actor.username().isBlank()) {
            throw new OrganizationValidationException("Authenticated actor is required.");
        }
    }
}
