package io.github.guillermodubon.coachgym.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentIncidentVersionConflictException;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatusChangedEvent;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentVersionConflictException;
import io.github.guillermodubon.coachgym.equipment.EquipmentIncidentNotFoundException;
import io.github.guillermodubon.coachgym.equipment.EquipmentIncidentStateConflictException;
import io.github.guillermodubon.coachgym.equipment.EquipmentIncidentVersionConflictException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentVersionConflictException;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatusTransition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class EquipmentIncidentApplicationServiceTest {

    private static final UUID EQUIPMENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant OCCURRED_AT =
            Instant.parse("2026-09-02T23:00:00Z");
    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "receptionist");

    @Mock
    private EquipmentStore equipmentStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EquipmentIncidentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new EquipmentIncidentApplicationService(
                equipmentStore,
                eventPublisher);
    }

    @Test
    void availableEquipmentIsTakenOutOfService() {
        EquipmentDetails available = equipment(EquipmentStatus.AVAILABLE, 3L);
        EquipmentDetails updated = equipment(EquipmentStatus.OUT_OF_SERVICE, 4L);
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(available));
        when(equipmentStore.applyTransition(
                eq(EQUIPMENT_ID),
                any(EquipmentStatusTransition.class),
                eq(ACTOR),
                eq(3L),
                eq(OCCURRED_AT)))
                .thenReturn(updated);

        EquipmentDetails result = service.takeOutOfServiceForIncident(
                EQUIPMENT_ID,
                "INC-PENDING-123",
                3L,
                ACTOR,
                OCCURRED_AT);

        assertThat(result.status()).isEqualTo(EquipmentStatus.OUT_OF_SERVICE);

        ArgumentCaptor<EquipmentStatusTransition> transitionCaptor =
                ArgumentCaptor.forClass(EquipmentStatusTransition.class);
        verify(equipmentStore).applyTransition(
                eq(EQUIPMENT_ID),
                transitionCaptor.capture(),
                eq(ACTOR),
                eq(3L),
                eq(OCCURRED_AT));
        assertThat(transitionCaptor.getValue().reason())
                .contains("INC-PENDING-123");

        ArgumentCaptor<EquipmentStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(EquipmentStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().previousStatus())
                .isEqualTo(EquipmentStatus.AVAILABLE);
        assertThat(eventCaptor.getValue().newStatus())
                .isEqualTo(EquipmentStatus.OUT_OF_SERVICE);
        assertThat(eventCaptor.getValue().occurredAt())
                .isEqualTo(OCCURRED_AT);
    }

    @Test
    void alreadyOutOfServiceEquipmentIsIdempotent() {
        EquipmentDetails current = equipment(EquipmentStatus.OUT_OF_SERVICE, 4L);
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(current));

        EquipmentDetails result = service.takeOutOfServiceForIncident(
                EQUIPMENT_ID,
                "INC-PENDING-124",
                4L,
                ACTOR,
                OCCURRED_AT);

        assertThat(result).isSameAs(current);
        verify(equipmentStore, never()).applyTransition(
                any(), any(), any(), eq(4L), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void equipmentInMaintenanceRemainsUnchanged() {
        EquipmentDetails current = equipment(EquipmentStatus.MAINTENANCE, 5L);
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(current));

        EquipmentDetails result = service.takeOutOfServiceForIncident(
                EQUIPMENT_ID,
                "INC-PENDING-125",
                5L,
                ACTOR,
                OCCURRED_AT);

        assertThat(result).isSameAs(current);
        verify(equipmentStore, never()).applyTransition(
                any(), any(), any(), eq(5L), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void retiredEquipmentRejectsIncidentWithdrawal() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                equipment(
                                        EquipmentStatus.RETIRED,
                                        6L)));

        assertThatThrownBy(
                () -> service.takeOutOfServiceForIncident(
                        EQUIPMENT_ID,
                        "INC-PENDING-126",
                        6L,
                        ACTOR,
                        OCCURRED_AT))
                .isInstanceOf(
                        EquipmentIncidentStateConflictException.class)
                .hasMessageContaining(
                        "Retired equipment");

        verify(equipmentStore, never())
                .applyTransition(
                        any(),
                        any(),
                        any(),
                        any(Long.class),
                        any());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void unknownEquipmentIsRejected() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.takeOutOfServiceForIncident(
                        EQUIPMENT_ID,
                        "INC-PENDING-127",
                        0L,
                        ACTOR,
                        OCCURRED_AT))
                .isInstanceOf(
                        EquipmentIncidentNotFoundException.class)
                .hasMessageContaining(
                        EQUIPMENT_ID.toString());

        verify(equipmentStore, never())
                .applyTransition(
                        any(),
                        any(),
                        any(),
                        any(Long.class),
                        any());

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void blankIncidentReferenceIsRejectedBeforePersistence() {
        assertThatThrownBy(() -> service.takeOutOfServiceForIncident(
                EQUIPMENT_ID,
                "   ",
                0L,
                ACTOR,
                OCCURRED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Incident reference is required.");

        verify(equipmentStore, never()).findById(any());
    }

    private static EquipmentDetails equipment(
            EquipmentStatus status,
            long version) {
        return new EquipmentDetails(
                EQUIPMENT_ID,
                1L,
                "EQP-000001",
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                "Cardio",
                "Treadmill",
                "Example",
                "T-1",
                "SN-1",
                "Cardio floor",
                status,
                null,
                null,
                status == EquipmentStatus.RETIRED ? OCCURRED_AT : null,
                status == EquipmentStatus.RETIRED ? ACTOR_ID : null,
                status == EquipmentStatus.RETIRED ? "Not repairable." : null,
                ACTOR_ID,
                ACTOR_ID,
                OCCURRED_AT.minusSeconds(3600),
                OCCURRED_AT,
                version);
    }

    @Test
    void translatesInternalVersionConflictToPublicException() {
        EquipmentDetails available =
                equipment(
                        EquipmentStatus.AVAILABLE,
                        3L);

        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(Optional.of(available));

        when(equipmentStore.applyTransition(
                eq(EQUIPMENT_ID),
                any(EquipmentStatusTransition.class),
                eq(ACTOR),
                eq(3L),
                eq(OCCURRED_AT)))
                .thenThrow(
                        new EquipmentVersionConflictException(
                                EQUIPMENT_ID));

        assertThatThrownBy(
                () -> service.takeOutOfServiceForIncident(
                        EQUIPMENT_ID,
                        "INC-000001",
                        3L,
                        ACTOR,
                        OCCURRED_AT))
                .isInstanceOf(
                        EquipmentIncidentVersionConflictException.class);

        verify(eventPublisher, never())
                .publishEvent(any());
    }

}
