package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
import io.github.guillermodubon.coachgym.audit.application.AuditEntryStore;
import io.github.guillermodubon.coachgym.client.ClientRegistered;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryActivatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryCreatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDeactivatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryUpdatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentRegisteredEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatusChangedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentUpdatedEvent;
import io.github.guillermodubon.coachgym.maintenance.*;
import io.github.guillermodubon.coachgym.membership.MembershipCancelled;
import io.github.guillermodubon.coachgym.membership.MembershipCreated;
import io.github.guillermodubon.coachgym.membership.MembershipFrozen;
import io.github.guillermodubon.coachgym.membership.MembershipReactivated;
import io.github.guillermodubon.coachgym.membership.MembershipRenewed;
import io.github.guillermodubon.coachgym.payment.PaymentRegistered;
import io.github.guillermodubon.coachgym.payment.PaymentRefunded;
import io.github.guillermodubon.coachgym.payment.PaymentVoided;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptCreated;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptProviderStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderEventAcknowledged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderPaymentConfirmed;
import io.github.guillermodubon.coachgym.plan.PlanChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionPlanEligibilityChanged;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

interface AuditEntryJpaRepository
        extends JpaRepository<
        AuditEntryJpaEntity,
        UUID> {
}

@Repository
class AuditEntryPersistenceAdapter
        implements AuditEntryStore {

    private static final String INSERT_AUDIT_SQL = """
            insert into gym.audit_entries
                (id, actor_user_id, actor_identifier_snapshot,
                 action_code, resource_type, resource_id,
                 resource_code_snapshot, summary, metadata, occurred_at)
            values
                (:id, :actorUserId, :actorIdentifier,
                 :actionCode, 'PAYMENT', :resourceId,
                 :resourceCode, :summary, cast(:metadata as jsonb), :occurredAt)
            """;

    private final AuditEntryJpaRepository repository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    AuditEntryPersistenceAdapter(
            AuditEntryJpaRepository repository,
            NamedParameterJdbcTemplate jdbcTemplate) {

        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void recordClientRegistered(
            ClientRegistered event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordPlanChanged(            PlanChanged event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordPromotionChanged(
            PromotionChanged event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordPromotionPlanEligibilityChanged(
            PromotionPlanEligibilityChanged event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordMembershipCreated(
            MembershipCreated event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordMembershipRenewed(
            MembershipRenewed event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordMembershipFrozen(
            MembershipFrozen event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordMembershipReactivated(
            MembershipReactivated event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordMembershipCancelled(
            MembershipCancelled event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordPaymentRegistered(
            PaymentRegistered event) {

        repository.save(
                AuditEntryJpaEntity.from(
                        event));
    }

    @Override
    @Transactional
    public void recordPaymentVoided(PaymentVoided event) {
        insertPaymentAudit(
                event.changedByUserId(), event.actorIdentifier(),
                "PAYMENT_VOIDED", event.paymentId(), event.paymentCode(),
                "Payment voided.",
                "{\"previousStatus\":\"PAID\",\"newStatus\":\"VOIDED\"}",
                event.occurredAt());
    }

    @Override
    @Transactional
    public void recordPaymentRefunded(PaymentRefunded event) {
        String metadata = "{\"previousStatus\":\"PAID\","
                + "\"newStatus\":\"REFUNDED\","
                + "\"refundId\":\"" + event.refundId() + "\","
                + "\"refundAmount\":\"" + event.amount().toPlainString() + "\","
                + "\"currency\":\"" + event.currency() + "\","
                + "\"externalReferencePresent\":"
                + event.externalReferencePresent() + "}";
        insertPaymentAudit(
                event.changedByUserId(), event.actorIdentifier(),
                "PAYMENT_REFUNDED", event.paymentId(), event.paymentCode(),
                "Payment refunded.", metadata, event.occurredAt());
    }

    @Override
    @Transactional
    public void recordPaymentAttemptCreated(PaymentAttemptCreated event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordPaymentAttemptStatusChanged(PaymentAttemptStatusChanged event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordPaymentAttemptProviderStatusChanged(
            PaymentAttemptProviderStatusChanged event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordPaymentProviderEventAcknowledged(
            PaymentProviderEventAcknowledged event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordPaymentProviderPaymentConfirmed(
            PaymentProviderPaymentConfirmed event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    private void insertPaymentAudit(
            UUID actorUserId,
            String actorIdentifier,
            String actionCode,
            UUID paymentId,
            String paymentCode,
            String summary,
            String metadata,
            java.time.Instant occurredAt) {
        jdbcTemplate.update(
                INSERT_AUDIT_SQL,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("actorUserId", actorUserId)
                        .addValue("actorIdentifier", actorIdentifier)
                        .addValue("actionCode", actionCode)
                        .addValue("resourceId", paymentId)
                        .addValue("resourceCode", paymentCode)
                        .addValue("summary", summary)
                        .addValue("metadata", metadata)
                        .addValue("occurredAt", java.time.OffsetDateTime.ofInstant(
                                occurredAt, java.time.ZoneOffset.UTC)));
    }

    @Override
    @Transactional
    public void recordDeniedAccessAttempt(
            AccessAttemptRecorded event) {

        repository.save(
                AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordEquipmentCategoryCreated(EquipmentCategoryCreatedEvent event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordEquipmentCategoryUpdated(EquipmentCategoryUpdatedEvent event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordEquipmentCategoryActivated(EquipmentCategoryActivatedEvent event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordEquipmentCategoryDeactivated(EquipmentCategoryDeactivatedEvent event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordEquipmentRegistered(EquipmentRegisteredEvent event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordEquipmentUpdated(EquipmentUpdatedEvent event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordEquipmentStatusChanged(EquipmentStatusChangedEvent event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordIncidentReported(IncidentReportedEvent event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordIncidentInvestigationStarted(
            IncidentInvestigationStartedEvent event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordIncidentPriorityChanged(
            IncidentPriorityChangedEvent event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordIncidentResolved(IncidentResolvedEvent event) {
        repository.save(AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordMaintenanceScheduled(
            MaintenanceScheduledEvent event) {

        repository.save(
                AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordMaintenanceUpdated(
            MaintenanceUpdatedEvent event) {

        repository.save(
                AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordMaintenanceStarted(
            MaintenanceStartedEvent event) {

        repository.save(
                AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordMaintenanceCompleted(
            MaintenanceCompletedEvent event) {

        repository.save(
                AuditEntryJpaEntity.from(event));
    }

    @Override
    @Transactional
    public void recordMaintenanceCancelled(
            MaintenanceCancelledEvent event) {

        repository.save(
                AuditEntryJpaEntity.from(event));
    }
}
