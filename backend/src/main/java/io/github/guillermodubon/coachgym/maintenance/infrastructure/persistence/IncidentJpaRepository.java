package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface IncidentJpaRepository extends
        JpaRepository<IncidentJpaEntity, UUID>,
        JpaSpecificationExecutor<IncidentJpaEntity> {

    Optional<IncidentJpaEntity> findByIdAndBranchId(UUID id, UUID branchId);
}
