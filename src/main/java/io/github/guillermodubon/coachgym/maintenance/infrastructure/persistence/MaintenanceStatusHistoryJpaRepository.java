package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MaintenanceStatusHistoryJpaRepository
        extends JpaRepository<MaintenanceStatusHistoryJpaEntity, UUID> {

    List<MaintenanceStatusHistoryJpaEntity>
            findByMaintenanceIdOrderByOccurredAtAscIdAsc(UUID maintenanceId);
}
