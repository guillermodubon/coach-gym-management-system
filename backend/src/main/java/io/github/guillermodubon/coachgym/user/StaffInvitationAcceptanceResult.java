package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Safe result of accepting an invitation; it contains no email or credential data. */
public record StaffInvitationAcceptanceResult(
        UUID userId,
        RoleCode role,
        StaffScopeType scope,
        Set<UUID> branchIds,
        Instant acceptedAt) {

    public StaffInvitationAcceptanceResult {
        if (userId == null || role == null || scope == null || acceptedAt == null) {
            throw new StaffIdentityValidationException("Invitation acceptance result is incomplete.");
        }
        branchIds = StaffInvitationPolicy.requireValidProposal(role, scope, branchIds);
    }

    @Override
    public String toString() {
        return "StaffInvitationAcceptanceResult[userId=" + userId
                + ", role=" + role
                + ", scope=" + scope
                + ", branchCount=" + branchIds.size()
                + ", acceptedAt=" + acceptedAt + ']';
    }
}
