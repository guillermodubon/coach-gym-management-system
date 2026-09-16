package io.github.guillermodubon.coachgym.accesscredential.infrastructure.persistence;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialDataAccessException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialDuplicateException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialNotFoundException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialPersistenceCommand;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialQuery;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStateConflictException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStore;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStoredDocument;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialVersionConflictException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class AccessCredentialPersistenceAdapter
        implements AccessCredentialStore, AccessCredentialQuery {

    private final AccessCredentialJpaRepository credentialRepository;
    private final EntityManager entityManager;

    AccessCredentialPersistenceAdapter(
            AccessCredentialJpaRepository credentialRepository,
            EntityManager entityManager) {
        this.credentialRepository = credentialRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void lockClient(UUID clientId) {
        lockClient(clientId, false);
    }

    @Override
    @Transactional
    public void lockClientForLifecycle(UUID clientId) {
        lockClient(clientId, true);
    }

    private void lockClient(UUID clientId, boolean failFast) {
        requireIdentifier(clientId, "Client id");
        try {
            String lockClause = failFast ? " for update nowait" : " for update";
            entityManager.createNativeQuery(
                            "select id from gym.clients where id = :clientId" + lockClause)
                    .setParameter("clientId", clientId)
                    .getResultList();
            // A missing client is resolved by the application service after
            // the lock attempt so the HTTP boundary can return CLIENT_NOT_FOUND
            // instead of conflating it with a missing credential.
        } catch (AccessCredentialNotFoundException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            if (isConcurrencyFailure(exception)) {
                throw new AccessCredentialVersionConflictException(clientId, -1, -1);
            }
            throw dataAccess("Client lock could not be acquired.", exception);
        } catch (RuntimeException exception) {
            // EntityManager calls can expose Hibernate's lock-timeout wrapper
            // before repository exception translation runs. Normalize both
            // paths to the same privacy-safe lifecycle conflict.
            if (isConcurrencyFailure(exception)) {
                throw new AccessCredentialVersionConflictException(clientId, -1, -1);
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccessCredentialDetails> findById(UUID credentialId) {
        requireIdentifier(credentialId, "Credential id");
        return safelyFind(() -> credentialRepository.findById(credentialId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccessCredentialDetails> findActiveByClientId(UUID clientId) {
        requireIdentifier(clientId, "Client id");
        return safelyFind(() -> credentialRepository.findByClientIdAndStatus(
                clientId, AccessCredentialStatus.ACTIVE));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccessCredentialDetails> findActiveByTokenFingerprint(
            String tokenFingerprint) {
        String normalized = requireFingerprint(tokenFingerprint);
        return safelyFind(() -> credentialRepository.findByTokenFingerprintAndStatus(
                normalized, AccessCredentialStatus.ACTIVE));
    }

    @Override
    @Transactional
    public Optional<AccessCredentialDetails> findActiveByTokenFingerprintForUpdate(
            String tokenFingerprint) {
        String normalized = requireFingerprint(tokenFingerprint);
        try {
            return credentialRepository
                    .findByTokenFingerprintAndStatusForUpdate(
                            normalized, AccessCredentialStatus.ACTIVE)
                    .map(AccessCredentialJpaEntity::toDetails);
        } catch (DataAccessException exception) {
            throw dataAccess("Access credential could not be locked.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccessCredentialStoredDocument> findArtifactByCredentialId(
            UUID credentialId) {
        requireIdentifier(credentialId, "Credential id");
        return safelyFindArtifact(() -> credentialRepository.findById(credentialId));
    }

    @Override
    @Transactional
    public Optional<AccessCredentialDetails> findByIdForUpdate(UUID credentialId) {
        requireIdentifier(credentialId, "Credential id");
        try {
            return credentialRepository.findByIdForUpdate(credentialId)
                    .map(AccessCredentialJpaEntity::toDetails);
        } catch (DataAccessException exception) {
            if (isConcurrencyFailure(exception)) {
                throw new AccessCredentialVersionConflictException(credentialId, -1, -1);
            }
            throw dataAccess("Access credential could not be loaded.", exception);
        }
    }

    @Override
    @Transactional
    public Optional<AccessCredentialDetails> findActiveByClientIdForUpdate(UUID clientId) {
        requireIdentifier(clientId, "Client id");
        return safelyFind(() -> credentialRepository.findActiveByClientIdForUpdate(
                clientId, AccessCredentialStatus.ACTIVE));
    }

    @Override
    @Transactional
    public Optional<AccessCredentialDetails> findLatestByClientIdForUpdate(UUID clientId) {
        requireIdentifier(clientId, "Client id");
        try {
            return credentialRepository.findLatestByClientIdForUpdate(clientId)
                    .map(AccessCredentialJpaEntity::toDetails);
        } catch (DataAccessException exception) {
            if (isConcurrencyFailure(exception)) {
                throw new AccessCredentialVersionConflictException(clientId, -1, -1);
            }
            throw dataAccess("Access credential could not be loaded.", exception);
        }
    }

    @Override
    @Transactional
    public AccessCredentialDetails insert(AccessCredentialPersistenceCommand command) {
        try {
            return credentialRepository
                    .saveAndFlush(AccessCredentialJpaEntity.create(command))
                    .toDetails();
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                throw new AccessCredentialDuplicateException(uniqueKind(exception));
            }
            throw dataAccess("Access credential could not be persisted.", exception);
        } catch (DataAccessException exception) {
            throw dataAccess("Access credential could not be persisted.", exception);
        }
    }

    @Override
    @Transactional
    public AccessCredentialDetails revoke(
            UUID credentialId,
            String reason,
            UUID actorId,
            Instant revokedAt,
            long expectedVersion) {

        AccessCredentialJpaEntity entity = lockedEntity(credentialId);
        ensureVersion(entity, expectedVersion);
        if (entity.status() != AccessCredentialStatus.ACTIVE) {
            throw new AccessCredentialStateConflictException(
                    credentialId,
                    entity.status(),
                    AccessCredentialStatus.REVOKED);
        }
        try {
            entity.revoke(reason, actorId, revokedAt);
            credentialRepository.saveAndFlush(entity);
            return entity.toDetails();
        } catch (OptimisticLockingFailureException | OptimisticLockException exception) {
            throw new AccessCredentialVersionConflictException(
                    credentialId, expectedVersion, entity.version());
        } catch (DataAccessException exception) {
            if (isConcurrencyFailure(exception)) {
                throw new AccessCredentialVersionConflictException(
                        credentialId, expectedVersion, entity.version());
            }
            throw dataAccess("Access credential could not be revoked.", exception);
        }
    }

    @Override
    @Transactional
    public AccessCredentialDetails attachReplacement(
            UUID credentialId,
            UUID replacementCredentialId,
            long expectedVersion) {

        requireIdentifier(replacementCredentialId, "Replacement credential id");
        AccessCredentialJpaEntity entity = lockedEntity(credentialId);
        ensureVersion(entity, expectedVersion);
        if (entity.status() != AccessCredentialStatus.REVOKED
                || entity.replacedByCredentialId() != null) {
            throw new AccessCredentialStateConflictException(
                    credentialId,
                    entity.status(),
                    AccessCredentialStatus.REVOKED);
        }
        AccessCredentialJpaEntity replacement = credentialRepository
                .findById(replacementCredentialId)
                .orElseThrow(() -> new AccessCredentialNotFoundException(replacementCredentialId));
        if (replacement.status() != AccessCredentialStatus.ACTIVE
                || !entity.clientId().equals(replacement.clientId())) {
            throw new AccessCredentialStateConflictException(
                    replacementCredentialId,
                    replacement.status(),
                    AccessCredentialStatus.ACTIVE);
        }
        try {
            entity.attachReplacement(replacementCredentialId);
            credentialRepository.saveAndFlush(entity);
            return entity.toDetails();
        } catch (OptimisticLockingFailureException | OptimisticLockException exception) {
            throw new AccessCredentialVersionConflictException(
                    credentialId, expectedVersion, entity.version());
        } catch (DataAccessException exception) {
            if (isConcurrencyFailure(exception)) {
                throw new AccessCredentialVersionConflictException(
                        credentialId, expectedVersion, entity.version());
            }
            throw dataAccess("Access credential replacement could not be linked.", exception);
        }
    }

    private AccessCredentialJpaEntity lockedEntity(UUID credentialId) {
        requireIdentifier(credentialId, "Credential id");
        try {
            return credentialRepository.findByIdForUpdate(credentialId)
                    .orElseThrow(() -> new AccessCredentialNotFoundException(credentialId));
        } catch (AccessCredentialNotFoundException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            if (isConcurrencyFailure(exception)) {
                throw new AccessCredentialVersionConflictException(credentialId, -1, -1);
            }
            throw dataAccess("Access credential could not be loaded.", exception);
        }
    }

    private static void ensureVersion(
            AccessCredentialJpaEntity entity,
            long expectedVersion) {
        if (expectedVersion < 0 || entity.version() != expectedVersion) {
            throw new AccessCredentialVersionConflictException(
                    entity.id(), expectedVersion, entity.version());
        }
    }

    private static Optional<AccessCredentialDetails> safelyFind(
            java.util.function.Supplier<Optional<AccessCredentialJpaEntity>> query) {
        try {
            return query.get().map(AccessCredentialJpaEntity::toDetails);
        } catch (DataAccessException exception) {
            throw dataAccess("Access credential could not be read.", exception);
        }
    }

    private static Optional<AccessCredentialStoredDocument> safelyFindArtifact(
            java.util.function.Supplier<Optional<AccessCredentialJpaEntity>> query) {
        try {
            return query.get().map(AccessCredentialJpaEntity::toStoredDocument);
        } catch (DataAccessException exception) {
            throw dataAccess("Access credential artifact metadata could not be read.", exception);
        }
    }

    private static String requireFingerprint(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Credential fingerprint has an invalid format.");
        }
        return value;
    }

    private static UUID requireIdentifier(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }

    private static AccessCredentialDataAccessException dataAccess(
            String message,
            Throwable cause) {
        return new AccessCredentialDataAccessException(message, cause);
    }

    private static boolean isUniqueViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException exception
                    && "23505".equals(exception.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isConcurrencyFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof CannotAcquireLockException
                    || current instanceof OptimisticLockingFailureException
                    || current instanceof OptimisticLockException) {
                return true;
            }
            if (current instanceof SQLException exception
                    && ("40P01".equals(exception.getSQLState())
                    || "40001".equals(exception.getSQLState())
                    || "55P03".equals(exception.getSQLState()))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static AccessCredentialDuplicateException.Kind uniqueKind(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            String text = current.toString();
            if (text.contains("uq_access_credentials_active_client")) {
                return AccessCredentialDuplicateException.Kind.ACTIVE_CLIENT;
            }
            if (text.contains("uq_access_credentials_code")) {
                return AccessCredentialDuplicateException.Kind.CREDENTIAL_CODE;
            }
            if (text.contains("uq_access_credentials_token_fingerprint")) {
                return AccessCredentialDuplicateException.Kind.TOKEN_FINGERPRINT;
            }
            current = current.getCause();
        }
        return AccessCredentialDuplicateException.Kind.UNKNOWN;
    }
}
