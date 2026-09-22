package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.audit.AuditExportCompleted;
import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
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
import io.github.guillermodubon.coachgym.payment.PaymentRegistered;
import io.github.guillermodubon.coachgym.payment.PaymentRefunded;
import io.github.guillermodubon.coachgym.payment.PaymentVoided;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptCreated;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptProviderStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderEventAcknowledged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderPaymentConfirmed;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptGenerated;
import io.github.guillermodubon.coachgym.plan.PlanChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionPlanEligibilityChanged;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryLifecycleEvent;
import io.github.guillermodubon.coachgym.organization.GymBranchCreated;
import io.github.guillermodubon.coachgym.organization.GymBranchStatusChanged;
import io.github.guillermodubon.coachgym.organization.GymBranchUpdated;
import io.github.guillermodubon.coachgym.organization.OrganizationUpdated;
import io.github.guillermodubon.coachgym.user.StaffPasswordChanged;
import io.github.guillermodubon.coachgym.user.StaffBranchAssigned;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentEnded;
import io.github.guillermodubon.coachgym.user.StaffProfilePhotoChanged;
import io.github.guillermodubon.coachgym.user.StaffProfileUpdated;
import io.github.guillermodubon.coachgym.user.StaffScopeChanged;

public interface AuditEntryStore {

    void recordAuditExportCompleted(AuditExportCompleted event);

    void recordClientRegistered(ClientRegistered event);

    void recordPlanChanged(PlanChanged event);

    void recordPromotionChanged(PromotionChanged event);

    void recordPromotionPlanEligibilityChanged(PromotionPlanEligibilityChanged event);

    void recordMembershipCreated(MembershipCreated event);

    void recordMembershipRenewed(MembershipRenewed event);

    void recordMembershipFrozen(MembershipFrozen event);

    void recordMembershipReactivated(MembershipReactivated event);

    void recordMembershipCancelled(MembershipCancelled event);

    void recordPaymentRegistered(PaymentRegistered event);

    void recordPaymentVoided(PaymentVoided event);

    void recordPaymentRefunded(PaymentRefunded event);

    void recordPaymentAttemptCreated(PaymentAttemptCreated event);

    void recordPaymentAttemptStatusChanged(PaymentAttemptStatusChanged event);

    void recordPaymentAttemptProviderStatusChanged(
            PaymentAttemptProviderStatusChanged event);

    void recordPaymentProviderEventAcknowledged(
            PaymentProviderEventAcknowledged event);

    void recordPaymentProviderPaymentConfirmed(
            PaymentProviderPaymentConfirmed event);

    void recordPaymentReceiptGenerated(PaymentReceiptGenerated event);

    void recordEmailDeliveryLifecycle(EmailDeliveryLifecycleEvent event);

    void recordStaffProfileUpdated(StaffProfileUpdated event);

    void recordStaffProfilePhotoChanged(StaffProfilePhotoChanged event);

    void recordStaffPasswordChanged(StaffPasswordChanged event);

    void recordStaffBranchAssigned(StaffBranchAssigned event);

    void recordStaffBranchAssignmentEnded(StaffBranchAssignmentEnded event);

    void recordStaffScopeChanged(StaffScopeChanged event);

    void recordOrganizationUpdated(OrganizationUpdated event);

    void recordGymBranchCreated(GymBranchCreated event);

    void recordGymBranchUpdated(GymBranchUpdated event);

    void recordGymBranchStatusChanged(GymBranchStatusChanged event);

    void recordDeniedAccessAttempt(AccessAttemptRecorded event);

    void recordAccessPaymentPolicyChanged(AccessPaymentPolicyChanged event);

    void recordAccessCredentialIssued(AccessCredentialIssued event);

    void recordAccessCredentialRevoked(AccessCredentialRevoked event);

    void recordAccessCredentialReplaced(AccessCredentialReplaced event);

    void recordEquipmentCategoryCreated(EquipmentCategoryCreatedEvent event);

    void recordEquipmentCategoryUpdated(EquipmentCategoryUpdatedEvent event);

    void recordEquipmentCategoryActivated(EquipmentCategoryActivatedEvent event);

    void recordEquipmentCategoryDeactivated(EquipmentCategoryDeactivatedEvent event);

    void recordEquipmentRegistered(EquipmentRegisteredEvent event);

    void recordEquipmentUpdated(EquipmentUpdatedEvent event);

    void recordEquipmentStatusChanged(EquipmentStatusChangedEvent event);

    void recordIncidentReported(IncidentReportedEvent event);

    void recordIncidentInvestigationStarted(IncidentInvestigationStartedEvent event);

    void recordIncidentPriorityChanged(IncidentPriorityChangedEvent event);

    void recordIncidentResolved(IncidentResolvedEvent event);

    void recordMaintenanceScheduled(
            MaintenanceScheduledEvent event);

    void recordMaintenanceUpdated(
            MaintenanceUpdatedEvent event);

    void recordMaintenanceStarted(
            MaintenanceStartedEvent event);

    void recordMaintenanceCompleted(
            MaintenanceCompletedEvent event);

    void recordMaintenanceCancelled(
            MaintenanceCancelledEvent event);
}
