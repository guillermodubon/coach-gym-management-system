package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Token-protected, privacy-minimized preview of a pending invitation. */
public record StaffInvitationInspection(
        UUID invitationId,
        String maskedEmail,
        RoleCode proposedRole,
        StaffScopeType proposedScope,
        List<StaffInvitationBranchSummary> proposedBranches,
        Instant expiresAt) {

    public StaffInvitationInspection {
        if (invitationId == null) {
            throw new StaffIdentityValidationException("Invitation identity is required.");
        }
        if (maskedEmail == null || maskedEmail.isBlank() || maskedEmail.length() > 254) {
            throw new StaffIdentityValidationException("Masked email is required.");
        }
        Objects.requireNonNull(proposedRole, "Proposed role is required.");
        Objects.requireNonNull(proposedScope, "Proposed scope is required.");
        proposedBranches = proposedBranches == null ? List.of() : List.copyOf(proposedBranches);
        Objects.requireNonNull(expiresAt, "Invitation expiration is required.");
        StaffInvitationPolicy.requireValidProposal(
                proposedRole,
                proposedScope,
                proposedBranches.stream().map(StaffInvitationBranchSummary::branchId).toList());
    }

    @Override
    public String toString() {
        return "StaffInvitationInspection[invitationId=" + invitationId
                + ", maskedEmail=" + maskedEmail
                + ", proposedRole=" + proposedRole
                + ", proposedScope=" + proposedScope
                + ", proposedBranchCount=" + proposedBranches.size()
                + ", expiresAt=" + expiresAt + ']';
    }
}
