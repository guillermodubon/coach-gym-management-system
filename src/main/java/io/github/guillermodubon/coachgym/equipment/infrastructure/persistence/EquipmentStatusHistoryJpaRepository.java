package io.github.guillermodubon.coachgym.equipment.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EquipmentStatusHistoryJpaRepository
        extends JpaRepository<EquipmentStatusHistoryJpaEntity, UUID> {

    /**
     * Returns the full status history for an equipment item, ordered from
     * most recent to oldest. Used by constraint and audit tests.
     */
    List<EquipmentStatusHistoryJpaEntity> findByEquipmentIdOrderByOccurredAtDesc(UUID equipmentId);
}
