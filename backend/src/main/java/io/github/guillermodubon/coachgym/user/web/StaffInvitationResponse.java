package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.StaffIdentityValuePolicy;
import io.github.guillermodubon.coachgym.user.StaffInvitationDeliveryResult;
import io.github.guillermodubon.coachgym.user.StaffInvitationDetails;
import io.github.guillermodubon.coachgym.user.StaffInvitationDeliveryStatus;
import io.github.guillermodubon.coachgym.user.StaffInvitationStatus;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Safe HTTP projection; the persisted full email is never serialized. */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record StaffInvitationResponse(
        UUID invitationId,
        String maskedEmail,
        RoleCode proposedRole,
        StaffScopeType proposedScope,
        List<UUID> proposedBranchIds,
        StaffInvitationStatus status,
        Instant createdAt,
        Instant lastSentAt,
        Instant expiresAt,
        long version,
        StaffInvitationDeliveryStatus deliveryStatus) {

    static StaffInvitationResponse from(StaffInvitationDetails details) {
        return from(details, null);
    }

    static StaffInvitationResponse from(StaffInvitationDeliveryResult result) {
        return from(result.invitation(), result.deliveryStatus());
    }

    private static StaffInvitationResponse from(
            StaffInvitationDetails details,
            StaffInvitationDeliveryStatus deliveryStatus) {
        return new StaffInvitationResponse(
                details.invitationId(),
                StaffIdentityValuePolicy.maskEmail(details.invitedEmail()),
                details.proposedRole(),
                details.proposedScope(),
                details.proposedBranchIds().stream().sorted().toList(),
                details.status(),
                details.createdAt(),
                details.lastSentAt(),
                details.expiresAt(),
                details.version(),
                deliveryStatus);
    }
}
