package io.github.guillermodubon.coachgym.equipment.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EquipmentCategoryJpaRepository
        extends JpaRepository<EquipmentCategoryJpaEntity, UUID> {

    @Query("""
            select c from EquipmentCategoryJpaEntity c
            where (:active is null or c.active = :active)
            """)
    Page<EquipmentCategoryJpaEntity> search(
            @Param("active") Boolean active,
            Pageable pageable);

    @Query("select count(c) > 0 from EquipmentCategoryJpaEntity c " +
           "where lower(c.name) = lower(:name) " +
           "and (:excludeId is null or c.id <> :excludeId)")
    boolean existsByNameIgnoreCase(
            @Param("name") String name,
            @Param("excludeId") UUID excludeId);

    @Override
    Optional<EquipmentCategoryJpaEntity> findById(UUID id);
}
