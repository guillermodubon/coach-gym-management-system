package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryActivatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryCreatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDeactivatedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryUpdatedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EquipmentCategoryAuditEventListenerTest {
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-31T12:00:00Z");

    @Mock private AuditEntryStore auditEntryStore;

    @Test
    void createdEventIsForwardedToAuditStore() {
        EquipmentCategoryAuditEventListener listener = new EquipmentCategoryAuditEventListener(auditEntryStore);
        EquipmentCategoryCreatedEvent event = new EquipmentCategoryCreatedEvent(
                CATEGORY_ID, "Cardio", ACTOR_ID, "admin", OCCURRED_AT);
        listener.record(event);
        verify(auditEntryStore).recordEquipmentCategoryCreated(event);
    }

    @Test
    void updatedEventIsForwardedToAuditStore() {
        EquipmentCategoryAuditEventListener listener = new EquipmentCategoryAuditEventListener(auditEntryStore);
        EquipmentCategoryUpdatedEvent event = new EquipmentCategoryUpdatedEvent(
                CATEGORY_ID, "Cardio", ACTOR_ID, "admin", OCCURRED_AT);
        listener.record(event);
        verify(auditEntryStore).recordEquipmentCategoryUpdated(event);
    }

    @Test
    void activatedEventIsForwardedToAuditStore() {
        EquipmentCategoryAuditEventListener listener = new EquipmentCategoryAuditEventListener(auditEntryStore);
        EquipmentCategoryActivatedEvent event = new EquipmentCategoryActivatedEvent(
                CATEGORY_ID, "Cardio", ACTOR_ID, "admin", OCCURRED_AT);
        listener.record(event);
        verify(auditEntryStore).recordEquipmentCategoryActivated(event);
    }

    @Test
    void deactivatedEventIsForwardedToAuditStore() {
        EquipmentCategoryAuditEventListener listener = new EquipmentCategoryAuditEventListener(auditEntryStore);
        EquipmentCategoryDeactivatedEvent event = new EquipmentCategoryDeactivatedEvent(
                CATEGORY_ID, "Cardio", ACTOR_ID, "admin", OCCURRED_AT);
        listener.record(event);
        verify(auditEntryStore).recordEquipmentCategoryDeactivated(event);
    }
}
