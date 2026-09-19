package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.audit.AuditExportCompleted;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialIssued;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialReplaced;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialRevoked;
import io.github.guillermodubon.coachgym.client.ClientRegistered;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyChanged;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryActivatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryCreatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDeactivatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryUpdatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentRegisteredEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatusChangedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentUpdatedEvent;
import io.github.guillermodubon.coachgym.maintenance.*;
import io.github.guillermodubon.coachgym.membership.*;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryLifecycleEvent;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptCreated;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptProviderStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderEventAcknowledged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderPaymentConfirmed;
import io.github.guillermodubon.coachgym.payment.PaymentRegistered;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptGenerated;
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

    static AuditEntryJpaEntity from(AccessPaymentPolicyChanged event) {
        if (event == null) {
            throw new IllegalArgumentException(
                    "Access payment policy event must be provided.");
        }

        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();
        entry.id = UUID.randomUUID();
        entry.actorUserId = event.actorUserId();
        entry.actorIdentifierSnapshot = event.actorIdentifier();
        entry.actionCode = "ACCESS_PAYMENT_POLICY_CHANGED";
        entry.resourceType = "SETTINGS";
        entry.resourceId = AccessPaymentPolicyChanged.SETTINGS_RESOURCE_ID;
        entry.resourceCodeSnapshot = "GYM_SETTINGS";
        entry.summary = "Access payment policy changed.";
        entry.metadata = Map.of(
                "previousValue", event.previousValue(),
                "newValue", event.newValue());
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

    static AuditEntryJpaEntity from(AuditExportCompleted event) {
        if (event == null) {
            throw new IllegalArgumentException(
                    "Audit export completed event must be provided.");
        }

        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();
        entry.id = UUID.randomUUID();
        entry.actorUserId = event.actorUserId();
        entry.actorIdentifierSnapshot = event.actorIdentifier();
        entry.actionCode = AuditExportCompleted.ACTION_CODE;
        entry.resourceType = AuditExportCompleted.RESOURCE_TYPE;
        entry.resourceId = event.exportId();
        entry.resourceCodeSnapshot = event.format();
        entry.summary = "Audit entries exported.";

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("occurredFrom", event.occurredFrom().toString());
        metadata.put("occurredUntil", event.occurredUntil().toString());
        metadata.put("filtersPresent", event.filterSummary());
        metadata.put("sortField", event.sortField().name());
        metadata.put("sortDirection", event.sortDirection().name());
        metadata.put("rowCount", event.rowCount());
        metadata.put("maximumRows", event.maximumRows());
        metadata.put("format", event.format());
        entry.metadata = Map.copyOf(metadata);
        entry.occurredAt = event.occurredAt();
        return entry;
    }

    static AuditEntryJpaEntity from(PaymentAttemptCreated event) {
        AuditEntryJpaEntity entry = attemptEntry(
                event.paymentAttemptId(), event.createdByUserId(),
                event.actorIdentifier(), event.occurredAt());
        entry.actionCode = "PAYMENT_ATTEMPT_CREATED";
        entry.summary = "Payment attempt created.";

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("clientId", event.clientId().toString());
        metadata.put("membershipId", event.membershipId().toString());
        metadata.put("membershipPeriodId", event.membershipPeriodId().toString());
        metadata.put("provider", event.provider().name());
        metadata.put("expectedAmount", event.expectedAmount().toPlainString());
        metadata.put("currency", event.currency());
        entry.metadata = Map.copyOf(metadata);
        return entry;
    }

    static AuditEntryJpaEntity from(PaymentAttemptStatusChanged event) {
        AuditEntryJpaEntity entry = attemptEntry(
                event.paymentAttemptId(), event.initiatedByUserId(),
                null, event.occurredAt());
        entry.actionCode = statusChangeAction(event.currentStatus());
        entry.summary = statusChangeSummary(event.currentStatus());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", event.provider().name());
        metadata.put("previousStatus", event.previousStatus().name());
        metadata.put("newStatus", event.currentStatus().name());
        if (event.failureCode() != null) {
            metadata.put("failureCode", event.failureCode().name());
        }
        metadata.put("confirmedPaymentPresent", event.confirmedPaymentId() != null);
        entry.metadata = Map.copyOf(metadata);
        return entry;
    }

    static AuditEntryJpaEntity from(PaymentAttemptProviderStatusChanged event) {
        AuditEntryJpaEntity entry = attemptEntry(
                event.paymentAttemptId(), null, null, event.occurredAt());
        boolean succeeded = event.currentStatus() == PaymentAttemptStatus.SUCCEEDED;
        entry.actionCode = succeeded
                ? "PAYMENT_ATTEMPT_PROVIDER_SUCCESS"
                : "PAYMENT_ATTEMPT_PROVIDER_FAILURE";
        entry.summary = succeeded
                ? "Payment provider success verified."
                : "Payment provider failure verified.";

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", event.provider().name());
        metadata.put("previousStatus", event.previousStatus().name());
        metadata.put("newStatus", event.currentStatus().name());
        if (event.failureCode() != null) {
            metadata.put("failureCode", event.failureCode().name());
        }
        metadata.put("providerEventReferencePresent", event.providerEventReferencePresent());
        metadata.put("confirmedPaymentPresent", event.confirmedPaymentId() != null);
        entry.metadata = Map.copyOf(metadata);
        return entry;
    }

    static AuditEntryJpaEntity from(PaymentProviderEventAcknowledged event) {
        AuditEntryJpaEntity entry = attemptEntry(
                event.paymentAttemptId(), null, null, event.occurredAt());
        entry.actionCode = "PAYMENT_PROVIDER_EVENT_DUPLICATE_ACKNOWLEDGED";
        entry.summary = "Duplicate payment provider event acknowledged.";

        entry.metadata = Map.of(
                "provider", event.provider().name(),
                "eventType", event.eventType(),
                "processingResult", event.processingResult(),
                "duplicate", true);
        return entry;
    }

    static AuditEntryJpaEntity from(PaymentProviderPaymentConfirmed event) {
        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();
        entry.id = UUID.randomUUID();
        entry.actorUserId = null;
        entry.actorIdentifierSnapshot = null;
        entry.actionCode = "PAYMENT_PROVIDER_PAYMENT_CONFIRMED";
        entry.resourceType = "PAYMENT";
        entry.resourceId = event.paymentId();
        entry.resourceCodeSnapshot = null;
        entry.summary = "Payment materialized from verified provider confirmation.";
        entry.metadata = Map.of(
                "paymentAttemptId", event.paymentAttemptId().toString(),
                "provider", event.provider().name(),
                "amount", event.amount().toPlainString(),
                "currency", event.currency());
        entry.occurredAt = event.occurredAt();
        return entry;
    }

    static AuditEntryJpaEntity from(PaymentReceiptGenerated event) {
        if (event == null) {
            throw new IllegalArgumentException(
                    "Payment receipt generated event must be provided.");
        }

        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();
        entry.id = UUID.randomUUID();
        entry.actorUserId = event.generatedByUserId();
        entry.actorIdentifierSnapshot = event.actorIdentifier();
        entry.actionCode = "PAYMENT_RECEIPT_GENERATED";
        entry.resourceType = "PAYMENT_RECEIPT";
        entry.resourceId = event.receiptId();
        entry.resourceCodeSnapshot = event.receiptNumber();
        entry.summary = "Payment receipt generated.";
        entry.metadata = Map.of(
                "paymentId", event.paymentId().toString(),
                "paymentCode", event.paymentCode(),
                "amount", event.amount().toPlainString(),
                "currency", event.currency(),
                "testMode", event.testMode());
        entry.occurredAt = event.occurredAt();
        return entry;
    }

    static AuditEntryJpaEntity from(EmailDeliveryLifecycleEvent event) {
        if (event == null) {
            throw new IllegalArgumentException(
                    "Email delivery lifecycle event must be provided.");
        }

        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();
        entry.id = UUID.randomUUID();
        entry.actorUserId = event.actorUserId();
        entry.actorIdentifierSnapshot = event.actorIdentifier() != null
                ? event.actorIdentifier()
                : "staff";
        entry.actionCode = event.auditActionCode();
        entry.resourceType = "EMAIL_DELIVERY";
        entry.resourceId = event.deliveryId();
        entry.resourceCodeSnapshot = event.deliveryType().name();
        entry.summary = switch (entry.actionCode) {
            case "EMAIL_DELIVERY_RETRIED" -> "Transactional email delivery retried.";
            case "EMAIL_DELIVERY_SENT" -> "Transactional email delivery sent.";
            default -> "Transactional email delivery failed.";
        };

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("deliveryType", event.deliveryType().name());
        metadata.put("status", event.currentStatus().name());
        metadata.put("attemptNumber", event.attemptNumber());
        metadata.put("attemptResult", event.attemptResult().name());
        metadata.put("previousStatus", event.previousStatus().name());
        if (event.sourceResourceId() != null) {
            metadata.put("sourceResourceId", event.sourceResourceId().toString());
        }
        if (event.clientId() != null) {
            metadata.put("clientId", event.clientId().toString());
        }
        if (event.maskedRecipient() != null) {
            metadata.put("maskedRecipient", event.maskedRecipient());
        }
        if (event.failureCode() != null) {
            metadata.put("failureCode", event.failureCode().name());
        }
        entry.metadata = Map.copyOf(metadata);
        entry.occurredAt = event.occurredAt();
        return entry;
    }

    private static AuditEntryJpaEntity attemptEntry(
            UUID attemptId, UUID actorUserId, String actorIdentifier, Instant occurredAt) {
        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();
        entry.id = UUID.randomUUID();
        entry.actorUserId = actorUserId;
        entry.actorIdentifierSnapshot = actorIdentifier != null
                ? actorIdentifier
                : actorUserId == null ? null : "staff";
        entry.resourceType = "PAYMENT_ATTEMPT";
        entry.resourceId = attemptId;
        entry.resourceCodeSnapshot = null;
        entry.occurredAt = occurredAt;
        return entry;
    }

    private static String statusChangeAction(PaymentAttemptStatus status) {
        return switch (status) {
            case PROCESSING -> "PAYMENT_ATTEMPT_PROCESSING";
            case CANCELLED -> "PAYMENT_ATTEMPT_CANCELLED";
            case FAILED -> "PAYMENT_ATTEMPT_FAILED";
            default -> "PAYMENT_ATTEMPT_STATUS_CHANGED";
        };
    }

    private static String statusChangeSummary(PaymentAttemptStatus status) {
        return switch (status) {
            case PROCESSING -> "Payment attempt moved to processing.";
            case CANCELLED -> "Payment attempt cancelled by staff.";
            case FAILED -> "Payment attempt failed before provider confirmation.";
            default -> "Payment attempt status changed.";
        };
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

    static AuditEntryJpaEntity from(AccessCredentialIssued event) {
        if (event == null) {
            throw new IllegalArgumentException(
                    "Access credential issued event must be provided.");
        }

        AuditEntryJpaEntity entry = accessCredentialEntry(
                event.credentialId(), event.credentialCode(), event.actorUserId(),
                event.actorIdentifier(), event.occurredAt());
        entry.actionCode = "ACCESS_CREDENTIAL_ISSUED";
        entry.summary = "Client access credential issued.";
        entry.metadata = Map.of(
                "clientId", event.clientId().toString(),
                "newStatus", "ACTIVE",
                "payloadVersion", event.payloadVersion(),
                "tokenSchemeVersion", event.tokenSchemeVersion());
        return entry;
    }

    static AuditEntryJpaEntity from(AccessCredentialRevoked event) {
        if (event == null) {
            throw new IllegalArgumentException(
                    "Access credential revoked event must be provided.");
        }

        AuditEntryJpaEntity entry = accessCredentialEntry(
                event.credentialId(), event.credentialCode(), event.actorUserId(),
                event.actorIdentifier(), event.occurredAt());
        entry.actionCode = "ACCESS_CREDENTIAL_REVOKED";
        entry.summary = "Client access credential revoked.";
        entry.metadata = Map.of(
                "clientId", event.clientId().toString(),
                "previousStatus", event.previousStatus().name(),
                "newStatus", event.newStatus().name(),
                "reasonPresent", event.reasonPresent());
        return entry;
    }

    static AuditEntryJpaEntity from(AccessCredentialReplaced event) {
        if (event == null) {
            throw new IllegalArgumentException(
                    "Access credential replaced event must be provided.");
        }

        AuditEntryJpaEntity entry = accessCredentialEntry(
                event.previousCredentialId(), null, event.actorUserId(),
                event.actorIdentifier(), event.occurredAt());
        entry.actionCode = "ACCESS_CREDENTIAL_REPLACED";
        entry.summary = "Client access credential replaced.";
        entry.metadata = Map.of(
                "clientId", event.clientId().toString(),
                "previousStatus", event.previousStatus().name(),
                "newStatus", event.replacementStatus().name(),
                "replacementCredentialId", event.replacementCredentialId().toString(),
                "reasonPresent", event.reasonPresent());
        return entry;
    }

    private static AuditEntryJpaEntity accessCredentialEntry(
            UUID credentialId,
            String credentialCode,
            UUID actorUserId,
            String actorIdentifier,
            Instant occurredAt) {
        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();
        entry.id = UUID.randomUUID();
        entry.actorUserId = actorUserId;
        entry.actorIdentifierSnapshot = actorIdentifier;
        entry.resourceType = "ACCESS_CREDENTIAL";
        entry.resourceId = credentialId;
        entry.resourceCodeSnapshot = credentialCode;
        entry.occurredAt = occurredAt;
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
                "identificationSource",
                event.presentedIdentifierType());

        metadata.put(
                "result",
                event.result().name());

        metadata.put(
                "reasonCode",
                event.reasonCode().name());

        metadata.put(
                "duplicate",
                event.duplicate());

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

        if (event.accessCredentialId() != null) {
            metadata.put(
                    "accessCredentialId",
                    event.accessCredentialId().toString());
        }

        return Map.copyOf(metadata);
    }

    static AuditEntryJpaEntity from(IncidentReportedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Incident reported event must be provided.");
        }
        AuditEntryJpaEntity entry = incidentEntry(
                event.incidentId(), event.incidentCode(), event.actorUserId(),
                event.actorIdentifier(), event.occurredAt());
        entry.actionCode = "INCIDENT_REPORTED";
        entry.summary = "Equipment incident reported.";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("equipmentId", event.equipmentId().toString());
        if (event.equipmentCode() != null) {
            metadata.put("equipmentCode", event.equipmentCode());
        }
        metadata.put("priority", event.priority().name());
        metadata.put("takenOutOfService", event.takenOutOfService());
        entry.metadata = Map.copyOf(metadata);
        return entry;
    }

    static AuditEntryJpaEntity from(IncidentInvestigationStartedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Incident investigation event must be provided.");
        }
        AuditEntryJpaEntity entry = incidentEntry(
                event.incidentId(), event.incidentCode(), event.actorUserId(),
                event.actorIdentifier(), event.occurredAt());
        entry.actionCode = "INCIDENT_INVESTIGATION_STARTED";
        entry.summary = "Incident investigation started.";
        entry.metadata = Map.of("equipmentId", event.equipmentId().toString());
        return entry;
    }

    static AuditEntryJpaEntity from(IncidentPriorityChangedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Incident priority event must be provided.");
        }
        AuditEntryJpaEntity entry = incidentEntry(
                event.incidentId(), event.incidentCode(), event.actorUserId(),
                event.actorIdentifier(), event.occurredAt());
        entry.actionCode = "INCIDENT_PRIORITY_CHANGED";
        entry.summary = "Incident priority changed.";
        entry.metadata = Map.of(
                "previousPriority", event.previousPriority().name(),
                "newPriority", event.newPriority().name());
        return entry;
    }

    static AuditEntryJpaEntity from(IncidentResolvedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Incident resolved event must be provided.");
        }
        AuditEntryJpaEntity entry = incidentEntry(
                event.incidentId(), event.incidentCode(), event.actorUserId(),
                event.actorIdentifier(), event.occurredAt());
        entry.actionCode = "INCIDENT_RESOLVED";
        entry.summary = "Equipment incident resolved.";
        entry.metadata = Map.of("equipmentId", event.equipmentId().toString());
        return entry;
    }

    static AuditEntryJpaEntity from(
            MaintenanceScheduledEvent event) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "Maintenance scheduled event must be provided.");
        }

        AuditEntryJpaEntity entry =
                maintenanceEntry(
                        event.maintenanceId(),
                        event.maintenanceCode(),
                        event.actorUserId(),
                        event.actorIdentifier(),
                        event.occurredAt());

        entry.actionCode =
                "MAINTENANCE_SCHEDULED";

        entry.summary =
                "Maintenance work order scheduled.";

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "equipmentId",
                event.equipmentId().toString());

        if (event.equipmentCode() != null) {
            metadata.put(
                    "equipmentCode",
                    event.equipmentCode());
        }

        if (event.incidentId() != null) {
            metadata.put(
                    "incidentId",
                    event.incidentId().toString());
        }

        metadata.put(
                "maintenanceType",
                event.maintenanceType().name());

        metadata.put(
                "scheduledOn",
                event.scheduledOn().toString());

        if (event.estimatedCost() != null) {
            metadata.put(
                    "estimatedCost",
                    event.estimatedCost());
        }

        metadata.put(
                "currency",
                event.currency());

        entry.metadata =
                Map.copyOf(metadata);

        return entry;
    }

    static AuditEntryJpaEntity from(
            MaintenanceUpdatedEvent event) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "Maintenance updated event must be provided.");
        }

        AuditEntryJpaEntity entry =
                maintenanceEntry(
                        event.maintenanceId(),
                        event.maintenanceCode(),
                        event.actorUserId(),
                        event.actorIdentifier(),
                        event.occurredAt());

        entry.actionCode =
                "MAINTENANCE_UPDATED";

        entry.summary =
                "Scheduled maintenance work order updated.";

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "equipmentId",
                event.equipmentId().toString());

        if (event.incidentId() != null) {
            metadata.put(
                    "incidentId",
                    event.incidentId().toString());
        }

        metadata.put(
                "scheduledOn",
                event.scheduledOn().toString());

        if (event.estimatedCost() != null) {
            metadata.put(
                    "estimatedCost",
                    event.estimatedCost());
        }

        metadata.put(
                "currency",
                event.currency());

        entry.metadata =
                Map.copyOf(metadata);

        return entry;
    }

    private static AuditEntryJpaEntity incidentEntry(
            UUID incidentId, String incidentCode, UUID actorUserId,
            String actorIdentifier, Instant occurredAt) {
        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();
        entry.id = UUID.randomUUID();
        entry.actorUserId = actorUserId;
        entry.actorIdentifierSnapshot = actorIdentifier;
        entry.resourceType = "INCIDENT";
        entry.resourceId = incidentId;
        entry.resourceCodeSnapshot = incidentCode;
        entry.occurredAt = occurredAt;
        return entry;
    }

    private static AuditEntryJpaEntity maintenanceEntry(
            UUID maintenanceId,
            String maintenanceCode,
            UUID actorUserId,
            String actorIdentifier,
            Instant occurredAt) {

        AuditEntryJpaEntity entry =
                new AuditEntryJpaEntity();

        entry.id = UUID.randomUUID();
        entry.actorUserId = actorUserId;
        entry.actorIdentifierSnapshot =
                actorIdentifier;
        entry.resourceType = "MAINTENANCE";
        entry.resourceId = maintenanceId;
        entry.resourceCodeSnapshot =
                maintenanceCode;
        entry.occurredAt = occurredAt;

        return entry;
    }

    static AuditEntryJpaEntity from(
            MaintenanceCompletedEvent event) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "Maintenance completed event must be provided.");
        }

        AuditEntryJpaEntity entry =
                maintenanceEntry(
                        event.maintenanceId(),
                        event.maintenanceCode(),
                        event.actorUserId(),
                        event.actorIdentifier(),
                        event.occurredAt());

        entry.actionCode =
                "MAINTENANCE_COMPLETED";

        entry.summary =
                "Maintenance work order completed.";

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "equipmentId",
                event.equipmentId().toString());

        metadata.put(
                "previousStatus",
                event.previousStatus().name());

        metadata.put(
                "newStatus",
                event.newStatus().name());

        metadata.put(
                "equipmentOutcome",
                event.equipmentOutcome().name());

        if (event.actualCost() != null) {
            metadata.put(
                    "actualCost",
                    event.actualCost());
        }

        metadata.put(
                "currency",
                event.currency());

        entry.metadata =
                Map.copyOf(metadata);

        return entry;
    }

    static AuditEntryJpaEntity from(
            MaintenanceCancelledEvent event) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "Maintenance cancelled event must be provided.");
        }

        AuditEntryJpaEntity entry =
                maintenanceEntry(
                        event.maintenanceId(),
                        event.maintenanceCode(),
                        event.actorUserId(),
                        event.actorIdentifier(),
                        event.occurredAt());

        entry.actionCode =
                "MAINTENANCE_CANCELLED";

        entry.summary =
                "Maintenance work order cancelled.";

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "equipmentId",
                event.equipmentId().toString());

        metadata.put(
                "previousStatus",
                event.previousStatus().name());

        metadata.put(
                "newStatus",
                event.newStatus().name());

        if (event.equipmentOutcome() != null) {
            metadata.put(
                    "equipmentOutcome",
                    event.equipmentOutcome().name());
        }

        entry.metadata =
                Map.copyOf(metadata);

        return entry;
    }

    static AuditEntryJpaEntity from(
            MaintenanceStartedEvent event) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "Maintenance started event must be provided.");
        }

        AuditEntryJpaEntity entry =
                maintenanceEntry(
                        event.maintenanceId(),
                        event.maintenanceCode(),
                        event.actorUserId(),
                        event.actorIdentifier(),
                        event.occurredAt());

        entry.actionCode = "MAINTENANCE_STARTED";
        entry.summary = "Maintenance work started.";

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "equipmentId",
                event.equipmentId().toString());

        if (event.equipmentCode() != null) {
            metadata.put(
                    "equipmentCode",
                    event.equipmentCode());
        }

        if (event.incidentId() != null) {
            metadata.put(
                    "incidentId",
                    event.incidentId().toString());
        }

        metadata.put(
                "previousStatus",
                event.previousStatus().name());

        metadata.put(
                "newStatus",
                event.newStatus().name());

        entry.metadata = Map.copyOf(metadata);

        return entry;
    }
}
