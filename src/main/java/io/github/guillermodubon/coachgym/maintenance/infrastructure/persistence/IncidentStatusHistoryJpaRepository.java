package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IncidentStatusHistoryJpaRepository
        extends JpaRepository<IncidentStatusHistoryJpaEntity, UUID> {

    List<IncidentStatusHistoryJpaEntity>
            findByIncidentIdOrderByOccurredAtAscIdAsc(UUID incidentId);
}
