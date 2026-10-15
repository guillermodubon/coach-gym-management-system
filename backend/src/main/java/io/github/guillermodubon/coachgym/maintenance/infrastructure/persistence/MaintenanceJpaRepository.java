package io.github.guillermodubon.coachgym.maintenance.infrastructure.persistence;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface MaintenanceJpaRepository extends
        JpaRepository<MaintenanceJpaEntity, UUID>,
        JpaSpecificationExecutor<MaintenanceJpaEntity> {

    Optional<MaintenanceJpaEntity> findByIdAndBranchId(UUID id, UUID branchId);

    boolean existsByEquipmentIdAndStatus(
            UUID equipmentId,
            MaintenanceStatus status);

    boolean existsByEquipmentIdAndStatusAndBranchId(
            UUID equipmentId,
            MaintenanceStatus status,
            UUID branchId);
}
