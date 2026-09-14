package io.github.guillermodubon.coachgym.access;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.access.application.AccessApplicationService;
import io.github.guillermodubon.coachgym.access.application.QrAccessCheckInCommand;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialQrPayload;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialTokenProtector;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialPersistenceCommand;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStore;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.concurrent.DelegatingSecurityContextCallable;

@TestPropertySource(properties = "gym.access.duplicate-scan-window=PT30S")
@WithMockUser(roles = "ADMIN")
class QrAccessCheckInPersistenceIntegrationTest
        extends AbstractAccessApiIntegrationTest {

    private static final String TOKEN =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired
    private AccessApplicationService accessApplicationService;

    @Autowired
    private AccessCredentialStore credentialStore;

    @Autowired
    private AccessCredentialTokenProtector tokenProtector;

    @BeforeEach
    void clearCredentialRows() {
        jdbcTemplate.execute("truncate table gym.access_records, "
                + "gym.access_credential_history, gym.access_credentials");
    }

    @Test
    void persistsAllowedQrAttemptThenRecordsDuplicateUsingServerHistory() {
        ClientFixture client = createClient("ACTIVE");
        createMembership(
                client,
                "ACTIVE",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1));
        AccessCredentialQrPayload payload = AccessCredentialQrPayload.fromToken(TOKEN);
        var credential = issueCredential(client.id(), payload);
        AuthenticatedActor actor = actor();

        AccessRecordDetails first = accessApplicationService.checkInQr(
                new QrAccessCheckInCommand(payload), actor);
        assertThat(countAccessAudits()).isZero();
        AccessRecordDetails second = accessApplicationService.checkInQr(
                new QrAccessCheckInCommand(payload), actor);

        assertThat(first.result()).isEqualTo(AccessResult.ALLOWED);
        assertThat(second.result()).isEqualTo(AccessResult.DENIED);
        assertThat(second.reasonCode()).isEqualTo(AccessReasonCode.DUPLICATE_CHECK_IN);
        assertThat(countAccessRows()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select identification_source from gym.access_records where id=?",
                String.class,
                first.id())).isEqualTo("QR_CREDENTIAL");
        assertThat(jdbcTemplate.queryForObject(
                "select access_credential_id from gym.access_records where id=?",
                UUID.class,
                first.id())).isEqualTo(credential.id());
        assertThat(jdbcTemplate.queryForObject(
                "select entered_code from gym.access_records where id=?",
                String.class,
                first.id())).isEqualTo("QR_CREDENTIAL");
        // The duplicate denial is audited exactly once, with actor and
        // occurrence data from the persisted access event.
        assertThat(countAccessAudits()).isEqualTo(1);
        Map<String, Object> audit = accessAudit(second.id());
        assertThat(audit.get("actor_user_id"))
                .isEqualTo(userId(ADMIN_USERNAME));
        assertThat(audit.get("occurred_at")).isNotNull();
        String auditMetadata = jdbcTemplate.queryForObject(
                "select metadata::text from gym.audit_entries "
                        + "where action_code='ACCESS_DENIED' and resource_id=?",
                String.class,
                second.id());
        assertThat(auditMetadata)
                .contains("QR_CREDENTIAL", "DUPLICATE_CHECK_IN",
                        credential.id().toString(), "duplicate")
                .doesNotContain(TOKEN, "fingerprint", "payload");
        assertThat(auditMetadata).contains("\"duplicate\": true");
    }

    @Test
    void concurrentScansForOneCredentialProduceOneAllowedAndOneDuplicate() throws Exception {
        ClientFixture client = createClient("ACTIVE");
        createMembership(
                client,
                "ACTIVE",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1));
        AccessCredentialQrPayload payload = AccessCredentialQrPayload.fromToken(TOKEN);
        issueCredential(client.id(), payload);
        AuthenticatedActor actor = actor();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<AccessRecordDetails> call = () -> accessApplicationService.checkInQr(
                    new QrAccessCheckInCommand(payload), actor);
            Future<AccessRecordDetails> first = executor.submit(
                    new DelegatingSecurityContextCallable<>(call));
            Future<AccessRecordDetails> second = executor.submit(
                    new DelegatingSecurityContextCallable<>(call));

            assertThat(java.util.List.of(first.get(), second.get()))
                    .extracting(AccessRecordDetails::result)
                    .containsExactlyInAnyOrder(AccessResult.ALLOWED, AccessResult.DENIED);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from gym.access_records",
                    Integer.class)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void revokedCredentialCannotCreateAnotherQrAttempt() {
        ClientFixture client = createClient("ACTIVE");
        createMembership(
                client,
                "ACTIVE",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1));
        AccessCredentialQrPayload payload = AccessCredentialQrPayload.fromToken(TOKEN);
        var credential = issueCredential(client.id(), payload);
        accessApplicationService.checkInQr(
                new QrAccessCheckInCommand(payload), actor());

        credentialStore.revoke(
                credential.id(),
                "Replaced",
                userId(ADMIN_USERNAME),
                Instant.now(),
                credential.version());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                accessApplicationService.checkInQr(
                        new QrAccessCheckInCommand(payload), actor()))
                .isInstanceOf(
                        io.github.guillermodubon.coachgym.access.application.QrAccessCredentialUnavailableException.class);
        assertThat(countAccessRows()).isEqualTo(1);
    }

    private io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails issueCredential(
            UUID clientId,
            AccessCredentialQrPayload payload) {
        UUID id = UUID.randomUUID();
        return credentialStore.insert(new AccessCredentialPersistenceCommand(
                id,
                clientId,
                "CRED-" + id.toString().substring(0, 8),
                tokenProtector.fingerprint(payload.value()),
                "sha256-v1",
                "v1",
                Instant.now(),
                userId(ADMIN_USERNAME),
                "access-credentials/" + id + ".png",
                "image/png",
                128,
                "f".repeat(64),
                "qr-v1"));
    }

    private AuthenticatedActor actor() {
        return new AuthenticatedActor(userId(ADMIN_USERNAME), "admin");
    }
}
