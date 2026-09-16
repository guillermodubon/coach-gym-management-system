package io.github.guillermodubon.coachgym.accesscredential.infrastructure.persistence;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AccessCredentialJpaRepository extends JpaRepository<AccessCredentialJpaEntity, UUID> {

    Optional<AccessCredentialJpaEntity> findByClientIdAndStatus(
            UUID clientId,
            AccessCredentialStatus status);

    Optional<AccessCredentialJpaEntity> findByTokenFingerprint(String tokenFingerprint);

    Optional<AccessCredentialJpaEntity> findByTokenFingerprintAndStatus(
            String tokenFingerprint,
            AccessCredentialStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select credential
            from AccessCredentialJpaEntity credential
            where credential.tokenFingerprint = :tokenFingerprint
              and credential.status = :status
            """)
    Optional<AccessCredentialJpaEntity> findByTokenFingerprintAndStatusForUpdate(
            @Param("tokenFingerprint") String tokenFingerprint,
            @Param("status") AccessCredentialStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select credential
            from AccessCredentialJpaEntity credential
            where credential.id = :credentialId
            """)
    Optional<AccessCredentialJpaEntity> findByIdForUpdate(
            @Param("credentialId") UUID credentialId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select credential
            from AccessCredentialJpaEntity credential
            where credential.clientId = :clientId
              and credential.status = :status
            """)
    Optional<AccessCredentialJpaEntity> findActiveByClientIdForUpdate(
            @Param("clientId") UUID clientId,
            @Param("status") AccessCredentialStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select credential
            from AccessCredentialJpaEntity credential
            where credential.clientId = :clientId
            order by credential.issuedAt desc, credential.id desc
            """)
    Optional<AccessCredentialJpaEntity> findLatestByClientIdForUpdate(
            @Param("clientId") UUID clientId);
}
