package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, UUID> {

    @EntityGraph(attributePaths = {"roleAssignments", "roleAssignments.role"})
    @Query("""
            select user
            from UserAccountEntity user
            where user.status = io.github.guillermodubon.coachgym.user.infrastructure.persistence.UserStatus.ACTIVE
              and (lower(user.username) = lower(:identifier) or lower(user.email) = lower(:identifier))
            """)
    Optional<UserAccountEntity> findActiveByIdentifier(@Param("identifier") String identifier);
}
