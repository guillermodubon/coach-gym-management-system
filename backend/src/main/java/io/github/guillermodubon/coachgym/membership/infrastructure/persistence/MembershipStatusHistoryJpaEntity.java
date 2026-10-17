package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        schema = "gym",
        name = "membership_status_history")
class MembershipStatusHistoryJpaEntity {

    @Id
    private UUID id;

    @Column(
            name = "membership_id",
            nullable = false)
    private UUID membershipId;

    @Column(name = "membership_period_id")
    private UUID membershipPeriodId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "previous_status",
            length = 20)
    private MembershipStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "new_status",
            nullable = false,
            length = 20)
    private MembershipStatus newStatus;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(
            name = "occurred_at",
            nullable = false)
    private Instant occurredAt;

    @Column(name = "changed_by_user_id")
    private UUID changedByUserId;

    protected MembershipStatusHistoryJpaEntity() {
    }

    static MembershipStatusHistoryJpaEntity
    initialActivation(
            UUID membershipId,
            UUID membershipPeriodId,
            AuthenticatedActor actor,
            Instant occurredAt) {

        MembershipStatusHistoryJpaEntity history =
                new MembershipStatusHistoryJpaEntity();

        history.id =
                UUID.randomUUID();

        history.membershipId =
                membershipId;

        history.membershipPeriodId =
                membershipPeriodId;

        history.previousStatus =
                null;

        history.newStatus =
                MembershipStatus.ACTIVE;

        history.reason =
                "Initial membership creation.";

        history.occurredAt =
                occurredAt;

        history.changedByUserId =
                actor.id();

        return history;
    }

    static MembershipStatusHistoryJpaEntity renewalTransition(
            UUID membershipId,
            UUID membershipPeriodId,
            MembershipStatus previousStatus,
            MembershipStatus resultingStatus,
            AuthenticatedActor actor,
            Instant occurredAt) {

        if (previousStatus == null) {
            throw new IllegalArgumentException(
                    "Previous membership status must be provided.");
        }

        if (resultingStatus == null) {
            throw new IllegalArgumentException(
                    "Resulting membership status must be provided.");
        }

        MembershipStatusHistoryJpaEntity history =
                new MembershipStatusHistoryJpaEntity();

        history.id =
                UUID.randomUUID();

        history.membershipId =
                membershipId;

        history.membershipPeriodId =
                membershipPeriodId;

        history.previousStatus =
                previousStatus;

        history.newStatus =
                resultingStatus;

        history.reason =
                "Membership reactivated by renewal.";

        history.occurredAt =
                occurredAt;

        history.changedByUserId =
                actor.id();

        return history;
    }

    static MembershipStatusHistoryJpaEntity frozen(
            UUID membershipId,
            UUID membershipPeriodId,
            AuthenticatedActor actor,
            Instant occurredAt) {

        return transition(
                membershipId,
                membershipPeriodId,
                MembershipStatus.ACTIVE,
                MembershipStatus.FROZEN,
                "Membership frozen.",
                actor,
                occurredAt);
    }

    static MembershipStatusHistoryJpaEntity reactivated(
            UUID membershipId,
            UUID membershipPeriodId,
            AuthenticatedActor actor,
            Instant occurredAt) {

        return transition(
                membershipId,
                membershipPeriodId,
                MembershipStatus.FROZEN,
                MembershipStatus.ACTIVE,
                "Membership reactivated.",
                actor,
                occurredAt);
    }

    private static MembershipStatusHistoryJpaEntity transition(
            UUID membershipId,
            UUID membershipPeriodId,
            MembershipStatus previousStatus,
            MembershipStatus newStatus,
            String reason,
            AuthenticatedActor actor,
            Instant occurredAt) {

        if (membershipId == null) {
            throw new MembershipValidationException(
                    "Membership identifier must be provided.");
        }

        if (membershipPeriodId == null) {
            throw new MembershipValidationException(
                    "Membership period identifier must be provided.");
        }

        if (previousStatus == null) {
            throw new MembershipValidationException(
                    "Previous membership status must be provided.");
        }

        if (newStatus == null) {
            throw new MembershipValidationException(
                    "New membership status must be provided.");
        }

        if (previousStatus == newStatus) {
            throw new MembershipValidationException(
                    "Membership status history must represent "
                            + "a status change.");
        }

        if (reason == null || reason.isBlank()) {
            throw new MembershipValidationException(
                    "Membership status change reason must not be blank.");
        }

        if (actor == null || actor.id() == null) {
            throw new MembershipValidationException(
                    "Authenticated actor must be provided.");
        }

        if (occurredAt == null) {
            throw new MembershipValidationException(
                    "Membership status occurrence time must be provided.");
        }

        MembershipStatusHistoryJpaEntity history =
                new MembershipStatusHistoryJpaEntity();

        history.id = UUID.randomUUID();
        history.membershipId = membershipId;
        history.membershipPeriodId = membershipPeriodId;
        history.previousStatus = previousStatus;
        history.newStatus = newStatus;
        history.reason = reason.trim();
        history.occurredAt = occurredAt;
        history.changedByUserId = actor.id();

        return history;
    }

    static MembershipStatusHistoryJpaEntity cancelled(
            UUID membershipId,
            UUID membershipPeriodId,
            MembershipStatus previousStatus,
            AuthenticatedActor actor,
            Instant occurredAt) {

        return transition(
                membershipId,
                membershipPeriodId,
                previousStatus,
                MembershipStatus.CANCELLED,
                "Membership cancelled.",
                actor,
                occurredAt);
    }

}
