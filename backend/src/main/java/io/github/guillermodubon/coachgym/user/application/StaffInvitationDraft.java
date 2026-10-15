package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffIdentityValuePolicy;
import io.github.guillermodubon.coachgym.user.StaffInvitationPolicy;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Persistence input for a pending invitation. Contains only a token fingerprint. */
public record StaffInvitationDraft(
        UUID invitationId,
        UUID organizationId,
        String invitedEmail,
        RoleCode proposedRole,
        StaffScopeType proposedScope,
        Set<UUID> proposedBranchIds,
        StaffTokenFingerprint tokenFingerprint,
        Instant createdAt,
        Instant lastSentAt,
        Instant expiresAt,
        UUID invitedByUserId) {

    public StaffInvitationDraft {
        if (invitationId == null || organizationId == null || invitedByUserId == null) {
            throw new IllegalArgumentException("Invitation identities are required.");
        }
        invitedEmail = StaffIdentityValuePolicy.normalizeEmail(invitedEmail);
        proposedBranchIds = StaffInvitationPolicy.requireValidProposal(
                proposedRole, proposedScope, proposedBranchIds);
        if (tokenFingerprint == null
                || !StaffTokenPurpose.INVITATION.schemeVersion().equals(tokenFingerprint.schemeVersion())) {
            throw new IllegalArgumentException("Invitation token fingerprint is invalid.");
        }
        StaffInvitationPolicy.requireExpiration(proposedRole, createdAt, lastSentAt, expiresAt);
    }

    @Override
    public String toString() {
        return "StaffInvitationDraft[invitationId=" + invitationId
                + ", organizationId=" + organizationId
                + ", invitedEmail=<redacted>"
                + ", proposedRole=" + proposedRole
                + ", proposedScope=" + proposedScope
                + ", proposedBranchCount=" + proposedBranchIds.size()
                + ", tokenFingerprint=<redacted>"
                + ", createdAt=" + createdAt
                + ", lastSentAt=" + lastSentAt
                + ", expiresAt=" + expiresAt
                + ", invitedByUserId=" + invitedByUserId + ']';
    }
}
