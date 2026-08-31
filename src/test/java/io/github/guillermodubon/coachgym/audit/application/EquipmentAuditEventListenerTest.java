package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.equipment.EquipmentRegisteredEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatusChangedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentUpdatedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EquipmentAuditEventListenerTest {
    private static final UUID EQUIPMENT_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-31T12:00:00Z");

    @Mock private AuditEntryStore auditEntryStore;

    @Test
    void registeredEventIsForwardedToAuditStore() {
        EquipmentAuditEventListener listener = new EquipmentAuditEventListener(auditEntryStore);
        EquipmentRegisteredEvent event = new EquipmentRegisteredEvent(
                EQUIPMENT_ID, "EQP-000001", CATEGORY_ID, ACTOR_ID, "admin", OCCURRED_AT);
        listener.record(event);
        verify(auditEntryStore).recordEquipmentRegistered(event);
    }

    @Test
    void updatedEventIsForwardedToAuditStore() {
        EquipmentAuditEventListener listener = new EquipmentAuditEventListener(auditEntryStore);
        EquipmentUpdatedEvent event = new EquipmentUpdatedEvent(
                EQUIPMENT_ID, "EQP-000001", ACTOR_ID, "admin", OCCURRED_AT);
        listener.record(event);
        verify(auditEntryStore).recordEquipmentUpdated(event);
    }

    @Test
    void statusChangedEventIsForwardedToAuditStore() {
        EquipmentAuditEventListener listener = new EquipmentAuditEventListener(auditEntryStore);
        EquipmentStatusChangedEvent event = new EquipmentStatusChangedEvent(
                EQUIPMENT_ID, "EQP-000001", EquipmentStatus.AVAILABLE,
                EquipmentStatus.OUT_OF_SERVICE, "Inspection", ACTOR_ID, "admin", OCCURRED_AT);
        listener.record(event);
        verify(auditEntryStore).recordEquipmentStatusChanged(event);
    }
}
