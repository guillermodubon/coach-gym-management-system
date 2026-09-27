package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.StaffInvitationStatus;
import io.github.guillermodubon.coachgym.user.StaffInvitationDeliveryStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Internal persistence port for invitation lifecycle records. */
public interface StaffInvitationPersistence {

    StaffInvitationRecord create(StaffInvitationDraft draft);

    UUID reserveDeliveryAttempt(UUID invitationId, long invitationVersion, Instant attemptedAt);

    void completeDeliveryAttempt(
            UUID invitationId,
            long invitationVersion,
            StaffInvitationDeliveryStatus status,
            Instant completedAt);

    Optional<StaffInvitationRecord> findById(UUID invitationId);

    Optional<StaffInvitationRecord> findPendingByFingerprint(StaffTokenFingerprint fingerprint);

    /** Locks a canonical pending invitation so acceptance, revocation, and resend serialize. */
    Optional<StaffInvitationRecord> lockPendingByFingerprint(StaffTokenFingerprint fingerprint);

    StaffInvitationPage findPage(StaffInvitationPageQuery query);

    StaffInvitationRecord transition(
            UUID invitationId,
            StaffInvitationStatus target,
            Instant occurredAt,
            UUID acceptedUserId,
            long expectedVersion);

    StaffInvitationRecord rotatePendingToken(
            UUID invitationId,
            StaffTokenFingerprint replacementFingerprint,
            Instant sentAt,
            Instant expiresAt,
            long expectedVersion);
}
