package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface MaintenanceJpaRepository extends
        JpaRepository<MaintenanceJpaEntity, UUID>,
        JpaSpecificationExecutor<MaintenanceJpaEntity> {

    boolean existsByEquipmentIdAndStatus(
            UUID equipmentId,
            MaintenanceStatus status);
}
