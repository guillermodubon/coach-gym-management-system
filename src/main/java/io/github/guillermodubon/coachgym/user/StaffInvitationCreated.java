package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Privacy-safe lifecycle event; identity credentials and email content are omitted. */
public record StaffInvitationCreated(
        UUID invitationId,
        UUID organizationId,
        UUID invitedByUserId,
        String maskedRecipient,
        RoleCode proposedRole,
        StaffScopeType proposedScope,
        Set<UUID> proposedBranchIds,
        Instant occurredAt) {

    public StaffInvitationCreated {
        if (invitationId == null || organizationId == null || invitedByUserId == null
                || proposedRole == null || proposedScope == null || occurredAt == null
                || maskedRecipient == null
                || !maskedRecipient.matches("^.{1}\\*{3}@[A-Za-z0-9.-]+$")) {
            throw new StaffIdentityValidationException("Invitation audit event is incomplete.");
        }
        proposedBranchIds = StaffInvitationPolicy.requireValidProposal(
                proposedRole, proposedScope, proposedBranchIds);
    }

    @Override
    public String toString() {
        return "StaffInvitationCreated[invitationId=" + invitationId
                + ", organizationId=" + organizationId
                + ", invitedByUserId=" + invitedByUserId
                + ", maskedRecipient=" + maskedRecipient
                + ", proposedRole=" + proposedRole
                + ", proposedScope=" + proposedScope
                + ", branchCount=" + proposedBranchIds.size()
                + ", occurredAt=" + occurredAt + ']';
    }
}
