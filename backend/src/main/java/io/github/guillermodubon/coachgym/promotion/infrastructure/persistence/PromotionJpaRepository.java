package io.github.guillermodubon.coachgym.promotion.infrastructure.persistence;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PromotionJpaRepository
        extends JpaRepository<PromotionJpaEntity, UUID> {

    @Query("""
            select promotion
            from PromotionJpaEntity promotion
            where (:active is null
                   or promotion.active = :active)
              and (:name = ''
                   or lower(promotion.name)
                       like lower(concat('%', :name, '%')))
              and (:discountType is null
                   or promotion.discountType = :discountType)
              and (
                   :filterByValidOn = false
                   or (
                       promotion.validFrom <= :validOn
                       and promotion.validUntil >= :validOn
                   )
              )
            """)
    Page<PromotionJpaEntity> search(
            @Param("active")
            Boolean active,

            @Param("name")
            String name,

            @Param("discountType")
            DiscountType discountType,

            @Param("filterByValidOn")
            boolean filterByValidOn,

            @Param("validOn")
            LocalDate validOn,

            Pageable pageable);

    @Override
    Optional<PromotionJpaEntity> findById(UUID id);
}