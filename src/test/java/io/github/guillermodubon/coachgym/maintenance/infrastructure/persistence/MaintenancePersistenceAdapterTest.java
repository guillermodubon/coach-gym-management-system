package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentLookup;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.maintenance.IncidentDetails;
import io.github.guillermodubon.coachgym.maintenance.IncidentLookup;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceType;
import io.github.guillermodubon.coachgym.maintenance.application.MaintenanceSearchQuery;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class MaintenancePersistenceAdapterTest {

    @Mock private MaintenanceJpaRepository maintenanceRepository;
    @Mock private MaintenanceStatusHistoryJpaRepository historyRepository;
    @Mock private EquipmentLookup equipmentLookup;
    @Mock private IncidentLookup incidentLookup;
    @Mock private EntityManager entityManager;

    private MaintenancePersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MaintenancePersistenceAdapter(
                maintenanceRepository,
                historyRepository,
                equipmentLookup,
                incidentLookup,
                entityManager);
    }

    @Test
    void schedulesMaintenanceAndInitialHistory() {
        UUID equipmentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-04T03:00:00Z");
        AuthenticatedActor actor = new AuthenticatedActor(actorId, "admin");

        when(maintenanceRepository.saveAndFlush(any(MaintenanceJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.saveAndFlush(
                any(MaintenanceStatusHistoryJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentLookup.findById(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId, actorId, now)));

        var result = adapter.schedule(
                new MaintenanceDefinition(
                        equipmentId, null, MaintenanceType.PREVENTIVE,
                        LocalDate.of(2026, 9, 10), null, null,
                        new BigDecimal("25.00"), "USD", null, null),
                actor,
                now);

        assertThat(result.equipmentId()).isEqualTo(equipmentId);
        assertThat(result.status()).isEqualTo(MaintenanceStatus.SCHEDULED);
        verify(historyRepository).saveAndFlush(
                any(MaintenanceStatusHistoryJpaEntity.class));
        verify(entityManager).refresh(any(MaintenanceJpaEntity.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsPaginatedSearchWithStableSecondarySort() {
        when(maintenanceRepository.findAll(
                any(Specification.class),
                any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);

                    return new PageImpl<>(
                            java.util.List.of(),
                            pageable,
                            0);
                });

        adapter.findAll(MaintenanceSearchQuery.defaults());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(maintenanceRepository).findAll(
                any(Specification.class), captor.capture());

        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort().getOrderFor("scheduledOn").isAscending())
                .isTrue();
        assertThat(pageable.getSort().getOrderFor("id").isAscending())
                .isTrue();
    }

    @Test
    void enrichesLinkedIncidentCodeThroughPublicLookup() {
        UUID equipmentId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-04T03:00:00Z");
        AuthenticatedActor actor = new AuthenticatedActor(actorId, "admin");
        IncidentDetails incident = org.mockito.Mockito.mock(IncidentDetails.class);

        when(maintenanceRepository.saveAndFlush(any(MaintenanceJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentLookup.findById(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId, actorId, now)));
        when(incidentLookup.findById(incidentId)).thenReturn(Optional.of(incident));
        when(incident.incidentCode()).thenReturn("INC-000001");

        var result = adapter.schedule(
                new MaintenanceDefinition(
                        equipmentId, incidentId, MaintenanceType.CORRECTIVE,
                        LocalDate.of(2026, 9, 10), null, null,
                        null, "USD", null, null),
                actor,
                now);

        assertThat(result.incidentCode()).isEqualTo("INC-000001");
    }

    private static EquipmentDetails equipment(
            UUID equipmentId,
            UUID actorId,
            Instant now) {
        return new EquipmentDetails(
                equipmentId, 1L, "EQP-000001", UUID.randomUUID(),
                "Cardio", "Treadmill", null, null, null, "Floor 1",
                EquipmentStatus.AVAILABLE, null, null, null, null, null,
                actorId, actorId, now, now, 0L);
    }
}
