package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        schema = "gym",
        name = "memberships")
class MembershipJpaEntity {

    @Id
    private UUID id;

    @Column(
            name = "membership_number",
            nullable = false,
            insertable = false,
            updatable = false)
    private Long membershipNumber;

    @Column(
            name = "membership_code",
            nullable = false,
            insertable = false,
            updatable = false,
            length = 32)
    private String membershipCode;

    @Column(
            name = "client_id",
            nullable = false)
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20)
    private MembershipStatus status;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by_user_id")
    private UUID cancelledByUserId;

    @Column(
            name = "cancellation_reason",
            columnDefinition = "text")
    private String cancellationReason;

    @Column(
            name = "created_by_user_id",
            nullable = false)
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

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

    protected MembershipJpaEntity() {
    }

    static MembershipJpaEntity create(
            UUID clientId,
            AuthenticatedActor actor,
            Instant occurredAt) {

        MembershipJpaEntity membership =
                new MembershipJpaEntity();

        membership.id =
                UUID.randomUUID();

        membership.clientId =
                clientId;

        membership.status =
                MembershipStatus.ACTIVE;

        membership.createdByUserId =
                actor.id();

        membership.updatedByUserId =
                actor.id();

        membership.createdAt =
                occurredAt;

        membership.updatedAt =
                occurredAt;

        return membership;
    }

    UUID id() {
        return id;
    }

    String membershipCode() {
        return membershipCode;
    }

    UUID clientId() {
        return clientId;
    }

    MembershipStatus status() {
        return status;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    long version() {
        return version;
    }
}