package io.github.guillermodubon.coachgym.user;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pure authorization and anti-lockout rules for staff scope changes. */
public final class StaffScopeAuthorizationPolicy {

    private StaffScopeAuthorizationPolicy() {
    }

    public static boolean isCompatible(Set<RoleCode> roles, StaffScopeType scopeType) {
        Objects.requireNonNull(roles, "roles is required");
        Objects.requireNonNull(scopeType, "scopeType is required");
        boolean hasAdmin = roles.contains(RoleCode.ADMIN);
        boolean hasReceptionist = roles.contains(RoleCode.RECEPTIONIST);
        if (hasAdmin && !hasReceptionist) {
            return true;
        }
        return hasReceptionist && !hasAdmin && scopeType == StaffScopeType.BRANCH;
    }

    public static void requireRoleScopeCombination(Set<RoleCode> roles, StaffScopeType scopeType) {
        if (!isCompatible(roles, scopeType)) {
            throw new StaffScopeValidationException("role and organizational scope combination is not allowed");
        }
    }

    public static void requireOrganizationAdministrator(StaffAuthorizationContext actor) {
        Objects.requireNonNull(actor, "actor is required");
        if (!actor.organizationAdmin()) {
            throw new StaffBranchAuthorizationException(
                    "only an active organization administrator may manage staff scope and assignments");
        }
    }

    public static void requireNotSelf(UUID actorId, UUID targetUserId) {
        if (Objects.equals(actorId, targetUserId)) {
            throw new StaffScopeStateConflictException("a staff member cannot change their own scope");
        }
    }

    public static void requireScopeChangeAllowed(
            StaffAuthorizationContext actor,
            StaffAuthorizationContext target,
            StaffScopeType requestedScope,
            boolean targetWillHaveActiveAssignment,
            long currentOrganizationAdministratorCount) {
        requireOrganizationAdministrator(actor);
        Objects.requireNonNull(target, "target is required");
        Objects.requireNonNull(requestedScope, "requestedScope is required");
        requireNotSelf(actor.userId(), target.userId());
        requireRoleScopeCombination(target.roles(), requestedScope);
        if (requestedScope == StaffScopeType.BRANCH && !targetWillHaveActiveAssignment) {
            throw new StaffScopeStateConflictException("branch-scoped staff must retain an active assignment");
        }
        if (target.organizationAdmin()
                && requestedScope != StaffScopeType.ORGANIZATION
                && currentOrganizationAdministratorCount <= 1) {
            throw new StaffScopeStateConflictException(
                    "the last organization administrator cannot lose organization scope");
        }
    }

    public static void requireOrganizationAdministratorWillRemain(
            StaffAuthorizationContext actor,
            boolean targetIsOrganizationAdministrator,
            long currentOrganizationAdministratorCount) {
        requireOrganizationAdministrator(actor);
        if (targetIsOrganizationAdministrator && currentOrganizationAdministratorCount <= 1) {
            throw new StaffScopeStateConflictException(
                    "the last organization administrator cannot be removed");
        }
    }
}
