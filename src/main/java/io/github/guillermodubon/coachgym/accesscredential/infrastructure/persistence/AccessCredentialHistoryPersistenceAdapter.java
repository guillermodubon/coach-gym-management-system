package io.github.guillermodubon.coachgym.accesscredential.infrastructure.persistence;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialHistoryDetails;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialDataAccessException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialHistoryPersistenceCommand;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialHistoryPage;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialHistoryQuery;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialHistoryStore;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class AccessCredentialHistoryPersistenceAdapter
        implements AccessCredentialHistoryStore, AccessCredentialHistoryQuery {

    private final AccessCredentialHistoryJpaRepository historyRepository;

    AccessCredentialHistoryPersistenceAdapter(
            AccessCredentialHistoryJpaRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @Override
    @Transactional
    public AccessCredentialHistoryDetails append(
            AccessCredentialHistoryPersistenceCommand command) {
        try {
            return historyRepository
                    .saveAndFlush(AccessCredentialHistoryJpaEntity.create(command))
                    .toDetails();
        } catch (DataAccessException exception) {
            throw new AccessCredentialDataAccessException(
                    "Access credential history could not be persisted.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AccessCredentialHistoryPage findByCredentialId(
            UUID credentialId,
            int page,
            int size) {
        return findPage(credentialId, page, size, historyRepository::findByCredentialId,
                "Credential id");
    }

    @Override
    @Transactional(readOnly = true)
    public AccessCredentialHistoryPage findByClientId(
            UUID clientId,
            int page,
            int size) {
        return findPage(clientId, page, size, historyRepository::findByClientId,
                "Client id");
    }

    private AccessCredentialHistoryPage findPage(
            UUID identifier,
            int page,
            int size,
            java.util.function.BiFunction<UUID, Pageable, Page<AccessCredentialHistoryJpaEntity>> finder,
            String label) {
        if (identifier == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Credential history pagination is invalid.");
        }
        try {
            Page<AccessCredentialHistoryJpaEntity> result = finder.apply(
                    identifier,
                    PageRequest.of(
                            page,
                            size,
                            Sort.by(Sort.Direction.DESC, "occurredAt")
                                    .and(Sort.by(Sort.Direction.ASC, "id"))));
            return new AccessCredentialHistoryPage(
                    result.getContent().stream()
                            .map(AccessCredentialHistoryJpaEntity::toDetails)
                            .toList(),
                    result.getNumber(),
                    result.getSize(),
                    result.getTotalElements(),
                    result.getTotalPages());
        } catch (DataAccessException exception) {
            throw new AccessCredentialDataAccessException(
                    "Access credential history could not be read.", exception);
        }
    }
}
