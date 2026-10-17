package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceCancelledEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceCompletedEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceScheduledEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStartedEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceUpdatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class MaintenanceAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    MaintenanceAuditEventListener(AuditEntryStore auditEntryStore) {
        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    void on(MaintenanceScheduledEvent event) {
        auditEntryStore.recordMaintenanceScheduled(event);
    }

    @EventListener
    void on(MaintenanceUpdatedEvent event) {
        auditEntryStore.recordMaintenanceUpdated(event);
    }

    @EventListener
    void on(MaintenanceStartedEvent event) {
        auditEntryStore.recordMaintenanceStarted(event);
    }

    @EventListener
    void on(MaintenanceCompletedEvent event) {
        auditEntryStore.recordMaintenanceCompleted(event);
    }

    @EventListener
    void on(MaintenanceCancelledEvent event) {
        auditEntryStore.recordMaintenanceCancelled(event);
    }
}
