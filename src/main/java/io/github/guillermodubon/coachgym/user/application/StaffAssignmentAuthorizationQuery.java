package io.github.guillermodubon.coachgym.user.application;

import java.util.UUID;

/**
 * Read port for database-backed anti-lockout facts.
 *
 * <p>The port deliberately returns facts rather than authorization decisions;
 * the application policy remains the owner of role and operation decisions.</p>
 */
public interface StaffAssignmentAuthorizationQuery {

    /**
     * Serializes scope mutations that could remove the last organization
     * administrator in the surrounding transaction.
     */
    default void lockOrganizationAdministratorLifecycle() {
        // Implementations backed by PostgreSQL acquire the authoritative row lock.
    }

    /** Serializes assignment lifecycle checks for one staff account. */
    default void lockStaffLifecycle(UUID userId) {
        // Implementations backed by PostgreSQL acquire the authoritative row lock.
    }

    long countActiveOrganizationAdministrators();

    boolean isLastActiveOrganizationAdministrator(UUID userId);

    boolean hasActiveBranchAssignment(UUID userId, UUID branchId);

    boolean hasAnotherActiveBranchAssignment(UUID userId, UUID excludingAssignmentId);

    boolean branchAdministratorControlsBranch(UUID actorUserId, UUID branchId);

    boolean branchAdministratorControlsTarget(UUID actorUserId, UUID targetUserId);
}
