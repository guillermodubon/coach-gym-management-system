package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
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

public interface AuditEntryStore {

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

    void recordDeniedAccessAttempt(AccessAttemptRecorded event);

    void recordEquipmentCategoryCreated(EquipmentCategoryCreatedEvent event);

    void recordEquipmentCategoryUpdated(EquipmentCategoryUpdatedEvent event);

    void recordEquipmentCategoryActivated(EquipmentCategoryActivatedEvent event);

    void recordEquipmentCategoryDeactivated(EquipmentCategoryDeactivatedEvent event);

    void recordEquipmentRegistered(EquipmentRegisteredEvent event);

    void recordEquipmentUpdated(EquipmentUpdatedEvent event);

    void recordEquipmentStatusChanged(EquipmentStatusChangedEvent event);
}

