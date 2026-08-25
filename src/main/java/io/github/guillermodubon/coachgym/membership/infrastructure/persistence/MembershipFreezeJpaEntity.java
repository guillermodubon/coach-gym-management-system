package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import io.github.guillermodubon.coachgym.membership.MembershipFreezeDetails;
import io.github.guillermodubon.coachgym.membership.domain.MembershipFreeze;
import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        schema = "gym",
        name = "membership_freezes")
class MembershipFreezeJpaEntity {

    @Id
    private UUID id;

    @Column(
            name = "membership_id",
            nullable = false)
    private UUID membershipId;

    @Column(
            name = "membership_period_id",
            nullable = false)
    private UUID membershipPeriodId;

    @Column(
            name = "starts_on",
            nullable = false)
    private LocalDate startsOn;

    @Column(
            name = "planned_ends_on",
            nullable = false)
    private LocalDate plannedEndsOn;

    @Column(
            nullable = false,
            columnDefinition = "text")
    private String reason;

    @Column(name = "reactivated_on")
    private LocalDate reactivatedOn;

    @Column(
            name = "created_by_user_id",
            nullable = false)
    private UUID createdByUserId;

    @Column(name = "reactivated_by_user_id")
    private UUID reactivatedByUserId;

    @Column(
            name = "created_at",
            nullable = false)
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "cancelled_on")
    private LocalDate cancelledOn;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    LocalDate cancelledOn() {
        return cancelledOn;
    }

    UUID cancelledByUserId() {
        return cancelledByUserId;
    }

    protected MembershipFreezeJpaEntity() {
    }

    static MembershipFreezeJpaEntity create(
            MembershipFreeze freeze,
            AuthenticatedActor actor,
            Instant occurredAt) {

        if (freeze == null) {
            throw new MembershipValidationException(
                    "Membership freeze must be provided.");
        }

        validateActor(actor);
        validateOccurredAt(occurredAt);

        MembershipFreezeJpaEntity entity =
                new MembershipFreezeJpaEntity();

        entity.id = UUID.randomUUID();
        entity.membershipId = freeze.membershipId();
        entity.membershipPeriodId =
                freeze.membershipPeriodId();
        entity.startsOn = freeze.startsOn();
        entity.plannedEndsOn =
                freeze.plannedEndsOn();
        entity.reason = freeze.reason();
        entity.createdByUserId = actor.id();
        entity.createdAt = occurredAt;
        entity.updatedAt = occurredAt;

        return entity;
    }

    void reactivate(
            LocalDate reactivatedOn,
            AuthenticatedActor actor,
            Instant occurredAt) {

        if (reactivatedOn == null) {
            throw new MembershipValidationException(
                    "Membership reactivation date "
                            + "must be provided.");
        }

        validateActor(actor);
        validateOccurredAt(occurredAt);

        if (!open()) {
            throw new MembershipValidationException(
                    "Membership freeze is already closed.");
        }

        if (reactivatedOn.isBefore(startsOn)) {
            throw new MembershipValidationException(
                    "Membership reactivation date must not be "
                            + "before the freeze start date.");
        }

        this.reactivatedOn = reactivatedOn;
        this.reactivatedByUserId = actor.id();
        this.updatedAt = occurredAt;
    }

    boolean open() {
        return reactivatedOn == null
                && cancelledOn == null;
    }

    UUID id() {
        return id;
    }

    UUID membershipId() {
        return membershipId;
    }

    long version() {
        return version;
    }

    MembershipFreezeDetails toDetails() {
        return new MembershipFreezeDetails(
                id,
                membershipId,
                membershipPeriodId,
                startsOn,
                plannedEndsOn,
                reason,
                reactivatedOn,
                createdByUserId,
                reactivatedByUserId,
                cancelledOn,
                cancelledByUserId,
                createdAt,
                updatedAt,
                version);
    }

    private static void validateActor(
            AuthenticatedActor actor) {

        if (actor == null || actor.id() == null) {
            throw new MembershipValidationException(
                    "Authenticated actor must be provided.");
        }
    }

    private static void validateOccurredAt(
            Instant occurredAt) {

        if (occurredAt == null) {
            throw new MembershipValidationException(
                    "Membership freeze occurrence time "
                            + "must be provided.");
        }
    }

    void closeForCancellation(
            LocalDate cancelledOn,
            AuthenticatedActor actor,
            Instant occurredAt) {

        if (cancelledOn == null) {
            throw new MembershipValidationException(
                    "Membership cancellation date "
                            + "must be provided.");
        }

        validateActor(actor);
        validateOccurredAt(occurredAt);

        if (!open()) {
            throw new MembershipValidationException(
                    "Membership freeze is already closed.");
        }

        if (cancelledOn.isBefore(startsOn)) {
            throw new MembershipValidationException(
                    "Membership cancellation date must not be "
                            + "before the freeze start date.");
        }

        this.cancelledOn =
                cancelledOn;

        this.cancelledByUserId =
                actor.id();

        this.updatedAt =
                occurredAt;
    }
}
