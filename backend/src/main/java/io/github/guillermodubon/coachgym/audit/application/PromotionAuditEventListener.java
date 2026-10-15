package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.promotion.PromotionChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionPlanEligibilityChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class PromotionAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    PromotionAuditEventListener(
            AuditEntryStore auditEntryStore) {

        this.auditEntryStore =
                auditEntryStore;
    }

    @EventListener
    void record(
            PromotionChanged event) {

        auditEntryStore.recordPromotionChanged(
                event);
    }

    @EventListener
    void record(
            PromotionPlanEligibilityChanged event) {

        auditEntryStore
                .recordPromotionPlanEligibilityChanged(
                        event);
    }
}
