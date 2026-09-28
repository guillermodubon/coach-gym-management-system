package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Safe invitation projection; it deliberately contains no token or fingerprint. */
public record StaffInvitationDetails(
        UUID invitationId,
        String invitedEmail,
        RoleCode proposedRole,
        StaffScopeType proposedScope,
        Set<UUID> proposedBranchIds,
        StaffInvitationStatus status,
        Instant createdAt,
        Instant lastSentAt,
        Instant expiresAt,
        long version) {

    public StaffInvitationDetails {
        if (invitationId == null) {
            throw new StaffIdentityValidationException("Invitation identity is required.");
        }
        invitedEmail = StaffIdentityValuePolicy.normalizeEmail(invitedEmail);
        Objects.requireNonNull(proposedRole, "Proposed role is required.");
        Objects.requireNonNull(proposedScope, "Proposed scope is required.");
        proposedBranchIds = StaffInvitationPolicy.requireValidProposal(
                proposedRole, proposedScope, proposedBranchIds);
        status = Objects.requireNonNull(status, "Invitation status is required.");
        createdAt = Objects.requireNonNull(createdAt, "Creation time is required.");
        lastSentAt = Objects.requireNonNull(lastSentAt, "Last sent time is required.");
        expiresAt = StaffInvitationPolicy.requireExpiration(
                proposedRole, createdAt, lastSentAt, expiresAt);
        if (version < 0) {
            throw new StaffIdentityValidationException("Invitation version must not be negative.");
        }
    }

    @Override
    public String toString() {
        return "StaffInvitationDetails[invitationId=" + invitationId
                + ", invitedEmail=" + StaffIdentityValuePolicy.maskEmail(invitedEmail)
                + ", proposedRole=" + proposedRole
                + ", proposedScope=" + proposedScope
                + ", proposedBranchCount=" + proposedBranchIds.size()
                + ", status=" + status
                + ", createdAt=" + createdAt
                + ", lastSentAt=" + lastSentAt
                + ", expiresAt=" + expiresAt
                + ", version=" + version
                + ']';
    }
}
