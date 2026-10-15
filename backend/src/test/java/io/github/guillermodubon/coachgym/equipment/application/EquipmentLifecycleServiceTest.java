package io.github.guillermodubon.coachgym.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatusChangedEvent;
import io.github.guillermodubon.coachgym.equipment.application.command.MarkAvailableCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.MarkOutOfServiceCommand;
import io.github.guillermodubon.coachgym.equipment.application.command.RetireEquipmentCommand;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentStateConflictException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentVersionConflictException;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatusTransition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class EquipmentLifecycleServiceTest {

    private static final Instant FIXED =
            Instant.parse(
                    "2025-10-01T09:00:00Z");

    private static final Clock CLOCK =
            Clock.fixed(
                    FIXED,
                    ZoneOffset.UTC);

    private static final UUID EQUIPMENT_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID CATEGORY_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final UUID ADMIN_ID =
            UUID.fromString(
                    "50000000-0000-0000-0000-000000000001");

    private static final String REASON =
            "Routine maintenance check";

    private static final AuthenticatedActor ADMIN =
            new AuthenticatedActor(
                    ADMIN_ID,
                    "admin@gym.com");

    @Mock
    private EquipmentStore equipmentStore;

    @Mock
    private EquipmentCategoryStore categoryStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EquipmentApplicationService service;

    @BeforeEach
    void setUp() {
        service =
                new EquipmentApplicationService(
                        equipmentStore,
                        categoryStore,
                        eventPublisher,
                        CLOCK);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private EquipmentDetails equipment(
            EquipmentStatus status) {

        return new EquipmentDetails(
                EQUIPMENT_ID,
                1L,
                "EQP-000001",
                CATEGORY_ID,
                "Cardio",
                "Treadmill",
                null,
                null,
                null,
                null,
                status,
                null,
                null,
                null,
                null,
                null,
                ADMIN.id(),
                null,
                FIXED,
                FIXED,
                0L);
    }

    private EquipmentDetails equipmentAfterTransition(
            EquipmentStatus status,
            long version) {

        boolean retired =
                status == EquipmentStatus.RETIRED;

        return new EquipmentDetails(
                EQUIPMENT_ID,
                1L,
                "EQP-000001",
                CATEGORY_ID,
                "Cardio",
                "Treadmill",
                null,
                null,
                null,
                null,
                status,
                null,
                null,
                retired ? FIXED : null,
                retired ? ADMIN.id() : null,
                retired ? REASON : null,
                ADMIN.id(),
                ADMIN.id(),
                FIXED,
                FIXED,
                version);
    }

    private void verifyTransitionWasNotApplied() {
        verify(equipmentStore, never())
                .applyTransition(
                        any(UUID.class),
                        any(EquipmentStatusTransition.class),
                        any(AuthenticatedActor.class),
                        anyLong(),
                        any(Instant.class));
    }

    // -------------------------------------------------------------------------
    // Mark out of service
    // -------------------------------------------------------------------------

    @Test
    void markOutOfService_available_succeedsAndPublishesEvent() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                equipment(
                                        EquipmentStatus.AVAILABLE)));

        when(equipmentStore.applyTransition(
                eq(EQUIPMENT_ID),
                any(EquipmentStatusTransition.class),
                eq(ADMIN),
                eq(0L),
                eq(FIXED)))
                .thenReturn(
                        equipmentAfterTransition(
                                EquipmentStatus.OUT_OF_SERVICE,
                                1L));

        EquipmentDetails result =
                service.markOutOfService(
                        new MarkOutOfServiceCommand(
                                EQUIPMENT_ID,
                                REASON,
                                0L),
                        ADMIN);

        assertThat(result.status())
                .isEqualTo(
                        EquipmentStatus.OUT_OF_SERVICE);

        assertThat(result.version())
                .isEqualTo(1L);

        verify(equipmentStore)
                .applyTransition(
                        eq(EQUIPMENT_ID),
                        any(EquipmentStatusTransition.class),
                        eq(ADMIN),
                        eq(0L),
                        eq(FIXED));

        ArgumentCaptor<EquipmentStatusChangedEvent> captor =
                ArgumentCaptor.forClass(
                        EquipmentStatusChangedEvent.class);

        verify(eventPublisher)
                .publishEvent(captor.capture());

        EquipmentStatusChangedEvent event =
                captor.getValue();

        assertThat(event.previousStatus())
                .isEqualTo(
                        EquipmentStatus.AVAILABLE);

        assertThat(event.newStatus())
                .isEqualTo(
                        EquipmentStatus.OUT_OF_SERVICE);

        assertThat(event.reason())
                .isEqualTo(REASON);

        assertThat(event.actorUserId())
                .isEqualTo(ADMIN.id());

        assertThat(event.actorIdentifier())
                .isEqualTo(ADMIN.username());

        assertThat(event.occurredAt())
                .isEqualTo(FIXED);
    }

    @Test
    void markOutOfService_persistBeforePublish() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                equipment(
                                        EquipmentStatus.AVAILABLE)));

        when(equipmentStore.applyTransition(
                eq(EQUIPMENT_ID),
                any(EquipmentStatusTransition.class),
                eq(ADMIN),
                eq(0L),
                eq(FIXED)))
                .thenReturn(
                        equipmentAfterTransition(
                                EquipmentStatus.OUT_OF_SERVICE,
                                1L));

        InOrder order =
                inOrder(
                        equipmentStore,
                        eventPublisher);

        service.markOutOfService(
                new MarkOutOfServiceCommand(
                        EQUIPMENT_ID,
                        REASON,
                        0L),
                ADMIN);

        order.verify(equipmentStore)
                .applyTransition(
                        eq(EQUIPMENT_ID),
                        any(EquipmentStatusTransition.class),
                        eq(ADMIN),
                        eq(0L),
                        eq(FIXED));

        order.verify(eventPublisher)
                .publishEvent(
                        any(
                                EquipmentStatusChangedEvent.class));
    }

    @Test
    void markOutOfService_alreadyOutOfService_throwsStateConflict() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                equipment(
                                        EquipmentStatus.OUT_OF_SERVICE)));

        assertThatThrownBy(() ->
                service.markOutOfService(
                        new MarkOutOfServiceCommand(                                 EQUIPMENT_ID,
                                REASON,
                                0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentStateConflictException.class);

        verifyTransitionWasNotApplied();

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void markOutOfService_retired_throwsStateConflict() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                equipment(
                                        EquipmentStatus.RETIRED)));

        assertThatThrownBy(() ->
                service.markOutOfService(
                        new MarkOutOfServiceCommand(
                                EQUIPMENT_ID,
                                REASON,
                                0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentStateConflictException.class);

        verifyTransitionWasNotApplied();

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void markOutOfService_notFound_throwsNotFoundException() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.markOutOfService(
                        new MarkOutOfServiceCommand(
                                EQUIPMENT_ID,
                                REASON,
                                0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentNotFoundException.class);

        verifyTransitionWasNotApplied();

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void markOutOfService_noEventOnApplyTransitionFailure() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                equipment(
                                        EquipmentStatus.AVAILABLE)));

        when(equipmentStore.applyTransition(
                eq(EQUIPMENT_ID),
                any(EquipmentStatusTransition.class),
                eq(ADMIN),
                eq(0L),
                eq(FIXED)))
                .thenThrow(
                        new EquipmentVersionConflictException(
                                EQUIPMENT_ID));

        assertThatThrownBy(() ->
                service.markOutOfService(
                        new MarkOutOfServiceCommand(
                                EQUIPMENT_ID,
                                REASON,
                                0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentVersionConflictException.class);

        verify(equipmentStore)
                .applyTransition(
                        eq(EQUIPMENT_ID),
                        any(EquipmentStatusTransition.class),
                        eq(ADMIN),
                        eq(0L),
                        eq(FIXED));

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    // -------------------------------------------------------------------------
    // Mark available
    // -------------------------------------------------------------------------

    @Test
    void markAvailable_outOfService_succeedsAndPublishesEvent() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                equipment(
                                        EquipmentStatus.OUT_OF_SERVICE)));

        when(equipmentStore.applyTransition(
                eq(EQUIPMENT_ID),
                any(EquipmentStatusTransition.class),
                eq(ADMIN),
                eq(0L),
                eq(FIXED)))
                .thenReturn(
                        equipmentAfterTransition(
                                EquipmentStatus.AVAILABLE,
                                1L));

        EquipmentDetails result =
                service.markAvailable(
                        new MarkAvailableCommand(
                                EQUIPMENT_ID,
                                REASON,
                                0L),
                        ADMIN);

        assertThat(result.status())
                .isEqualTo(
                        EquipmentStatus.AVAILABLE);

        assertThat(result.version())
                .isEqualTo(1L);

        verify(equipmentStore)
                .applyTransition(
                        eq(EQUIPMENT_ID),
                        any(EquipmentStatusTransition.class),
                        eq(ADMIN),
                        eq(0L),
                        eq(FIXED));

        ArgumentCaptor<EquipmentStatusChangedEvent> captor =
                ArgumentCaptor.forClass(
                        EquipmentStatusChangedEvent.class);

        verify(eventPublisher)
                .publishEvent(captor.capture());

        EquipmentStatusChangedEvent event =
                captor.getValue();

        assertThat(event.previousStatus())
                .isEqualTo(
                        EquipmentStatus.OUT_OF_SERVICE);

        assertThat(event.newStatus())
                .isEqualTo(
                        EquipmentStatus.AVAILABLE);

        assertThat(event.reason())
                .isEqualTo(REASON);

        assertThat(event.actorUserId())
                .isEqualTo(ADMIN.id());

        assertThat(event.actorIdentifier())
                .isEqualTo(ADMIN.username());

        assertThat(event.occurredAt())
                .isEqualTo(FIXED);
    }

    @Test
    void markAvailable_alreadyAvailable_throwsStateConflict() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                equipment(
                                        EquipmentStatus.AVAILABLE)));

        assertThatThrownBy(() ->
                service.markAvailable(
                        new MarkAvailableCommand(
                                EQUIPMENT_ID,
                                REASON,
                                0L),
                        ADMIN))
                .isInstanceOf(                         EquipmentStateConflictException.class);

        verifyTransitionWasNotApplied();

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void markAvailable_retired_throwsStateConflict() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                equipment(
                                        EquipmentStatus.RETIRED)));

        assertThatThrownBy(() ->
                service.markAvailable(
                        new MarkAvailableCommand(
                                EQUIPMENT_ID,
                                REASON,
                                0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentStateConflictException.class);

        verifyTransitionWasNotApplied();

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    // -------------------------------------------------------------------------
    // Retire
    // -------------------------------------------------------------------------

    @Test
    void retire_fromAvailable_succeedsAndSetsRetirementData() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                equipment(
                                        EquipmentStatus.AVAILABLE)));

        when(equipmentStore.applyTransition(
                eq(EQUIPMENT_ID),
                any(EquipmentStatusTransition.class),
                eq(ADMIN),
                eq(0L),
                eq(FIXED)))
                .thenReturn(
                        equipmentAfterTransition(
                                EquipmentStatus.RETIRED,
                                1L));

        EquipmentDetails result =
                service.retire(
                        new RetireEquipmentCommand(
                                EQUIPMENT_ID,
                                REASON,
                                0L),
                        ADMIN);

        assertThat(result.status())
                .isEqualTo(
                        EquipmentStatus.RETIRED);

        assertThat(result.version())
                .isEqualTo(1L);

        assertThat(result.retiredAt())
                .isEqualTo(FIXED);

        assertThat(result.retiredByUserId())
                .isEqualTo(ADMIN.id());

        assertThat(result.retirementReason())
                .isEqualTo(REASON);

        verify(equipmentStore)
                .applyTransition(
                        eq(EQUIPMENT_ID),
                        any(EquipmentStatusTransition.class),
                        eq(ADMIN),
                        eq(0L),
                        eq(FIXED));

        ArgumentCaptor<EquipmentStatusChangedEvent> captor =
                ArgumentCaptor.forClass(
                        EquipmentStatusChangedEvent.class);

        verify(eventPublisher)
                .publishEvent(captor.capture());

        EquipmentStatusChangedEvent event =
                captor.getValue();

        assertThat(event.previousStatus())
                .isEqualTo(
                        EquipmentStatus.AVAILABLE);

        assertThat(event.newStatus())
                .isEqualTo(
                        EquipmentStatus.RETIRED);

        assertThat(event.reason())
                .isEqualTo(REASON);

        assertThat(event.actorUserId())
                .isEqualTo(ADMIN.id());

        assertThat(event.actorIdentifier())
                .isEqualTo(ADMIN.username());

        assertThat(event.occurredAt())
                .isEqualTo(FIXED);
    }

    @Test
    void retire_fromOutOfService_succeeds() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                equipment(
                                        EquipmentStatus.OUT_OF_SERVICE)));

        when(equipmentStore.applyTransition(
                eq(EQUIPMENT_ID),
                any(EquipmentStatusTransition.class),
                eq(ADMIN),
                eq(0L),
                eq(FIXED)))
                .thenReturn(
                        equipmentAfterTransition(
                                EquipmentStatus.RETIRED,
                                1L));

        EquipmentDetails result =
                service.retire(
                        new RetireEquipmentCommand(
                                EQUIPMENT_ID,
                                REASON,
                                0L),
                        ADMIN);

        assertThat(result.status())
                .isEqualTo(
                        EquipmentStatus.RETIRED);

        assertThat(result.version())
                .isEqualTo(1L);

        assertThat(result.retiredAt())
                .isEqualTo(FIXED);

        assertThat(result.retiredByUserId())
                .isEqualTo(ADMIN.id());

        assertThat(result.retirementReason())
                .isEqualTo(REASON);

        verify(equipmentStore)
                .applyTransition(
                        eq(EQUIPMENT_ID),
                        any(EquipmentStatusTransition.class),
                        eq(ADMIN),
                        eq(0L),
                        eq(FIXED));

        ArgumentCaptor<EquipmentStatusChangedEvent> captor =
                ArgumentCaptor.forClass(
                        EquipmentStatusChangedEvent.class);

        verify(eventPublisher)
                .publishEvent(captor.capture());

        EquipmentStatusChangedEvent event =
                captor.getValue();

        assertThat(event.previousStatus())
                .isEqualTo(
                        EquipmentStatus.OUT_OF_SERVICE);

        assertThat(event.newStatus())
                .isEqualTo(
                        EquipmentStatus.RETIRED);

        assertThat(event.reason())
                .isEqualTo(REASON);

        assertThat(event.actorUserId())
                .isEqualTo(ADMIN.id());

        assertThat(event.actorIdentifier())
                .isEqualTo(ADMIN.username());

        assertThat(event.occurredAt())
                .isEqualTo(FIXED);
    }

    @Test
    void retire_alreadyRetired_throwsStateConflict() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                equipment(
                                        EquipmentStatus.RETIRED)));

        assertThatThrownBy(() ->
                service.retire(
                        new RetireEquipmentCommand(
                                EQUIPMENT_ID,
                                REASON,
                                0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentStateConflictException.class);

        verifyTransitionWasNotApplied();

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void retire_maintenance_throwsStateConflict() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                equipment(
                                        EquipmentStatus.MAINTENANCE)));

        assertThatThrownBy(() ->
                service.retire(
                        new RetireEquipmentCommand(
                                EQUIPMENT_ID,
                                REASON,
                                0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentStateConflictException.class);

        verifyTransitionWasNotApplied();

        verify(eventPublisher, never())
                .publishEvent(any());
    }

    @Test
    void retire_noEventOnPersistFailure() {
        when(equipmentStore.findById(EQUIPMENT_ID))
                .thenReturn(
                        Optional.of(
                                equipment(                                         EquipmentStatus.AVAILABLE)));

        when(equipmentStore.applyTransition(
                eq(EQUIPMENT_ID),
                any(EquipmentStatusTransition.class),
                eq(ADMIN),
                eq(0L),
                eq(FIXED)))
                .thenThrow(
                        new EquipmentVersionConflictException(
                                EQUIPMENT_ID));

        assertThatThrownBy(() ->
                service.retire(
                        new RetireEquipmentCommand(
                                EQUIPMENT_ID,
                                REASON,
                                0L),
                        ADMIN))
                .isInstanceOf(
                        EquipmentVersionConflictException.class);

        verify(equipmentStore)
                .applyTransition(
                        eq(EQUIPMENT_ID),
                        any(EquipmentStatusTransition.class),
                        eq(ADMIN),
                        eq(0L),
                        eq(FIXED));

        verify(eventPublisher, never())
                .publishEvent(any());
    }
}
