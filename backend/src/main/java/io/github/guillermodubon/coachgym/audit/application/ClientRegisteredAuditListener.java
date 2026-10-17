package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.client.ClientRegistered;
import io.github.guillermodubon.coachgym.plan.PlanChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class ClientRegisteredAuditListener {

    private final AuditEntryStore auditEntryStore;

    ClientRegisteredAuditListener(AuditEntryStore auditEntryStore) {
        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    void record(ClientRegistered event) {
        auditEntryStore.recordClientRegistered(event);
    }

    @EventListener
    void record(PlanChanged event) {
        auditEntryStore.recordPlanChanged(event);
    }
}
