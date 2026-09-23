package io.github.guillermodubon.coachgym.equipment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.equipment.EquipmentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EquipmentJpaRepository extends JpaRepository<EquipmentJpaEntity, UUID> {

    /**
     * Paginated search with all allowlisted filters applied as AND conditions.
     *
     * <p>Null parameters are treated as "no filter" via JPQL coalesce/null-check
     * pattern. The {@code search} parameter is used for ILIKE against {@code name};
     * {@code location} is used for ILIKE against {@code location}.</p>
     *
     * <p>Secondary sort ({@code id ASC}) is always appended by the adapter via
     * {@link org.springframework.data.domain.Sort}.</p>
     */
    @Query("""
            select e from EquipmentJpaEntity e
            where (:branchId is null or e.branchId = :branchId)
              and (:categoryId is null or e.categoryId = :categoryId)
              and (:status is null or e.status = :status)
              and (:search = '' or lower(e.name) like lower(concat('%', :search, '%')))
              and (:location = '' or lower(e.location) like lower(concat('%', :location, '%')))
            """)
    Page<EquipmentJpaEntity> search(
            @Param("branchId") UUID branchId,
            @Param("categoryId") UUID categoryId,
            @Param("status") EquipmentStatus status,
            @Param("search") String search,
            @Param("location") String location,
            Pageable pageable);

    @Query("select count(e) > 0 from EquipmentJpaEntity e " +
           "where lower(e.serialNumber) = lower(:serialNumber) " +
           "and (:excludeId is null or e.id <> :excludeId)")
    boolean existsBySerialNumberIgnoreCase(
            @Param("serialNumber") String serialNumber,
            @Param("excludeId") UUID excludeId);

    @Query("select count(e) > 0 from EquipmentJpaEntity e "
            + "where e.branchId = :branchId "
            + "and lower(e.serialNumber) = lower(:serialNumber) "
            + "and (:excludeId is null or e.id <> :excludeId)")
    boolean existsBySerialNumberIgnoreCaseInBranch(
            @Param("serialNumber") String serialNumber,
            @Param("excludeId") UUID excludeId,
            @Param("branchId") UUID branchId);

    @Query("select e from EquipmentJpaEntity e where e.id = :id and e.branchId = :branchId")
    Optional<EquipmentJpaEntity> findByIdAndBranchId(
            @Param("id") UUID id,
            @Param("branchId") UUID branchId);

    @Override
    Optional<EquipmentJpaEntity> findById(UUID id);
}
