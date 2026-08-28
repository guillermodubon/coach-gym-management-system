package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.client.ClientRegistered;
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
}
