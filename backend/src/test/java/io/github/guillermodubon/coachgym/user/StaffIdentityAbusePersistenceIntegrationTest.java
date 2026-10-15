package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.user.application.StaffIdentityAbuseBucket;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityAbuseStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@ActiveProfiles("test")
class StaffIdentityAbusePersistenceIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

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
    private StaffIdentityAbuseStore store;

    @BeforeEach
    void clearWindows() {
        jdbcTemplate.update("delete from gym.staff_identity_abuse_windows");
    }

    @Test
    void perSubjectCounterRejectsAfterTheConfiguredCapAndResetsAtExpiry() {
        for (int count = 1; count <= 3; count++) {
            assertThat(store.consume(
                    StaffIdentityAbuseBucket.RECOVERY_EMAIL_REQUEST,
                    "bounded@example.test", 3, Duration.ofHours(1), NOW)).isTrue();
        }
        assertThat(store.consume(
                StaffIdentityAbuseBucket.RECOVERY_EMAIL_REQUEST,
                "bounded@example.test", 3, Duration.ofHours(1), NOW)).isFalse();
        assertThat(store.isAllowed(
                StaffIdentityAbuseBucket.RECOVERY_EMAIL_REQUEST,
                "bounded@example.test", 3, NOW)).isFalse();

        assertThat(store.consume(
                StaffIdentityAbuseBucket.RECOVERY_EMAIL_REQUEST,
                "bounded@example.test", 3, Duration.ofHours(1), NOW.plus(Duration.ofHours(1))))
                .isTrue();
        Integer attempts = jdbcTemplate.queryForObject("""
                select attempt_count from gym.staff_identity_abuse_windows
                 where bucket_type = 'RECOVERY_EMAIL_REQUEST' and subject_key = 'bounded@example.test'
                """, Integer.class);
        assertThat(attempts).isEqualTo(1);
    }

    @Test
    void tokenFingerprintsAreIndependentAndExpiredSubjectsArePruned() {
        for (int attempt = 0; attempt < 5; attempt++) {
            store.recordFailure(
                    StaffIdentityAbuseBucket.RECOVERY_TOKEN_FAILURE,
                    "a".repeat(64), 5, Duration.ofHours(48), NOW);
        }
        assertThat(store.isAllowed(
                StaffIdentityAbuseBucket.RECOVERY_TOKEN_FAILURE, "a".repeat(64), 5, NOW)).isFalse();
        assertThat(store.isAllowed(
                StaffIdentityAbuseBucket.RECOVERY_TOKEN_FAILURE, "b".repeat(64), 5, NOW)).isTrue();

        store.consume(
                StaffIdentityAbuseBucket.RECOVERY_IP_REQUEST,
                "127.0.0.1", 10, Duration.ofMinutes(15), NOW.plus(Duration.ofHours(49)));
        Integer expired = jdbcTemplate.queryForObject("""
                select count(*) from gym.staff_identity_abuse_windows
                 where expires_at <= ?
                """, Integer.class, java.sql.Timestamp.from(NOW.plus(Duration.ofHours(49))));
        assertThat(expired).isZero();
    }

    @Test
    void concurrentConsumersCannotExceedOneAtomicAllowance() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> attempts = IntStream.range(0, 32)
                    .mapToObj(index -> (Callable<Boolean>) () -> store.consume(
                            StaffIdentityAbuseBucket.RECOVERY_GLOBAL_REQUEST,
                            "GLOBAL", 1, Duration.ofHours(1), NOW))
                    .toList();
            long allowed = executor.invokeAll(attempts).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception failure) {
                            throw new IllegalStateException("Rate-limit attempt failed.");
                        }
                    })
                    .filter(Boolean.TRUE::equals)
                    .count();
            assertThat(allowed).isEqualTo(1);
        }
    }

    @Test
    void schemaRejectsRawTokensUnknownBucketsAndUnboundedCounters() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.staff_identity_abuse_windows (
                    bucket_type, subject_key, attempt_count, window_started_at, expires_at)
                values ('UNKNOWN', 'value', 1, ?, ?)
                """, NOW, NOW.plusSeconds(1))).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.staff_identity_abuse_windows (
                    bucket_type, subject_key, attempt_count, window_started_at, expires_at)
                values ('RECOVERY_TOKEN_FAILURE', 'raw-token', 1, ?, ?)
                """, NOW, NOW.plusSeconds(1))).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.staff_identity_abuse_windows (
                    bucket_type, subject_key, attempt_count, window_started_at, expires_at)
                values ('RECOVERY_GLOBAL_REQUEST', 'GLOBAL', 100002, ?, ?)
                """, NOW, NOW.plusSeconds(1))).isInstanceOf(Exception.class);
    }
}
