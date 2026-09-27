package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Privacy-safe fact emitted after an invitation and its staff account commit. */
public record StaffInvitationAccepted(
        UUID invitationId,
        UUID organizationId,
        UUID userId,
        UUID invitedByUserId,
        RoleCode role,
        StaffScopeType scope,
        Set<UUID> branchIds,
        Instant occurredAt) {

    public StaffInvitationAccepted {
        if (invitationId == null || organizationId == null || userId == null || invitedByUserId == null
                || role == null || scope == null || occurredAt == null) {
            throw new StaffIdentityValidationException("Invitation acceptance event is incomplete.");
        }
        branchIds = StaffInvitationPolicy.requireValidProposal(role, scope, branchIds);
    }

    @Override
    public String toString() {
        return "StaffInvitationAccepted[invitationId=" + invitationId
                + ", organizationId=" + organizationId
                + ", userId=" + userId
                + ", invitedByUserId=" + invitedByUserId
                + ", role=" + role
                + ", scope=" + scope
                + ", branchCount=" + branchIds.size()
                + ", occurredAt=" + occurredAt + ']';
    }
}
