package io.github.guillermodubon.coachgym.accesscredential.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialDuplicateException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialPersistenceCommand;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStateConflictException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialVersionConflictException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AccessCredentialPersistenceAdapterTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-09-13T10:00:00Z");

    @Mock
    private AccessCredentialJpaRepository credentialRepository;

    @Mock
    private EntityManager entityManager;

    private AccessCredentialPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccessCredentialPersistenceAdapter(credentialRepository, entityManager);
    }

    @Test
    void insertsSafeMetadataAndMapsActiveDetails() {
        AccessCredentialPersistenceCommand command = command();
        when(credentialRepository.saveAndFlush(any(AccessCredentialJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var details = adapter.insert(command);

        assertThat(details.id()).isEqualTo(command.id());
        assertThat(details.clientId()).isEqualTo(command.clientId());
        assertThat(details.status()).isEqualTo(AccessCredentialStatus.ACTIVE);
        assertThat(details.version()).isZero();
    }

    @Test
    void readsArtifactMetadataWithoutExposingCredentialToken() {
        AccessCredentialPersistenceCommand command = command();
        AccessCredentialJpaEntity entity = AccessCredentialJpaEntity.create(command);
        when(credentialRepository.findById(command.id())).thenReturn(Optional.of(entity));

        var metadata = adapter.findArtifactByCredentialId(command.id()).orElseThrow();

        assertThat(metadata.storageKey()).isEqualTo(command.storageKey());
        assertThat(metadata.contentType()).isEqualTo(command.contentType());
        assertThat(metadata.checksumSha256()).isEqualTo(command.checksumSha256());
        assertThat(metadata.toString()).doesNotContain(command.checksumSha256());
    }

    @Test
    void revokesOnlyWithTheExpectedVersionAndPersistsFinalState() {
        AccessCredentialJpaEntity entity = AccessCredentialJpaEntity.create(command());
        when(credentialRepository.findByIdForUpdate(entity.id())).thenReturn(Optional.of(entity));
        when(credentialRepository.saveAndFlush(entity)).thenReturn(entity);

        var details = adapter.revoke(
                entity.id(),
                "Lost card",
                UUID.randomUUID(),
                ISSUED_AT.plusSeconds(60),
                0);

        assertThat(details.status()).isEqualTo(AccessCredentialStatus.REVOKED);
        assertThat(details.revokedAt()).isEqualTo(ISSUED_AT.plusSeconds(60));
        verify(credentialRepository).saveAndFlush(entity);
    }

    @Test
    void rejectsStaleVersionBeforeChangingTheManagedEntity() {
        AccessCredentialJpaEntity entity = AccessCredentialJpaEntity.create(command());
        when(credentialRepository.findByIdForUpdate(entity.id())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> adapter.revoke(
                entity.id(),
                "Lost card",
                UUID.randomUUID(),
                ISSUED_AT.plusSeconds(60),
                1))
                .isInstanceOf(AccessCredentialVersionConflictException.class);
        assertThat(entity.toDetails().status()).isEqualTo(AccessCredentialStatus.ACTIVE);
    }

    @Test
    void rejectsASecondRevocationAsAStateConflict() {
        AccessCredentialJpaEntity entity = AccessCredentialJpaEntity.create(command());
        entity.revoke("Lost card", UUID.randomUUID(), ISSUED_AT.plusSeconds(60));
        when(credentialRepository.findByIdForUpdate(entity.id())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> adapter.revoke(
                entity.id(),
                "Again",
                UUID.randomUUID(),
                ISSUED_AT.plusSeconds(120),
                entity.version()))
                .isInstanceOf(AccessCredentialStateConflictException.class);
    }

    @Test
    void translatesUniqueDatabaseConflictsWithoutEchoingProtectedValues() {
        AccessCredentialPersistenceCommand command = command();
        SQLException sqlException = new SQLException(
                "duplicate key value violates constraint uq_access_credentials_token_fingerprint",
                "23505");
        when(credentialRepository.saveAndFlush(any(AccessCredentialJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("Credential conflict", sqlException));

        assertThatThrownBy(() -> adapter.insert(command))
                .isInstanceOf(AccessCredentialDuplicateException.class)
                .hasMessage("Access credential already exists for the requested unique key.")
                .hasMessageNotContaining(command.tokenFingerprint());
    }

    private static AccessCredentialPersistenceCommand command() {
        UUID id = UUID.randomUUID();
        return new AccessCredentialPersistenceCommand(
                id,
                UUID.randomUUID(),
                "CRED-ADAPTER-1",
                "a".repeat(64),
                "sha256-v1",
                "v1",
                ISSUED_AT,
                UUID.randomUUID(),
                "access-credentials/" + id + ".png",
                "image/png",
                128,
                "b".repeat(64),
                "qr-v1");
    }
}
