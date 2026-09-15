package io.github.guillermodubon.coachgym.accesscredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialHistoryPersistenceCommand;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialHistoryQuery;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialHistoryStore;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialPersistenceCommand;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialQuery;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStore;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialVersionConflictException;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class AccessCredentialPersistenceIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-09-13T10:00:00Z");

    @Autowired
    private AccessCredentialStore credentialStore;

    @Autowired
    private AccessCredentialQuery credentialQuery;

    @Autowired
    private AccessCredentialResolver resolver;

    @Autowired
    private AccessCredentialTokenProtector tokenProtector;

    @Autowired
    private AccessCredentialHistoryStore historyStore;

    @Autowired
    private AccessCredentialHistoryQuery historyQuery;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clearCredentialRows() {
        jdbcTemplate.execute("truncate table gym.access_records, "
                + "gym.access_credential_history, gym.access_credentials");
    }

    @Test
    void persistsAndQueriesSafeMetadataWithProtectedLookup() {
        UUID clientId = createClient();
        AccessCredentialPersistenceCommand command = command(clientId, "a");

        credentialStore.lockClient(clientId);
        AccessCredentialDetails inserted = credentialStore.insert(command);
        historyStore.append(initialHistory(inserted));

        assertThat(credentialQuery.findById(inserted.id()))
                .hasValueSatisfying(details -> {
                    assertThat(details.clientId()).isEqualTo(clientId);
                    assertThat(details.status()).isEqualTo(AccessCredentialStatus.ACTIVE);
                    assertThat(details.version()).isZero();
                });
        assertThat(credentialQuery.findActiveByTokenFingerprint(command.tokenFingerprint()))
                .hasValueSatisfying(details -> assertThat(details.id()).isEqualTo(inserted.id()));
        assertThat(historyQuery.findByCredentialId(inserted.id(), 0, 10).items())
                .singleElement()
                .satisfies(history -> assertThat(history.newStatus())
                        .isEqualTo(AccessCredentialStatus.ACTIVE));
    }

    @Test
    void resolvesPersistedActiveCredentialFromTheCanonicalPayload() {
        UUID clientId = createClient();
        AccessCredentialQrPayload payload = AccessCredentialQrPayload.parse(
                "cgac:v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        AccessCredentialDetails inserted = credentialStore.insert(command(
                clientId,
                "g",
                tokenProtector.fingerprint(payload.value())));

        assertThat(resolver.resolve(payload))
                .hasValueSatisfying(resolved -> {
                    assertThat(resolved.credentialId()).isEqualTo(inserted.id());
                    assertThat(resolved.clientId()).isEqualTo(clientId);
                    assertThat(resolved.status()).isEqualTo(AccessCredentialStatus.ACTIVE);
                });
    }

    @Test
    void revocationUsesOptimisticVersionAndHistoryRemainsNewestFirst() {
        UUID clientId = createClient();
        AccessCredentialDetails inserted = credentialStore.insert(command(clientId, "b"));
        historyStore.append(initialHistory(inserted));

        AccessCredentialDetails revoked = credentialStore.revoke(
                inserted.id(),
                "Lost card",
                adminId,
                ISSUED_AT.plusSeconds(60),
                inserted.version());
        historyStore.append(new AccessCredentialHistoryPersistenceCommand(
                inserted.id(),
                clientId,
                AccessCredentialStatus.ACTIVE,
                AccessCredentialStatus.REVOKED,
                "Lost card",
                ISSUED_AT.plusSeconds(60),
                adminId,
                null));

        assertThat(revoked.status()).isEqualTo(AccessCredentialStatus.REVOKED);
        assertThat(revoked.version()).isEqualTo(1);
        assertThatThrownBy(() -> credentialStore.revoke(
                inserted.id(), "Again", adminId, ISSUED_AT.plusSeconds(120), 0))
                .isInstanceOf(AccessCredentialVersionConflictException.class);
        assertThat(historyQuery.findByCredentialId(inserted.id(), 0, 10).items())
                .extracting(AccessCredentialHistoryDetails::newStatus)
                .containsExactly(AccessCredentialStatus.REVOKED, AccessCredentialStatus.ACTIVE);
    }

    @Test
    void replacementSequenceIsAtomicAndKeepsOnlyTheNewCredentialActive() {
        UUID clientId = createClient();
        AccessCredentialDetails original = credentialStore.insert(command(clientId, "c"));
        historyStore.append(initialHistory(original));
        AccessCredentialDetails linked = transactionTemplate.execute(status -> {
            AccessCredentialDetails revoked = credentialStore.revoke(
                    original.id(),
                    "Replace card",
                    adminId,
                    ISSUED_AT.plusSeconds(60),
                    original.version());
            AccessCredentialDetails replacement = credentialStore.insert(command(clientId, "d"));
            historyStore.append(initialHistory(replacement));
            AccessCredentialDetails finalCredential = credentialStore.attachReplacement(
                    original.id(), replacement.id(), revoked.version());
            historyStore.append(new AccessCredentialHistoryPersistenceCommand(
                    original.id(), clientId, AccessCredentialStatus.ACTIVE,
                    AccessCredentialStatus.REVOKED, "Replace card",
                    ISSUED_AT.plusSeconds(60), adminId, replacement.id()));
            return finalCredential;
        });

        assertThat(linked).isNotNull();
        assertThat(linked.replacedByCredentialId()).isNotNull();
        assertThat(credentialQuery.findActiveByClientId(clientId))
                .hasValueSatisfying(details -> assertThat(details.id())
                        .isEqualTo(linked.replacedByCredentialId()));
    }

    @Test
    void transactionRollbackLeavesOriginalCredentialUntouched() {
        UUID clientId = createClient();
        AccessCredentialDetails original = credentialStore.insert(command(clientId, "e"));
        historyStore.append(initialHistory(original));

        assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> {
            credentialStore.revoke(
                    original.id(), "Failed replacement", adminId,
                    ISSUED_AT.plusSeconds(60), original.version());
            credentialStore.insert(command(clientId, "f"));
            status.setRollbackOnly();
        })).doesNotThrowAnyException();

        assertThat(credentialQuery.findById(original.id()))
                .hasValueSatisfying(details -> assertThat(details.status())
                        .isEqualTo(AccessCredentialStatus.ACTIVE));
        assertThat(historyQuery.findByCredentialId(original.id(), 0, 10).items())
                .hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.access_credentials where client_id = ?",
                Integer.class,
                clientId)).isEqualTo(1);
    }

    private AccessCredentialHistoryPersistenceCommand initialHistory(
            AccessCredentialDetails details) {
        return new AccessCredentialHistoryPersistenceCommand(
                details.id(),
                details.clientId(),
                null,
                AccessCredentialStatus.ACTIVE,
                null,
                details.issuedAt(),
                details.issuedByUserId(),
                null);
    }

    private AccessCredentialPersistenceCommand command(UUID clientId, String suffix) {
        return command(clientId, suffix, suffix.repeat(64));
    }

    private AccessCredentialPersistenceCommand command(
            UUID clientId,
            String suffix,
            String fingerprint) {
        UUID id = UUID.randomUUID();
        return new AccessCredentialPersistenceCommand(
                id,
                clientId,
                "CRED-" + suffix.toUpperCase() + "-" + id.toString().substring(0, 8),
                fingerprint,
                "sha256-v1",
                "v1",
                ISSUED_AT,
                adminId,
                "access-credentials/" + id + ".png",
                "image/png",
                128,
                "f".repeat(64),
                "qr-v1");
    }

    private UUID createClient() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.clients
                    (id, first_name, last_name, phone, status,
                     created_by_user_id, updated_by_user_id, created_at, updated_at, version)
                values (?, 'Credential', 'Client', '+50370003001', 'ACTIVE', ?, ?, ?, ?, 0)
                """, id, adminId, adminId,
                java.sql.Timestamp.from(ISSUED_AT),
                java.sql.Timestamp.from(ISSUED_AT));
        return id;
    }
}
