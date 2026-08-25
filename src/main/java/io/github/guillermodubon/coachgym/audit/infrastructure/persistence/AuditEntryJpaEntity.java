package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import io.github.guillermodubon.coachgym.client.ClientRegistered;
import io.github.guillermodubon.coachgym.membership.*;
import io.github.guillermodubon.coachgym.plan.PlanChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionPlanEligibilityChanged;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "gym", name = "audit_entries")
class AuditEntryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_identifier_snapshot", length = 100)
    private String actorIdentifierSnapshot;

    @Column(name = "action_code", nullable = false, length = 100)
    private String actionCode;

    @Column(name = "resource_type", nullable = false, length = 100)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "resource_code_snapshot", length = 64)
    private String resourceCodeSnapshot;

    @Column(columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEntryJpaEntity() {}

    static AuditEntryJpaEntity from(ClientRegistered event) {
        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();
        entry.id = UUID.randomUUID();
        entry.actorUserId = event.actorUserId();
        entry.actorIdentifierSnapshot = event.actorIdentifier();
        entry.actionCode = "CLIENT_REGISTERED";
        entry.resourceType = "CLIENT";
        entry.resourceId = event.clientId();
        entry.resourceCodeSnapshot = event.clientCode();
        entry.summary = "Client registered.";
        entry.metadata = Map.of();
        entry.occurredAt = event.occurredAt();
        return entry;
    }

    static AuditEntryJpaEntity from(PlanChanged event) {
        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();
        entry.id = UUID.randomUUID();
        entry.actorUserId = event.actorUserId();
        entry.actorIdentifierSnapshot = event.actorIdentifier();
        entry.actionCode = "PLAN_" + event.changeType().name();
        entry.resourceType = "MEMBERSHIP_PLAN";
        entry.resourceId = event.planId();
        entry.resourceCodeSnapshot = event.planCode();
        entry.summary = switch (event.changeType()) {
            case CREATED -> "Membership plan created.";
            case UPDATED -> "Membership plan updated.";
            case DEACTIVATED -> "Membership plan deactivated.";
            case REACTIVATED -> "Membership plan reactivated.";
        };
        entry.metadata = Map.of();
        entry.occurredAt = event.occurredAt();
        return entry;
    }

    static AuditEntryJpaEntity from(PromotionChanged event) {
        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();

        entry.id = UUID.randomUUID();
        entry.actorUserId = event.actorUserId();
        entry.actorIdentifierSnapshot = event.actorIdentifier();
        entry.actionCode =
                "PROMOTION_" + event.changeType().name();
        entry.resourceType = "PROMOTION";
        entry.resourceId = event.promotionId();
        entry.resourceCodeSnapshot = event.promotionCode();
        entry.summary = promotionSummary(event);
        entry.metadata = Map.of();
        entry.occurredAt = event.occurredAt();

        return entry;
    }

    private static String promotionSummary(
            PromotionChanged event) {

        return switch (event.changeType()) {
            case CREATED ->
                    "Promotion created.";

            case UPDATED ->
                    "Promotion updated.";

            case DEACTIVATED ->
                    "Promotion deactivated.";

            case REACTIVATED ->
                    "Promotion reactivated.";

            case ELIGIBLE_PLANS_CHANGED ->
                    "Promotion eligible plans changed.";
        };
    }

    static AuditEntryJpaEntity from(
            PromotionPlanEligibilityChanged event) {

        AuditEntryJpaEntity entry =
                new AuditEntryJpaEntity();

        entry.id = UUID.randomUUID();
        entry.actorUserId =
                event.actorUserId();
        entry.actorIdentifierSnapshot =
                event.actorIdentifier();
        entry.actionCode =
                "PROMOTION_ELIGIBLE_PLANS_CHANGED";
        entry.resourceType =
                "PROMOTION";
        entry.resourceId =
                event.promotionId();
        entry.resourceCodeSnapshot =
                event.promotionCode();
        entry.summary =
                "Promotion eligible plans changed.";
        entry.metadata =
                Map.of(
                        "eligiblePlanIds",
                        event.eligiblePlanIds()
                                .stream()
                                .map(UUID::toString)
                                .sorted()
                                .toList(),
                        "eligiblePlanCount",
                        event.eligiblePlanIds()
                                .size());
        entry.occurredAt =
                event.occurredAt();

        return entry;
    }

    static AuditEntryJpaEntity from(
            MembershipCreated event) {

        AuditEntryJpaEntity entry =
                new AuditEntryJpaEntity();

        entry.id =
                UUID.randomUUID();

        entry.actorUserId =
                event.actorUserId();

        entry.actorIdentifierSnapshot =
                event.actorIdentifier();

        entry.actionCode =
                "MEMBERSHIP_CREATED";

        entry.resourceType =
                "MEMBERSHIP";

        entry.resourceId =
                event.membershipId();

        entry.resourceCodeSnapshot =
                event.membershipCode();

        entry.summary =
                "Membership created.";

        entry.metadata =
                membershipCreatedMetadata(event);

        entry.occurredAt =
                event.occurredAt();

        return entry;
    }

    static AuditEntryJpaEntity from(
            MembershipRenewed event) {

        AuditEntryJpaEntity entry =
                new AuditEntryJpaEntity();

        entry.id =
                UUID.randomUUID();

        entry.actorUserId =
                event.actorUserId();

        entry.actorIdentifierSnapshot =
                event.actorIdentifier();

        entry.actionCode =
                "MEMBERSHIP_RENEWED";

        entry.resourceType =
                "MEMBERSHIP";

        entry.resourceId =
                event.membershipId();

        entry.resourceCodeSnapshot =
                event.membershipCode();

        entry.summary =
                "Membership renewed.";

        entry.metadata =
                membershipRenewedMetadata(
                        event);

        entry.occurredAt =
                event.occurredAt();

        return entry;
    }

    private static Map<String, Object>
    membershipCreatedMetadata(
            MembershipCreated event) {

        Map<String, Object> metadata =
                new java.util.LinkedHashMap<>();

        metadata.put(
                "clientId",
                event.clientId().toString());

        metadata.put(
                "membershipPeriodId",
                event.membershipPeriodId()
                        .toString());

        metadata.put(
                "membershipPlanId",
                event.membershipPlanId()
                        .toString());

        if (event.promotionId() != null) {
            metadata.put(
                    "promotionId",
                    event.promotionId()
                            .toString());
        }

        metadata.put(
                "listPrice",
                event.listPrice()
                        .toPlainString());

        metadata.put(
                "discountAmount",
                event.discountAmount()
                        .toPlainString());

        metadata.put(
                "finalPrice",
                event.finalPrice()
                        .toPlainString());

        metadata.put(
                "currency",
                event.currency());

        metadata.put(
                "startsOn",
                event.startsOn()
                        .toString());

        metadata.put(
                "effectiveEndsOn",
                event.effectiveEndsOn()
                        .toString());

        return Map.copyOf(metadata);
    }

    private static Map<String, Object>
    membershipRenewedMetadata(
            MembershipRenewed event) {

        Map<String, Object> metadata =
                new java.util.LinkedHashMap<>();

        metadata.put(
                "clientId",
                event.clientId()
                        .toString());

        metadata.put(
                "membershipPeriodId",
                event.membershipPeriodId()
                        .toString());

        metadata.put(
                "periodNumber",
                event.periodNumber());

        metadata.put(
                "membershipPlanId",
                event.membershipPlanId()
                        .toString());

        if (event.promotionId() != null) {
            metadata.put(
                    "promotionId",
                    event.promotionId()
                            .toString());
        }

        metadata.put(
                "listPrice",
                event.listPrice()
                        .toPlainString());

        metadata.put(
                "discountAmount",
                event.discountAmount()
                        .toPlainString());

        metadata.put(
                "finalPrice",
                event.finalPrice()
                        .toPlainString());

        metadata.put(
                "currency",
                event.currency());

        metadata.put(
                "startsOn",
                event.startsOn()
                        .toString());

        metadata.put(
                "effectiveEndsOn",
                event.effectiveEndsOn()
                        .toString());

        metadata.put(
                "previousStatus",
                event.previousStatus()
                        .name());

        metadata.put(
                "resultingStatus",
                event.resultingStatus()
                        .name());

        metadata.put(
                "statusChanged",
                event.previousStatus()
                        != event.resultingStatus());

        return Map.copyOf(metadata);
    }

    static AuditEntryJpaEntity from(
            MembershipFrozen event) {

        AuditEntryJpaEntity entry =
                new AuditEntryJpaEntity();

        entry.id = UUID.randomUUID();
        entry.actorUserId = event.actorUserId();
        entry.actorIdentifierSnapshot =
                event.actorIdentifier();
        entry.actionCode =
                "MEMBERSHIP_FROZEN";
        entry.resourceType =
                "MEMBERSHIP";
        entry.resourceId =
                event.membershipId();
        entry.resourceCodeSnapshot =
                event.membershipCode();
        entry.summary =
                "Membership frozen.";
        entry.metadata =
                membershipFrozenMetadata(event);
        entry.occurredAt =
                event.occurredAt();

        return entry;
    }

    static AuditEntryJpaEntity from(
            MembershipReactivated event) {

        AuditEntryJpaEntity entry =
                new AuditEntryJpaEntity();

        entry.id = UUID.randomUUID();
        entry.actorUserId = event.actorUserId();
        entry.actorIdentifierSnapshot =
                event.actorIdentifier();
        entry.actionCode =
                "MEMBERSHIP_REACTIVATED";
        entry.resourceType =
                "MEMBERSHIP";
        entry.resourceId =
                event.membershipId();
        entry.resourceCodeSnapshot =
                event.membershipCode();
        entry.summary =
                "Membership reactivated.";
        entry.metadata =
                membershipReactivatedMetadata(event);
        entry.occurredAt =
                event.occurredAt();

        return entry;
    }

    private static Map<String, Object>
    membershipFrozenMetadata(
            MembershipFrozen event) {

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "clientId",
                event.clientId());

        metadata.put(
                "membershipPeriodId",
                event.membershipPeriodId());

        metadata.put(
                "startsOn",
                event.startsOn().toString());

        metadata.put(
                "plannedEndsOn",
                event.plannedEndsOn().toString());

        metadata.put(
                "reason",
                event.reason());

        metadata.put(
                "previousStatus",
                event.previousStatus().name());

        metadata.put(
                "resultingStatus",
                event.resultingStatus().name());

        metadata.put(
                "statusChanged",
                true);

        return metadata;
    }

    private static Map<String, Object>
    membershipReactivatedMetadata(
            MembershipReactivated event) {

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "clientId",
                event.clientId());

        metadata.put(
                "membershipPeriodId",
                event.membershipPeriodId());

        metadata.put(
                "membershipFreezeId",
                event.membershipFreezeId());

        metadata.put(
                "freezeStartsOn",
                event.freezeStartsOn().toString());

        metadata.put(
                "plannedEndsOn",
                event.plannedEndsOn().toString());

        metadata.put(
                "reactivatedOn",
                event.reactivatedOn().toString());

        metadata.put(
                "reason",
                event.reason());

        metadata.put(
                "previousStatus",
                event.previousStatus().name());

        metadata.put(
                "resultingStatus",
                event.resultingStatus().name());

        metadata.put(
                "statusChanged",
                true);

        return metadata;
    }

    static AuditEntryJpaEntity from(
            MembershipCancelled event) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "Membership cancelled event "
                            + "must be provided.");
        }

        AuditEntryJpaEntity entry =
                new AuditEntryJpaEntity();

        entry.id =
                UUID.randomUUID();

        entry.actorUserId =
                event.actorUserId();

        entry.actorIdentifierSnapshot =
                event.actorIdentifier();

        entry.actionCode =
                "MEMBERSHIP_CANCELLED";

        entry.resourceType =
                "MEMBERSHIP";

        entry.resourceId =
                event.membershipId();

        entry.resourceCodeSnapshot =
                event.membershipCode();

        entry.summary =
                "Membership cancelled.";

        entry.metadata =
                membershipCancelledMetadata(
                        event);

        entry.occurredAt =
                event.occurredAt();

        return entry;
    }

    private static Map<String, Object>
    membershipCancelledMetadata(
            MembershipCancelled event) {

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "clientId",
                event.clientId());

        metadata.put(
                "membershipPeriodId",
                event.membershipPeriodId());

        metadata.put(
                "cancelledOn",
                event.cancelledOn().toString());

        metadata.put(
                "reason",
                event.reason());

        metadata.put(
                "previousStatus",
                event.previousStatus().name());

        metadata.put(
                "resultingStatus",
                event.resultingStatus().name());

        metadata.put(
                "statusChanged",
                event.previousStatus()
                        != event.resultingStatus());

        metadata.put(
                "closedOpenFreeze",
                event.closedOpenFreeze());

        return metadata;
    }

    UUID id() {
        return id;
    }

    UUID actorUserId() {
        return actorUserId;
    }

    String actorIdentifierSnapshot() {
        return actorIdentifierSnapshot;
    }

    String actionCode() {
        return actionCode;
    }

    String resourceType() {
        return resourceType;
    }

    UUID resourceId() {
        return resourceId;
    }

    String resourceCodeSnapshot() {
        return resourceCodeSnapshot;
    }

    String summary() {
        return summary;
    }

    Map<String, Object> metadata() {
        return metadata;
    }

    Instant occurredAt() {
        return occurredAt;
    }
}
