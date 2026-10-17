package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.user.AuthenticationUserQuery;
import io.github.guillermodubon.coachgym.user.CompletePasswordRecoveryCommand;
import io.github.guillermodubon.coachgym.user.PasswordRecoveryStatus;
import io.github.guillermodubon.coachgym.user.RequestPasswordRecoveryCommand;
import io.github.guillermodubon.coachgym.user.StaffAccountSecurityState;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@ActiveProfiles("test")
class StaffPasswordRecoveryFlowIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");
    private static final String OLD_PASSWORD = "old-test-password-just-for-fixture";
    private static final String NEW_PASSWORD = "new-test-password-for-recovery-456";

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
    private StaffPasswordRecoveryTransactionService recoveryTransactions;

    @Autowired
    private AuthenticationUserQuery authenticationUsers;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearRecoveryState() {
        jdbcTemplate.execute("truncate table gym.staff_identity_abuse_windows, "
                + "gym.staff_password_recovery_tokens");
    }

    @Test
    void resetConsumesGeneratedTokenChangesPasswordAndInvalidatesOldSessionVersion() {
        UUID userId = createActiveUser();
        String email = emailFor(userId);
        var delivery = recoveryTransactions.request(new RequestPasswordRecoveryCommand(email)).orElseThrow();
        String token = delivery.token();
        String fingerprint = jdbcTemplate.queryForObject("""
                select token_fingerprint from gym.staff_password_recovery_tokens where user_id = ?
                """, String.class, userId);

        assertThat(fingerprint).isNotEqualTo(token).hasSize(64);
        assertThat(delivery.toString()).doesNotContain(email, token);
        CompletePasswordRecoveryCommand command =
                new CompletePasswordRecoveryCommand(token, NEW_PASSWORD, NEW_PASSWORD);

        assertThat(recoveryTransactions.complete(command)).isTrue();
        assertThat(recoveryTransactions.complete(command)).isFalse();

        String storedPasswordHash = jdbcTemplate.queryForObject(
                "select password_hash from gym.users where id = ?", String.class, userId);
        assertThat(passwordEncoder.matches(OLD_PASSWORD, storedPasswordHash)).isFalse();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, storedPasswordHash)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select status from gym.staff_password_recovery_tokens where user_id = ?",
                String.class, userId)).isEqualTo(PasswordRecoveryStatus.USED.name());
        assertThat(jdbcTemplate.queryForObject(
                "select security_version from gym.users where id = ?", Long.class, userId))
                .isEqualTo(1L);
        assertThat(authenticationUsers.findAccountSecurityState(userId))
                .contains(new StaffAccountSecurityState(StaffAccountStatus.ACTIVE, 1, false));
    }

    @Test
    void concurrentResetAttemptsHaveExactlyOneWinner() throws Exception {
        UUID userId = createActiveUser();
        String email = emailFor(userId);
        String token = recoveryTransactions.request(new RequestPasswordRecoveryCommand(email))
                .orElseThrow().token();
        CompletePasswordRecoveryCommand command =
                new CompletePasswordRecoveryCommand(token, NEW_PASSWORD, NEW_PASSWORD);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> completeAfterStart(ready, start, command));
            var second = executor.submit(() -> completeAfterStart(ready, start, command));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.staff_password_recovery_tokens "
                        + "where user_id = ? and status = 'USED'",
                Integer.class, userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select security_version from gym.users where id = ?", Long.class, userId))
                .isEqualTo(1L);
    }

    @Test
    void newRequestRevokesThePriorPendingTokenBeforeCreatingItsReplacement() {
        UUID userId = createActiveUser();
        String email = emailFor(userId);
        var firstDelivery = recoveryTransactions.request(new RequestPasswordRecoveryCommand(email)).orElseThrow();
        var replacementDelivery = recoveryTransactions.request(
                new RequestPasswordRecoveryCommand(email)).orElseThrow();

        assertThat(firstDelivery.token()).isNotEqualTo(replacementDelivery.token());
        assertThat(jdbcTemplate.queryForList("""
                select status from gym.staff_password_recovery_tokens
                 where user_id = ?
                """, String.class, userId))
                .containsExactlyInAnyOrder(
                        PasswordRecoveryStatus.REVOKED.name(), PasswordRecoveryStatus.PENDING.name());
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.staff_password_recovery_tokens
                 where user_id = ? and token_fingerprint in (?, ?)
                """, Integer.class, userId, firstDelivery.token(), replacementDelivery.token()))
                .isZero();
    }

    private boolean completeAfterStart(
            CountDownLatch ready,
            CountDownLatch start,
            CompletePasswordRecoveryCommand command) throws InterruptedException {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return recoveryTransactions.complete(command);
    }

    private UUID createActiveUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.users (
                    id, username, email, password_hash, first_name, last_name, status, version)
                values (?, ?, ?, ?, 'Recovery', 'Fixture', 'ACTIVE', 0)
                """, id, "recovery-" + id, emailFor(id), passwordEncoder.encode(OLD_PASSWORD));
        return id;
    }

    private static String emailFor(UUID id) {
        return "recovery-" + id + "@example.test";
    }
}
