package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
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
}
