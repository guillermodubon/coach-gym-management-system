package io.github.guillermodubon.coachgym.access.infrastructure.persistence;

import java.util.UUID;
import java.time.Instant;
import java.util.Optional;
import io.github.guillermodubon.coachgym.access.domain.AccessIdentifierType;
import io.github.guillermodubon.coachgym.access.AccessResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface AccessRecordJpaRepository
        extends JpaRepository<AccessRecordJpaEntity, UUID>,
        JpaSpecificationExecutor<AccessRecordJpaEntity> {

    Optional<AccessRecordJpaEntity>
    findFirstByAccessCredentialIdAndIdentificationSourceAndResultAndCheckedInAtGreaterThanEqualOrderByCheckedInAtDescIdAsc(
            UUID accessCredentialId,
            AccessIdentifierType identificationSource,
            AccessResult result,
            Instant occurredAtFromInclusive);
}
