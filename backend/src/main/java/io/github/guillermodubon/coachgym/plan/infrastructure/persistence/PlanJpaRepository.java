package io.github.guillermodubon.coachgym.plan.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PlanJpaRepository extends JpaRepository<MembershipPlanJpaEntity, UUID> {

    @Query("""
            select plan from MembershipPlanJpaEntity plan
            where (:active is null or plan.active = :active)
              and (:name = '' or lower(plan.name) like lower(concat('%', :name, '%')))
            """)
    Page<MembershipPlanJpaEntity> search(
            @Param("active") Boolean active,
            @Param("name") String name,
            Pageable pageable);

    @Override
    Optional<MembershipPlanJpaEntity> findById(UUID id);
}
