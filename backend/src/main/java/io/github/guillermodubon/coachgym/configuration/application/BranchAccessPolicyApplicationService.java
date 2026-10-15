package io.github.guillermodubon.coachgym.configuration.application;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyActor;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyAuthorization;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPaymentPolicyMode;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPolicyOverrideChanged;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPolicyQuery;
import io.github.guillermodubon.coachgym.configuration.EffectiveBranchAccessPolicy;
import io.github.guillermodubon.coachgym.configuration.UpdateBranchAccessPolicyCommand;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application use cases for effective and versioned branch access policies. */
@Service
public class BranchAccessPolicyApplicationService {

    private final BranchAccessPolicyQuery policyQuery;
    private final BranchAccessPolicyStore policyStore;
    private final AccessPaymentPolicyAuthorization authorization;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public BranchAccessPolicyApplicationService(
            BranchAccessPolicyQuery policyQuery,
            BranchAccessPolicyStore policyStore,
            AccessPaymentPolicyAuthorization authorization,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.policyQuery = Objects.requireNonNull(policyQuery);
        this.policyStore = Objects.requireNonNull(policyStore);
        this.authorization = Objects.requireNonNull(authorization);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Reads the effective value for authorized staff without exposing payment details. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EffectiveBranchAccessPolicy findEffective(
            UUID branchId,
            AccessPaymentPolicyActor actor) {
        UUID organizationId = authorization
                .requireOperationalStaffCanReadBranch(actor, branchId);
        return requirePolicy(organizationId, branchId);
    }

    /**
     * Updates an active branch override. Sending {@code INHERIT} clears any
     * override while preserving its row and incrementing its version.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EffectiveBranchAccessPolicy updateOverride(
            UpdateBranchAccessPolicyCommand command,
            AccessPaymentPolicyActor actor) {
        Objects.requireNonNull(command, "Branch access-policy command is required.");
        UUID organizationId = authorization
                .requireOrganizationAdministrator(actor);
        UUID branchId = command.branchId();

        EffectiveBranchAccessPolicy current = requirePolicy(
                organizationId, branchId);
        if (current.version() != command.expectedVersion()) {
            throw new BranchAccessPolicyVersionConflictException();
        }
        if (current.branchMode() == command.mode()) {
            return current;
        }

        Instant occurredAt = clock.instant();
        EffectiveBranchAccessPolicy updated = policyStore.update(
                organizationId,
                branchId,
                command.mode(),
                command.expectedVersion(),
                actor.userId(),
                occurredAt);
        if (updated == null) {
            throw new BranchAccessPolicyDataAccessException(
                    "Branch access policy update returned no persisted value.", null);
        }

        eventPublisher.publishEvent(new BranchAccessPolicyOverrideChanged(
                organizationId,
                branchId,
                current.branchMode(),
                updated.branchMode(),
                updated.version(),
                actor.userId(),
                actor.identifier(),
                occurredAt));
        return updated;
    }

    private EffectiveBranchAccessPolicy requirePolicy(UUID organizationId, UUID branchId) {
        EffectiveBranchAccessPolicy policy = policyQuery.findForBranch(
                organizationId, branchId);
        if (policy == null) {
            throw new BranchAccessPolicyDataAccessException(
                    "Branch access policy could not be read.", null);
        }
        return policy;
    }
}
