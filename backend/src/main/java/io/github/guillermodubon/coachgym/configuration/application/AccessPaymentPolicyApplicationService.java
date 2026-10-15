package io.github.guillermodubon.coachgym.configuration.application;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicy;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyActor;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyAuthorization;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyChanged;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyDetails;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyQuery;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrative application workflow for the global access-payment policy.
 *
 * <p>Only the persisted settings projection is authoritative. Actor identity
 * and timestamps are supplied by the server-side workflow, while the expected
 * version prevents lost administrative updates.</p>
 */
@Service
public class AccessPaymentPolicyApplicationService {

    private final AccessPaymentPolicyQuery policyQuery;
    private final AccessPaymentPolicyStore policyStore;
    private final AccessPaymentPolicyAuthorization authorization;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AccessPaymentPolicyApplicationService(
            AccessPaymentPolicyQuery policyQuery,
            AccessPaymentPolicyStore policyStore,
            AccessPaymentPolicyAuthorization authorization,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.policyQuery = Objects.requireNonNull(policyQuery);
        this.policyStore = Objects.requireNonNull(policyStore);
        this.authorization = Objects.requireNonNull(authorization);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Returns the current persisted policy for an authorized administrator. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AccessPaymentPolicyDetails findCurrent(AccessPaymentPolicyActor actor) {
        authorization.requireOrganizationAdministrator(actor);
        return requireCurrent();
    }

    /**
     * Applies an optimistic, idempotent policy update for an authorized
     * administrator.
     *
     * <p>A no-op returns the current projection without writing, incrementing
     * the version, or publishing an event. The expected version is checked
     * before that decision so stale callers cannot silently succeed.</p>
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AccessPaymentPolicyDetails update(
            UpdateAccessPaymentPolicyCommand command,
            AccessPaymentPolicyActor actor) {
        Objects.requireNonNull(command, "Access payment policy command is required.");
        authorization.requireOrganizationAdministrator(actor);

        AccessPaymentPolicyDetails current = requireCurrent();
        if (current.version() != command.expectedVersion()) {
            throw new AccessPaymentPolicyVersionConflictException();
        }

        AccessPaymentPolicy requested = new AccessPaymentPolicy(
                command.requireConfirmedPaymentForAccess());
        if (requested.equals(current.policy())) {
            return current;
        }

        Instant occurredAt = clock.instant();
        AccessPaymentPolicyDetails updated = policyStore.update(
                requested,
                command.expectedVersion(),
                actor.userId(),
                occurredAt);
        if (updated == null) {
            throw new AccessPaymentPolicyDataAccessException(
                    "Access payment policy update returned no persisted value.", null);
        }

        eventPublisher.publishEvent(new AccessPaymentPolicyChanged(
                current.requireConfirmedPaymentForAccess(),
                updated.requireConfirmedPaymentForAccess(),
                actor.userId(),
                actor.identifier(),
                occurredAt));

        return updated;
    }

    private AccessPaymentPolicyDetails requireCurrent() {
        AccessPaymentPolicyDetails current = policyQuery.findCurrent();
        if (current == null) {
            throw new AccessPaymentPolicyDataAccessException(
                    "Access payment policy could not be read.", null);
        }
        return current;
    }

}
