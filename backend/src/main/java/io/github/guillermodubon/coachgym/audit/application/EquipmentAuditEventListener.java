package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.equipment.EquipmentRegisteredEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatusChangedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentUpdatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Synchronous audit listener for equipment business events.
 *
 * <p>Follows the project convention: events are published after persistence
 * succeeds; this listener records them to the audit trail synchronously within
 * the same transaction boundary.
 */
@Component
class EquipmentAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    EquipmentAuditEventListener(AuditEntryStore auditEntryStore) {
        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    void record(EquipmentRegisteredEvent event) {
        auditEntryStore.recordEquipmentRegistered(event);
    }

    @EventListener
    void record(EquipmentUpdatedEvent event) {
        auditEntryStore.recordEquipmentUpdated(event);
    }

    @EventListener
    void record(EquipmentStatusChangedEvent event) {
        auditEntryStore.recordEquipmentStatusChanged(event);
    }
}
