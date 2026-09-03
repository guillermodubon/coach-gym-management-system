package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import io.github.guillermodubon.coachgym.equipment.EquipmentLookup;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentSearchQuery;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class IncidentPersistenceAdapterTest {

    @Mock
    private IncidentJpaRepository incidentRepository;

    @Mock
    private IncidentStatusHistoryJpaRepository historyRepository;

    @Mock
    private EquipmentLookup equipmentLookup;

    @Mock
    private EntityManager entityManager;

    private IncidentPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new IncidentPersistenceAdapter(
                incidentRepository,
                historyRepository,
                equipmentLookup,
                entityManager);
    }

    @Test
    void reportsIncidentAndInitialHistory() {
        UUID equipmentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        AuthenticatedActor actor =
                new AuthenticatedActor(actorId, "admin");

        when(incidentRepository.saveAndFlush(
                any(IncidentJpaEntity.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        when(historyRepository.saveAndFlush(
                any(IncidentStatusHistoryJpaEntity.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        when(equipmentLookup.findById(equipmentId))
                .thenReturn(
                        Optional.of(
                                equipment(
                                        equipmentId,
                                        actorId,
                                        now)));

        var result = adapter.report(
                new IncidentDefinition(
                        equipmentId,
                        IncidentPriority.HIGH,
                        "Motor failure.",
                        false,
                        null),
                actor,
                now);

        assertThat(result.equipmentId())
                .isEqualTo(equipmentId);

        assertThat(result.equipmentCode())
                .isEqualTo("EQP-000001");

        assertThat(result.priority())
                .isEqualTo(IncidentPriority.HIGH);

        verify(historyRepository).saveAndFlush(
                any(IncidentStatusHistoryJpaEntity.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsPaginatedSearchWithStableSort() {
        when(incidentRepository.findAll(
                any(Specification.class),
                any(Pageable.class)))
                .thenReturn(
                        new PageImpl<>(
                                java.util.List.of(),
                                PageRequest.of(0, 25),
                                0));

        var page = adapter.findAll(
                IncidentSearchQuery.defaults());

        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(25);
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(incidentRepository).findAll(
                any(Specification.class),
                pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(25);

        Sort.Order reportedAtOrder =
                pageable.getSort().getOrderFor("reportedAt");

        assertThat(reportedAtOrder).isNotNull();
        assertThat(reportedAtOrder.getDirection())
                .isEqualTo(Sort.Direction.DESC);

        Sort.Order idOrder =
                pageable.getSort().getOrderFor("id");

        assertThat(idOrder).isNotNull();
        assertThat(idOrder.getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    private static EquipmentDetails equipment(
            UUID equipmentId,
            UUID actorId,
            Instant now) {

        return new EquipmentDetails(
                equipmentId,
                1L,
                "EQP-000001",
                UUID.randomUUID(),
                "Cardio",
                "Treadmill",
                null,
                null,
                null,
                "Floor 1",
                EquipmentStatus.AVAILABLE,
                null,
                null,
                null,
                null,
                null,
                actorId,
                actorId,
                now,
                now,
                0L);
    }
}