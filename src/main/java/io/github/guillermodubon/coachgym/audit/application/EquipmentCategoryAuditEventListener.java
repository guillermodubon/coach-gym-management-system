package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryActivatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryCreatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDeactivatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryUpdatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Synchronous audit listener for equipment category business events.
 *
 * <p>Follows the project convention: published after persistence succeeds;
 * recorded synchronously before the enclosing transaction commits.
 */
@Component
class EquipmentCategoryAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    EquipmentCategoryAuditEventListener(AuditEntryStore auditEntryStore) {
        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    void record(EquipmentCategoryCreatedEvent event) {
        auditEntryStore.recordEquipmentCategoryCreated(event);
    }

    @EventListener
    void record(EquipmentCategoryUpdatedEvent event) {
        auditEntryStore.recordEquipmentCategoryUpdated(event);
    }

    @EventListener
    void record(EquipmentCategoryActivatedEvent event) {
        auditEntryStore.recordEquipmentCategoryActivated(event);
    }

    @EventListener
    void record(EquipmentCategoryDeactivatedEvent event) {
        auditEntryStore.recordEquipmentCategoryDeactivated(event);
    }
}

