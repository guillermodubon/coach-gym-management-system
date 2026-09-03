package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.maintenance.IncidentInvestigationStartedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriorityChangedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentResolvedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Synchronously records incident business events in the audit trail. */
@Component
class IncidentAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    IncidentAuditEventListener(AuditEntryStore auditEntryStore) {
        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    void record(IncidentReportedEvent event) {
        auditEntryStore.recordIncidentReported(event);
    }

    @EventListener
    void record(IncidentInvestigationStartedEvent event) {
        auditEntryStore.recordIncidentInvestigationStarted(event);
    }

    @EventListener
    void record(IncidentPriorityChangedEvent event) {
        auditEntryStore.recordIncidentPriorityChanged(event);
    }

    @EventListener
    void record(IncidentResolvedEvent event) {
        auditEntryStore.recordIncidentResolved(event);
    }
}
