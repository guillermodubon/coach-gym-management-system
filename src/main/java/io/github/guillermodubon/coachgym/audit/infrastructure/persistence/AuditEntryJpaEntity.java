package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.client.ClientRegistered;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryActivatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryCreatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDeactivatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryUpdatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentRegisteredEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatusChangedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentUpdatedEvent;
import io.github.guillermodubon.coachgym.membership.*;
import io.github.guillermodubon.coachgym.payment.PaymentRegistered;
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

    public static AuditEntryJpaEntity from(EquipmentRegisteredEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Equipment registered event must be provided.");
        }

        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();

        entry.id = UUID.randomUUID();
        entry.actorUserId = event.actorUserId();
        entry.actorIdentifierSnapshot = event.actorIdentifier();
        entry.actionCode = "EQUIPMENT_REGISTERED";
        entry.resourceType = "EQUIPMENT";
        entry.resourceId = event.equipmentId();
        entry.resourceCodeSnapshot = event.equipmentCode();
        entry.summary = "Equipment registered.";

        entry.metadata = Map.of(
                "categoryId",
                event.categoryId().toString()
        );

        entry.occurredAt = event.occurredAt();

        return entry;
    }


    static AuditEntryJpaEntity from(EquipmentStatusChangedEvent event) {
        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();
        entry.id = UUID.randomUUID();
        entry.actorUserId = event.actorUserId();
        entry.actorIdentifierSnapshot = event.actorIdentifier();
        // Action code encodes the new status for direct query by action.
        entry.actionCode = "EQUIPMENT_STATUS_CHANGED_TO_" + event.newStatus().name();
        entry.resourceType = "EQUIPMENT";
        entry.resourceId = event.equipmentId();
        entry.resourceCodeSnapshot = event.equipmentCode();
        entry.summary = "Equipment status changed to " + event.newStatus().name() + ".";
        LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
        if (event.previousStatus() != null) {
            meta.put("previousStatus", event.previousStatus().name());
        }
        meta.put("newStatus", event.newStatus().name());
        meta.put("reason", event.reason());
        entry.metadata = Map.copyOf(meta);
        entry.occurredAt = event.occurredAt();
        return entry;
    }

    static AuditEntryJpaEntity from(EquipmentUpdatedEvent event) {
        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();
        entry.id = UUID.randomUUID();
        entry.actorUserId = event.actorUserId();
        entry.actorIdentifierSnapshot = event.actorIdentifier();
        entry.actionCode = "EQUIPMENT_UPDATED";
        entry.resourceType = "EQUIPMENT";
        entry.resourceId = event.equipmentId();
        entry.resourceCodeSnapshot = event.equipmentCode();
        entry.summary = "Equipment updated.";
        entry.metadata = Map.of();
        entry.occurredAt = event.occurredAt();
        return entry;
    }

    static AuditEntryJpaEntity from(
            EquipmentCategoryCreatedEvent event) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "Equipment category created event "
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
                "EQUIPMENT_CATEGORY_CREATED";

        entry.resourceType =
                "EQUIPMENT_CATEGORY";

        entry.resourceId =
                event.categoryId();

        entry.resourceCodeSnapshot =
                event.categoryName();

        entry.summary =
                "Equipment category created.";

        entry.metadata =
                Map.of();

        entry.occurredAt =
                event.occurredAt();

        return entry;
    }

    static AuditEntryJpaEntity from(
            EquipmentCategoryUpdatedEvent event) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "Equipment category updated event "
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
                "EQUIPMENT_CATEGORY_UPDATED";

        entry.resourceType =
                "EQUIPMENT_CATEGORY";

        entry.resourceId =
                event.categoryId();

        entry.resourceCodeSnapshot =
                event.categoryName();

        entry.summary =
                "Equipment category updated.";

        entry.metadata =
                Map.of();

        entry.occurredAt =
                event.occurredAt();

        return entry;
    }

    static AuditEntryJpaEntity from(
            EquipmentCategoryActivatedEvent event) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "Equipment category activated event "
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
                "EQUIPMENT_CATEGORY_ACTIVATED";

        entry.resourceType =
                "EQUIPMENT_CATEGORY";

        entry.resourceId =
                event.categoryId();

        entry.resourceCodeSnapshot =
                event.categoryName();

        entry.summary =
                "Equipment category activated.";

        entry.metadata =
                Map.of();

        entry.occurredAt =
                event.occurredAt();

        return entry;
    }

    static AuditEntryJpaEntity from(
            EquipmentCategoryDeactivatedEvent event) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "Equipment category deactivated event "
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
                "EQUIPMENT_CATEGORY_DEACTIVATED";

        entry.resourceType =
                "EQUIPMENT_CATEGORY";

        entry.resourceId =
                event.categoryId();

        entry.resourceCodeSnapshot =
                event.categoryName();

        entry.summary =
                "Equipment category deactivated.";

        entry.metadata =
                Map.of();

        entry.occurredAt =
                event.occurredAt();

        return entry;
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

    static AuditEntryJpaEntity from(
            PaymentRegistered event) {

        AuditEntryJpaEntity entry =
                new AuditEntryJpaEntity();

        entry.id =
                UUID.randomUUID();

        entry.actorUserId =
                event.actorUserId();

        entry.actorIdentifierSnapshot =
                event.actorIdentifier();

        entry.actionCode =
                "PAYMENT_REGISTERED";

        entry.resourceType =
                "PAYMENT";

        entry.resourceId =
                event.paymentId();

        entry.resourceCodeSnapshot =
                event.paymentCode();

        entry.summary =
                "Payment registered.";

        entry.metadata =
                paymentRegisteredMetadata(event);

        entry.occurredAt =
                event.occurredAt();

        return entry;
    }

    private static Map<String, Object>
    paymentRegisteredMetadata(
            PaymentRegistered event) {

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "clientId",
                event.clientId().toString());

        metadata.put(
                "membershipId",
                event.membershipId().toString());

        metadata.put(
                "membershipPeriodId",
                event.membershipPeriodId().toString());

        metadata.put(
                "amount",
                event.amount().toPlainString());

        metadata.put(
                "currency",
                event.currency());

        metadata.put(
                "paymentMethod",
                event.paymentMethod().name());

        metadata.put(
                "paidAt",
                event.paidAt().toString());

        metadata.put(
                "resultingStatus",
                event.resultingStatus().name());

        // Store only a boolean flag — never the reference value itself.
        metadata.put(
                "hasExternalReference",
                event.hasExternalReference());

        return Map.copyOf(metadata);
    }

    static AuditEntryJpaEntity from(
            AccessAttemptRecorded event) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "Access attempt event must be provided.");
        }

        if (event.result() != AccessResult.DENIED) {
            throw new IllegalArgumentException(
                    "Only denied access attempts may create audit entries.");
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
                "ACCESS_DENIED";

        entry.resourceType =
                "ACCESS_RECORD";

        entry.resourceId =
                event.accessRecordId();

        entry.resourceCodeSnapshot =
                event.presentedIdentifier();

        entry.summary =
                "Gym access denied.";

        entry.metadata =
                deniedAccessMetadata(event);

        entry.occurredAt =
                event.occurredAt();

        return entry;
    }

    private static Map<String, Object>
    deniedAccessMetadata(
            AccessAttemptRecorded event) {

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "presentedIdentifierType",
                event.presentedIdentifierType());

        metadata.put(
                "result",
                event.result().name());

        metadata.put(
                "reasonCode",
                event.reasonCode().name());

        metadata.put(
                "checkedInAt",
                event.checkedInAt().toString());

        if (event.clientId() != null) {
            metadata.put(
                    "clientId",
                    event.clientId().toString());
        }

        if (event.membershipId() != null) {
            metadata.put(
                    "membershipId",
                    event.membershipId().toString());
        }

        return Map.copyOf(metadata);
    }
}
