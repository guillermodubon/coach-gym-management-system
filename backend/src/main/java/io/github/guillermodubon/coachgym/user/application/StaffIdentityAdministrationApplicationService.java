package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.shared.security.CurrentPasswordVerifier;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.ChangeStaffIdentityStatusCommand;
import io.github.guillermodubon.coachgym.user.ChangeStaffRoleScopeCommand;
import io.github.guillermodubon.coachgym.user.LastOrganizationAdministratorPolicy;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffIdentityAssignmentsClosed;
import io.github.guillermodubon.coachgym.user.StaffIdentityAuthorizationException;
import io.github.guillermodubon.coachgym.user.StaffIdentityLifecycleChanged;
import io.github.guillermodubon.coachgym.user.StaffIdentityLifecyclePolicy;
import io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffIdentityStatus;
import io.github.guillermodubon.coachgym.user.StaffIdentityValidationException;
import io.github.guillermodubon.coachgym.user.StaffRoleScopeChanged;
import io.github.guillermodubon.coachgym.user.StaffScopeAuthorizationPolicy;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional, organization-admin-only staff lifecycle and authority use cases. */
@Service
public class StaffIdentityAdministrationApplicationService {

    private final StaffScopeQuery scopeQuery;
    private final StaffAssignmentAuthorizationQuery authorizationQuery;
    private final StaffBranchAssignmentStore assignmentStore;
    private final StaffIdentityAdministrationStore identityStore;
    private final CurrentPasswordVerifier passwordVerifier;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public StaffIdentityAdministrationApplicationService(
            StaffScopeQuery scopeQuery,
            StaffAssignmentAuthorizationQuery authorizationQuery,
            StaffBranchAssignmentStore assignmentStore,
            StaffIdentityAdministrationStore identityStore,
            CurrentPasswordVerifier passwordVerifier,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.scopeQuery = Objects.requireNonNull(scopeQuery);
        this.authorizationQuery = Objects.requireNonNull(authorizationQuery);
        this.assignmentStore = Objects.requireNonNull(assignmentStore);
        this.identityStore = Objects.requireNonNull(identityStore);
        this.passwordVerifier = Objects.requireNonNull(passwordVerifier);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Suspends or terminally deactivates another account; suspension retains assignments. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public StaffIdentityAdministrationState changeStatus(
            ChangeStaffIdentityStatusCommand command,
            AuthenticatedActor actor,
            String currentPassword) {
        Objects.requireNonNull(command, "Staff identity status command is required.");
        requireActor(actor);
        requireOrganizationAdministrator(actor);
        StaffIdentityLifecyclePolicy.requireDifferentActorAndTarget(actor.id(), command.targetUserId());
        lockLifecycle(command.targetUserId(), actor);

        StaffIdentityAdministrationState current = find(command.targetUserId());
        requireExpectedVersion(current, command.expectedVersion());
        StaffIdentityStatus currentStatus = identityStatus(current.accountStatus());
        StaffIdentityLifecyclePolicy.requireTransition(currentStatus, command.requestedStatus());

        boolean targetIsOrganizationAdministrator = isOrganizationAdministrator(current);
        boolean removesAdministrator = targetIsOrganizationAdministrator
                && command.requestedStatus() != StaffIdentityStatus.ACTIVE;
        if (removesAdministrator) {
            requireReauthentication(actor, currentPassword);
        }
        LastOrganizationAdministratorPolicy.requireAdministratorRemains(
                targetIsOrganizationAdministrator,
                !removesAdministrator,
                authorizationQuery.countActiveOrganizationAdministrators());

        Instant occurredAt = clock.instant();
        if (command.requestedStatus() == StaffIdentityStatus.ACTIVE) {
            StaffScopeAuthorizationPolicy.requireRoleScopeCombination(current.roles(), current.scopeType());
            if (current.scopeType() == StaffScopeType.BRANCH
                    && !authorizationQuery.hasActiveBranchAssignment(current.userId())) {
                throw new StaffIdentityStateConflictException(
                        "Branch-scoped staff require an active branch assignment to reactivate.");
            }
        }

        if (command.requestedStatus() == StaffIdentityStatus.DEACTIVATED) {
            int assignmentsClosed = assignmentStore.endAllActiveForUser(
                    command.targetUserId(), actor.id(), command.reason(), occurredAt);
            eventPublisher.publishEvent(new StaffIdentityAssignmentsClosed(
                    command.targetUserId(), actor.id(), assignmentsClosed,
                    occurredAt, true));
        }

        StaffIdentityAdministrationState updated = identityStore.changeStatus(
                command.targetUserId(),
                currentStatus,
                command.requestedStatus(),
                command.expectedVersion(),
                actor.id(),
                occurredAt);
        eventPublisher.publishEvent(new StaffIdentityLifecycleChanged(
                command.targetUserId(), currentStatus, command.requestedStatus(),
                actor.id(), occurredAt, true));
        return updated;
    }

    /** Replaces supported role/scope authority without self-promotion or assignment gaps. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public StaffIdentityAdministrationState changeRoleScope(
            ChangeStaffRoleScopeCommand command,
            AuthenticatedActor actor,
            String currentPassword) {
        Objects.requireNonNull(command, "Staff role/scope command is required.");
        requireActor(actor);
        requireOrganizationAdministrator(actor);
        StaffIdentityLifecyclePolicy.requireDifferentActorAndTarget(actor.id(), command.targetUserId());
        lockLifecycle(command.targetUserId(), actor);

        StaffIdentityAdministrationState current = find(command.targetUserId());
        requireExpectedVersion(current, command.expectedVersion());
        if (current.accountStatus() != StaffAccountStatus.ACTIVE) {
            throw new StaffIdentityStateConflictException(
                    "Only active staff authority can be changed.");
        }
        if (current.roles().equals(command.requestedRoles())
                && current.scopeType() == command.requestedScope()) {
            return current;
        }

        requireReauthentication(actor, currentPassword);
        StaffScopeAuthorizationPolicy.requireRoleScopeCombination(
                command.requestedRoles(), command.requestedScope());
        boolean targetWillHaveActiveAssignment = authorizationQuery
                .hasActiveBranchAssignment(command.targetUserId());
        if (command.requestedScope() == StaffScopeType.BRANCH && !targetWillHaveActiveAssignment) {
            throw new StaffIdentityStateConflictException(
                    "Branch-scoped staff require an active branch assignment.");
        }

        boolean targetIsOrganizationAdministrator = isOrganizationAdministrator(current);
        boolean targetRemainsOrganizationAdministrator =
                command.requestedRoles().contains(RoleCode.ADMIN)
                        && command.requestedScope() == StaffScopeType.ORGANIZATION;
        LastOrganizationAdministratorPolicy.requireAdministratorRemains(
                targetIsOrganizationAdministrator,
                targetRemainsOrganizationAdministrator,
                authorizationQuery.countActiveOrganizationAdministrators());

        Instant occurredAt = clock.instant();
        StaffIdentityAdministrationState updated = identityStore.changeRoleScope(
                command, current.scopeVersion(), actor.id(), occurredAt);
        eventPublisher.publishEvent(new StaffRoleScopeChanged(
                command.targetUserId(), current.roles(), updated.roles(),
                current.scopeType(), updated.scopeType(), actor.id(), occurredAt, true));
        return updated;
    }

    private void lockLifecycle(UUID targetUserId, AuthenticatedActor actor) {
        authorizationQuery.lockOrganizationAdministratorLifecycle();
        authorizationQuery.lockStaffLifecycle(targetUserId);
        requireOrganizationAdministrator(actor);
    }

    private StaffIdentityAdministrationState find(UUID userId) {
        return identityStore.find(userId).orElseThrow(StaffIdentityNotFoundException::new);
    }

    private void requireOrganizationAdministrator(AuthenticatedActor actor) {
        StaffScopeAuthorizationPolicy.requireOrganizationAdministrator(
                scopeQuery.findAuthorizationContext(actor.id())
                        .orElseThrow(() -> new StaffIdentityAuthorizationException(
                                "An active organization administrator is required.")));
    }

    private void requireReauthentication(AuthenticatedActor actor, String currentPassword) {
        if (!passwordVerifier.verify(actor.id(), actor.username(), currentPassword)) {
            throw new StaffIdentityAuthorizationException("Current password verification failed.");
        }
    }

    private static StaffIdentityStatus identityStatus(StaffAccountStatus status) {
        try {
            return StaffIdentityStatus.valueOf(status.name());
        } catch (IllegalArgumentException exception) {
            throw new StaffIdentityStateConflictException(
                    "This legacy staff account state cannot be changed through the current lifecycle.");
        }
    }

    private static boolean isOrganizationAdministrator(StaffIdentityAdministrationState state) {
        return state.accountStatus() == StaffAccountStatus.ACTIVE
                && state.roles().contains(RoleCode.ADMIN)
                && state.scopeType() == StaffScopeType.ORGANIZATION;
    }

    private static void requireExpectedVersion(
            StaffIdentityAdministrationState current, long expectedVersion) {
        if (current.securityVersion() != expectedVersion) {
            throw new StaffIdentityVersionConflictException(current.userId());
        }
    }

    private static void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null
                || actor.username() == null || actor.username().isBlank()) {
            throw new StaffIdentityValidationException("Authenticated actor is required.");
        }
    }
}
