package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.client.ClientRegistered;
import io.github.guillermodubon.coachgym.membership.MembershipCreated;
import io.github.guillermodubon.coachgym.membership.MembershipFrozen;
import io.github.guillermodubon.coachgym.membership.MembershipReactivated;
import io.github.guillermodubon.coachgym.membership.MembershipRenewed;
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
}
