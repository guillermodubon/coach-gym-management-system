package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface IncidentJpaRepository extends
        JpaRepository<IncidentJpaEntity, UUID>,
        JpaSpecificationExecutor<IncidentJpaEntity> {
}
