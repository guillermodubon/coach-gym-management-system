package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffInvitationDetails;
import io.github.guillermodubon.coachgym.user.StaffInvitationStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Internal persistence projection; the fingerprint is redacted from diagnostics. */
public record StaffInvitationRecord(
        UUID invitationId,
        UUID organizationId,
        String invitedEmail,
        RoleCode proposedRole,
        StaffScopeType proposedScope,
        Set<UUID> proposedBranchIds,
        StaffInvitationStatus status,
        StaffTokenFingerprint tokenFingerprint,
        Instant createdAt,
        Instant lastSentAt,
        Instant expiresAt,
        Instant acceptedAt,
        UUID acceptedUserId,
        Instant expiredAt,
        Instant revokedAt,
        UUID invitedByUserId,
        long version) {

    public StaffInvitationRecord {
        proposedBranchIds = Set.copyOf(proposedBranchIds);
    }

    public StaffInvitationDetails safeDetails() {
        return new StaffInvitationDetails(
                invitationId,
                invitedEmail,
                proposedRole,
                proposedScope,
                proposedBranchIds,
                status,
                createdAt,
                lastSentAt,
                expiresAt,
                version);
    }

    @Override
    public String toString() {
        return "StaffInvitationRecord[invitationId=" + invitationId
                + ", organizationId=" + organizationId
                + ", invitedEmail=<redacted>"
                + ", proposedRole=" + proposedRole
                + ", proposedScope=" + proposedScope
                + ", proposedBranchCount=" + proposedBranchIds.size()
                + ", status=" + status
                + ", tokenFingerprint=<redacted>"
                + ", createdAt=" + createdAt
                + ", lastSentAt=" + lastSentAt
                + ", expiresAt=" + expiresAt
                + ", acceptedAt=" + acceptedAt
                + ", acceptedUserId=" + acceptedUserId
                + ", expiredAt=" + expiredAt
                + ", revokedAt=" + revokedAt
                + ", invitedByUserId=" + invitedByUserId
                + ", version=" + version + ']';
    }
}
