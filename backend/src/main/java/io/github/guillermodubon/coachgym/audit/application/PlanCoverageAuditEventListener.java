package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.plan.MembershipPlanCoverageChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Persists the privacy-minimized audit record for plan-coverage administration. */
@Component
class PlanCoverageAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    PlanCoverageAuditEventListener(AuditEntryStore auditEntryStore) {
        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    void record(MembershipPlanCoverageChanged event) {
        auditEntryStore.recordMembershipPlanCoverageChanged(event);
    }
}
