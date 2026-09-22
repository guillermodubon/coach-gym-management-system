package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.AssignStaffToBranchCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthorizedBranchQuery;
import io.github.guillermodubon.coachgym.user.ChangeStaffScopeCommand;
import io.github.guillermodubon.coachgym.user.EndStaffBranchAssignmentCommand;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffBranchAssigned;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentDetails;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentEnded;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentPolicy;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentQuery;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentStateConflictException;
import io.github.guillermodubon.coachgym.user.StaffBranchAuthorizationException;
import io.github.guillermodubon.coachgym.user.StaffScopeAuthorizationPolicy;
import io.github.guillermodubon.coachgym.user.StaffScopeChanged;
import io.github.guillermodubon.coachgym.user.StaffScopeDetails;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional administrative use cases for staff scopes and branch
 * assignments. Authorization is checked from database-backed scope facts;
 * the actor-supplied identifier is used only for attribution.
 */
@Service
public class StaffBranchAssignmentApplicationService {

    private final StaffScopeQuery scopeQuery;
    private final StaffBranchAssignmentQuery assignmentQuery;
    private final AuthorizedBranchQuery authorizedBranchQuery;
    private final StaffAssignmentAuthorizationQuery authorizationQuery;
    private final StaffScopeStore scopeStore;
    private final StaffBranchAssignmentStore assignmentStore;
    private final StaffBranchAssignmentAdminQuery adminQuery;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public StaffBranchAssignmentApplicationService(
            StaffScopeQuery scopeQuery,
            StaffBranchAssignmentQuery assignmentQuery,
            AuthorizedBranchQuery authorizedBranchQuery,
            StaffAssignmentAuthorizationQuery authorizationQuery,
            StaffScopeStore scopeStore,
            StaffBranchAssignmentStore assignmentStore,
            StaffBranchAssignmentAdminQuery adminQuery,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.scopeQuery = Objects.requireNonNull(scopeQuery);
        this.assignmentQuery = Objects.requireNonNull(assignmentQuery);
        this.authorizedBranchQuery = Objects.requireNonNull(authorizedBranchQuery);
        this.authorizationQuery = Objects.requireNonNull(authorizationQuery);
        this.scopeStore = Objects.requireNonNull(scopeStore);
        this.assignmentStore = Objects.requireNonNull(assignmentStore);
        this.adminQuery = Objects.requireNonNull(adminQuery);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Assigns a supported staff account to one active branch. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public StaffBranchAssignmentDetails assign(
            AssignStaffToBranchCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Assignment command is required.");
        requireActor(actor);
        StaffAuthorizationContext actorContext = requireActorScope(actor);
        StaffScopeAuthorizationPolicy.requireOrganizationAdministrator(actorContext);
        StaffScopeAuthorizationPolicy.requireNotSelf(actor.id(), command.targetUserId());

        StaffAuthorizationContext target = requireTargetScope(command.targetUserId());
        requireActiveTarget(target);
        boolean branchIsActive = authorizedBranchQuery.findAuthorizedActiveBranches(actor.id())
                .stream()
                .anyMatch(branch -> branch.id().equals(command.branchId()));
        boolean duplicate = authorizationQuery.hasActiveBranchAssignment(
                command.targetUserId(), command.branchId());
        StaffBranchAssignmentPolicy.requireCreationAllowed(branchIsActive, duplicate);

        Instant occurredAt = clock.instant();
        StaffBranchAssignmentDetails assigned = assignmentStore.assign(
                command, actor.id(), occurredAt);
        eventPublisher.publishEvent(new StaffBranchAssigned(
                assigned.id(), assigned.userId(), assigned.branchId(),
                actor.id(), actor.username(), occurredAt, true));
        return assigned;
    }

    /** Ends one active assignment while preserving its append-only history. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public StaffBranchAssignmentDetails end(
            EndStaffBranchAssignmentCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Assignment end command is required.");
        requireActor(actor);
        StaffAuthorizationContext actorContext = requireActorScope(actor);
        StaffScopeAuthorizationPolicy.requireOrganizationAdministrator(actorContext);

        StaffBranchAssignmentDetails current = assignmentQuery.findById(command.assignmentId())
                .orElseThrow(StaffBranchAssignmentNotFoundException::new);
        StaffScopeAuthorizationPolicy.requireNotSelf(actor.id(), current.userId());
        authorizationQuery.lockStaffLifecycle(current.userId());
        StaffAuthorizationContext target = requireTargetScope(current.userId());
        requireActiveTarget(target);
        StaffBranchAssignmentPolicy.requireEndAllowed(current, command);
        boolean anotherActiveAssignment = authorizationQuery.hasAnotherActiveBranchAssignment(
                current.userId(), current.id());
        StaffBranchAssignmentPolicy.requireActiveAssignmentRetained(
                target, anotherActiveAssignment, false);

        Instant occurredAt = clock.instant();
        StaffBranchAssignmentDetails ended = assignmentStore.end(
                command, actor.id(), occurredAt);
        eventPublisher.publishEvent(new StaffBranchAssignmentEnded(
                ended.id(), ended.userId(), ended.branchId(),
                actor.id(), actor.username(), occurredAt, true));
        return ended;
    }

    /** Changes one staff account's single organizational scope. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public StaffScopeDetails changeScope(
            ChangeStaffScopeCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Scope change command is required.");
        requireActor(actor);
        StaffAuthorizationContext actorContext = requireActorScope(actor);
        StaffScopeAuthorizationPolicy.requireOrganizationAdministrator(actorContext);
        StaffScopeAuthorizationPolicy.requireNotSelf(actor.id(), command.targetUserId());

        StaffScopeDetails currentScope = scopeQuery.findScope(command.targetUserId())
                .orElseThrow(StaffScopeNotFoundException::new);
        requireExpectedVersion(
                currentScope.version(), command.expectedVersion(), command.targetUserId());
        StaffAuthorizationContext target = requireTargetScope(command.targetUserId());
        requireActiveTarget(target);
        if (currentScope.scopeType() == command.requestedScope()) {
            return currentScope;
        }

        authorizationQuery.lockOrganizationAdministratorLifecycle();
        boolean targetWillHaveActiveAssignment = !assignmentQuery
                .findActive(command.targetUserId()).isEmpty();
        long administratorCount = authorizationQuery.countActiveOrganizationAdministrators();
        StaffScopeAuthorizationPolicy.requireScopeChangeAllowed(
                actorContext,
                target,
                command.requestedScope(),
                targetWillHaveActiveAssignment,
                administratorCount);

        Instant occurredAt = clock.instant();
        StaffScopeDetails updated = scopeStore.update(command, actor.id(), occurredAt);
        eventPublisher.publishEvent(new StaffScopeChanged(
                currentScope.userId(),
                currentScope.scopeType(),
                updated.scopeType(),
                actor.id(),
                actor.username(),
                occurredAt,
                true));
        return updated;
    }

    /** Returns bounded assignment history to organization-scoped administrators. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public StaffBranchAssignmentSearchPage findAssignments(
            StaffBranchAssignmentSearchQuery query,
            AuthenticatedActor actor) {
        Objects.requireNonNull(query, "Assignment search query is required.");
        requireOrganizationAdministrator(actor);
        return adminQuery.findPage(query);
    }

    /** Returns one staff member's assignment history to organization administrators. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public StaffBranchAssignmentSearchPage findAssignmentsForUser(
            java.util.UUID targetUserId,
            StaffBranchAssignmentSearchQuery query,
            AuthenticatedActor actor) {
        Objects.requireNonNull(targetUserId, "targetUserId is required.");
        Objects.requireNonNull(query, "Assignment search query is required.");
        requireOrganizationAdministrator(actor);
        return adminQuery.findPage(query.forUser(targetUserId));
    }

    /** Returns one staff member's current scope to organization administrators. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public StaffScopeDetails findScope(
            java.util.UUID targetUserId,
            AuthenticatedActor actor) {
        Objects.requireNonNull(targetUserId, "targetUserId is required.");
        requireOrganizationAdministrator(actor);
        return scopeQuery.findScope(targetUserId)
                .orElseThrow(StaffScopeNotFoundException::new);
    }

    private StaffAuthorizationContext requireActorScope(AuthenticatedActor actor) {
        return scopeQuery.findAuthorizationContext(actor.id())
                .orElseThrow(() -> new StaffBranchAuthorizationException(
                        "The authenticated staff scope is not available."));
    }

    private StaffAuthorizationContext requireTargetScope(java.util.UUID userId) {
        return scopeQuery.findAuthorizationContext(userId)
                .orElseThrow(StaffScopeNotFoundException::new);
    }

    private static void requireActiveTarget(StaffAuthorizationContext target) {
        if (target.accountStatus() != StaffAccountStatus.ACTIVE) {
            throw new StaffBranchAssignmentStateConflictException(
                    "inactive staff cannot receive or change branch assignments");
        }
    }

    private static void requireExpectedVersion(long current, long expected, java.util.UUID userId) {
        if (current != expected) {
            throw new StaffScopeVersionConflictException(userId);
        }
    }

    private static void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null
                || actor.username() == null || actor.username().isBlank()) {
            throw new StaffScopeValidationException(
                    "Authenticated actor is required.");
        }
    }

    private void requireOrganizationAdministrator(AuthenticatedActor actor) {
        requireActor(actor);
        StaffScopeAuthorizationPolicy.requireOrganizationAdministrator(
                requireActorScope(actor));
    }
}
