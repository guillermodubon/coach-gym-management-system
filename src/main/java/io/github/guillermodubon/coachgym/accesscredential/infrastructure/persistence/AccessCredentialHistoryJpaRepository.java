package io.github.guillermodubon.coachgym.accesscredential.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccessCredentialHistoryJpaRepository
        extends JpaRepository<AccessCredentialHistoryJpaEntity, UUID> {

    Page<AccessCredentialHistoryJpaEntity> findByCredentialId(
            UUID credentialId,
            Pageable pageable);

    Page<AccessCredentialHistoryJpaEntity> findByClientId(
            UUID clientId,
            Pageable pageable);
}
