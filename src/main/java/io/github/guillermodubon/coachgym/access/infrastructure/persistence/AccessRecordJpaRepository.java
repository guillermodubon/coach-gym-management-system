package io.github.guillermodubon.coachgym.access.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface AccessRecordJpaRepository
        extends JpaRepository<AccessRecordJpaEntity, UUID>,
        JpaSpecificationExecutor<AccessRecordJpaEntity> {
}
