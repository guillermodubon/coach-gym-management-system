package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.RoleCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleJpaRepository extends JpaRepository<RoleEntity, UUID> {

    Optional<RoleEntity> findByRoleCode(RoleCode roleCode);
}
