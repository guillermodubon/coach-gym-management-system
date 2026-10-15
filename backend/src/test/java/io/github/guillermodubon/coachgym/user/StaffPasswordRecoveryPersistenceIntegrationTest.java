package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.user.application.StaffIdentityDataAccessException;
import io.github.guillermodubon.coachgym.user.application.StaffOneTimeTokenGenerator;
import io.github.guillermodubon.coachgym.user.application.StaffPasswordRecoveryDraft;
import io.github.guillermodubon.coachgym.user.application.StaffPasswordRecoveryPersistence;
import io.github.guillermodubon.coachgym.user.application.StaffPasswordRecoveryRecord;
import io.github.guillermodubon.coachgym.user.application.StaffTokenFingerprint;
import io.github.guillermodubon.coachgym.user.application.StaffTokenProtector;
import io.github.guillermodubon.coachgym.user.application.StaffTokenPurpose;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@ActiveProfiles("test")
class StaffPasswordRecoveryPersistenceIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StaffPasswordRecoveryPersistence recoveries;

    @Autowired
    private StaffOneTimeTokenGenerator tokenGenerator;

    @Autowired
    private StaffTokenProtector tokenProtector;

    @BeforeEach
    void clearStaffIdentityTokenRows() {
        jdbcTemplate.execute("truncate table gym.staff_account_activation_deliveries, "
                + "gym.staff_invitation_delivery_attempts, "
                + "gym.staff_invitation_branches, "
                + "gym.staff_invitations, gym.staff_password_recovery_tokens, "
                + "gym.staff_identity_abuse_windows");
    }

    @Test
    void recoveryIsUniquePerUserAndOnlyActiveAccountsCanCreateOne() {
        UUID activeUserId = createUser("ACTIVE");
        Instant requestedAt = now();
        String rawToken = tokenGenerator.generate();
        StaffTokenFingerprint fingerprint = tokenProtector.fingerprint(
                rawToken, StaffTokenPurpose.PASSWORD_RECOVERY);
        StaffPasswordRecoveryRecord created = recoveries.create(
                recoveryDraft(activeUserId, fingerprint, requestedAt));

        assertThat(created.status()).isEqualTo(PasswordRecoveryStatus.PENDING);
        assertThat(created.failedAttemptCount()).isZero();
        assertThat(created.tokenFingerprint()).isEqualTo(fingerprint);
        assertThat(created.toString()).doesNotContain(rawToken, fingerprint.value());
        assertThat(recoveries.findPendingByFingerprint(fingerprint)).contains(created);
        assertThatThrownBy(() -> recoveries.create(
                recoveryDraft(activeUserId, newFingerprint(), requestedAt)))
                .isInstanceOf(StaffIdentityDataAccessException.class)
                .hasMessageNotContaining(rawToken)
                .hasMessageNotContaining(fingerprint.value());

        UUID inactiveUserId = createUser("INACTIVE");
        assertThatThrownBy(() -> recoveries.create(
                recoveryDraft(inactiveUserId, newFingerprint(), requestedAt)))
                .isInstanceOf(StaffIdentityDataAccessException.class);
    }

    @Test
    void fifthInvalidAttemptRevokesTokenAndAdvancesVersionExactlyOnce() {
        UUID userId = createUser("ACTIVE");
        Instant requestedAt = now();
        StaffTokenFingerprint fingerprint = newFingerprint();
        StaffPasswordRecoveryRecord recovery = recoveries.create(
                recoveryDraft(userId, fingerprint, requestedAt));

        for (int attempt = 1; attempt <= 5; attempt++) {
            recovery = recoveries.recordFailedAttempt(
                    recovery.recoveryId(), requestedAt.plusSeconds(attempt), recovery.version());
            assertThat(recovery.failedAttemptCount()).isEqualTo(attempt);
            assertThat(recovery.version()).isEqualTo(attempt);
        }

        assertThat(recovery.status()).isEqualTo(PasswordRecoveryStatus.REVOKED);
        assertThat(recovery.revokedAt()).isEqualTo(requestedAt.plusSeconds(5));
        assertThat(recoveries.findPendingByFingerprint(fingerprint)).isEmpty();
        StaffPasswordRecoveryRecord finalRecovery = recovery;
        assertThatThrownBy(() -> recoveries.recordFailedAttempt(
                finalRecovery.recoveryId(), requestedAt.plusSeconds(6), finalRecovery.version()))
                .isInstanceOf(StaffIdentityStateConflictException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                update gym.staff_password_recovery_tokens
                   set failed_attempt_count = 0, version = version + 1
                 where id = ?
                """, finalRecovery.recoveryId())).isInstanceOf(DataAccessException.class);
    }

    @Test
    void useRequiresUnexpiredPendingStateAndFinalRowsAreImmutable() {
        UUID userId = createUser("ACTIVE");
        Instant requestedAt = now();
        StaffPasswordRecoveryRecord recovery = recoveries.create(
                recoveryDraft(userId, newFingerprint(), requestedAt));
        Instant usedAt = requestedAt.plusSeconds(10);

        assertThatThrownBy(() -> recoveries.transition(
                recovery.recoveryId(), PasswordRecoveryStatus.USED,
                requestedAt.minusSeconds(1), recovery.version()))
                .isInstanceOf(StaffIdentityDataAccessException.class);

        StaffPasswordRecoveryRecord used = recoveries.transition(
                recovery.recoveryId(), PasswordRecoveryStatus.USED, usedAt, recovery.version());

        assertThat(used.status()).isEqualTo(PasswordRecoveryStatus.USED);
        assertThat(used.usedAt()).isEqualTo(usedAt);
        assertThat(used.version()).isEqualTo(1);
        assertThatThrownBy(() -> recoveries.transition(
                used.recoveryId(), PasswordRecoveryStatus.REVOKED,
                usedAt.plusSeconds(1), used.version()))
                .isInstanceOf(StaffIdentityStateConflictException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.staff_password_recovery_tokens where id = ?", used.recoveryId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void explicitExpiryRequiresElapsedLifetime() {
        UUID userId = createUser("ACTIVE");
        Instant requestedAt = now();
        StaffPasswordRecoveryRecord recovery = recoveries.create(
                recoveryDraft(userId, newFingerprint(), requestedAt));
        Instant expiredAt = recovery.expiresAt().plusSeconds(1);

        StaffPasswordRecoveryRecord expired = recoveries.transition(
                recovery.recoveryId(), PasswordRecoveryStatus.EXPIRED,
                expiredAt, recovery.version());

        assertThat(expired.status()).isEqualTo(PasswordRecoveryStatus.EXPIRED);
        assertThat(expired.expiredAt()).isEqualTo(expiredAt);
        assertThat(expired.version()).isEqualTo(1);
    }

    private UUID createUser(String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.users (
                    id, username, email, password_hash, first_name, last_name, status, version)
                values (?, ?, ?, 'test-hash-not-a-credential', 'Test', 'Staff', ?, 0)
                """, id, "recovery-" + id, "recovery-" + id + "@example.test", status);
        return id;
    }

    private StaffPasswordRecoveryDraft recoveryDraft(
            UUID userId,
            StaffTokenFingerprint fingerprint,
            Instant requestedAt) {
        return new StaffPasswordRecoveryDraft(
                UUID.randomUUID(), userId, fingerprint, requestedAt,
                requestedAt.plus(PasswordRecoveryPolicy.DEFAULT_LIFETIME));
    }

    private StaffTokenFingerprint newFingerprint() {
        return tokenProtector.fingerprint(
                tokenGenerator.generate(), StaffTokenPurpose.PASSWORD_RECOVERY);
    }

    private static Instant now() {
        return Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MICROS);
    }
}
