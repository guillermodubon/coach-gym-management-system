package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoRecord;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoStore;
import io.github.guillermodubon.coachgym.user.application.StaffProfileQuery;
import io.github.guillermodubon.coachgym.user.application.StaffProfileStore;
import io.github.guillermodubon.coachgym.user.application.StaffProfileVersionConflictException;
import io.github.guillermodubon.coachgym.user.application.UpdateStaffSelfProfileCommand;
import java.time.Instant;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@ActiveProfiles("test")
@Transactional
class StaffProfilePersistenceIntegrationTest {

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
    private StaffProfileQuery profileQuery;

    @Autowired
    private StaffProfileStore profileStore;

    @Autowired
    private StaffProfilePhotoStore photoStore;

    private UUID userId;

    @BeforeEach
    void setUpUser() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.users
                    (id, username, email, password_hash, first_name, last_name,
                     status, version)
                values (?, ?, ?, ?, ?, ?, 'ACTIVE', 0)
                """, userId, "self-profile-" + userId,
                userId + "@example.test", "{bcrypt}stored-hash", "Initial", "Name");
        jdbcTemplate.update("""
                insert into gym.user_roles (user_id, role_id)
                select ?, id from gym.roles where role_code = 'ADMIN'
                """, userId);
    }

    @Test
    void existingUserIsReadableWithNoPhotoAndSafeRoleProjection() {
        StaffSelfProfileDetails details = profileQuery.findByUserId(userId).orElseThrow();

        assertThat(details.userId()).isEqualTo(userId);
        assertThat(details.username()).startsWith("self-profile-");
        assertThat(details.email()).endsWith("@example.test");
        assertThat(details.displayName()).isEqualTo("Initial Name");
        assertThat(details.roles()).containsExactly(RoleCode.ADMIN);
        assertThat(details.status()).isEqualTo(StaffAccountStatus.ACTIVE);
        assertThat(details.photoPresent()).isFalse();
        assertThat(details.version()).isZero();
    }

    @Test
    void updateUsesExpectedVersionAndLeavesProtectedIdentityFieldsUnchanged() {
        String passwordHash = jdbcTemplate.queryForObject(
                "select password_hash from gym.users where id = ?", String.class, userId);

        StaffSelfProfileDetails updated = profileStore.update(
                userId,
                new UpdateStaffSelfProfileCommand(" Updated ", " Profile ", 0));

        assertThat(updated.firstName()).isEqualTo("Updated");
        assertThat(updated.lastName()).isEqualTo("Profile");
        assertThat(updated.version()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select password_hash from gym.users where id = ?", String.class, userId))
                .isEqualTo(passwordHash);
        assertThat(jdbcTemplate.queryForObject(
                "select status from gym.users where id = ?", String.class, userId))
                .isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                  from gym.user_roles ur
                  join gym.roles r on r.id = ur.role_id
                 where ur.user_id = ? and r.role_code = 'ADMIN'
                """, Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void staleProfileVersionIsRejectedWithoutASecondWrite() {
        profileStore.update(userId, new UpdateStaffSelfProfileCommand("First", "Write", 0));

        assertThatThrownBy(() -> profileStore.update(
                userId,
                new UpdateStaffSelfProfileCommand("Second", "Write", 0)))
                .isInstanceOf(StaffProfileVersionConflictException.class);
        assertThat(jdbcTemplate.queryForObject(
                "select first_name from gym.users where id = ?", String.class, userId))
                .isEqualTo("First");
        assertThat(jdbcTemplate.queryForObject(
                "select version from gym.users where id = ?", Long.class, userId))
                .isEqualTo(1L);
    }

    @Test
    void photoMetadataIsOneToOneAndHiddenFromThePublicProfileProjection() {
        Instant occurredAt = Instant.parse("2026-09-19T12:00:00Z");
        String storageKey = "staff-profiles/" + userId + "/photo-1.png";
        StaffProfilePhotoDetails saved = photoStore.save(
                userId,
                storageKey,
                "image/png",
                32,
                "a".repeat(64),
                0,
                new AuthenticatedActor(userId, "self-profile"),
                occurredAt);

        assertThat(saved.version()).isEqualTo(1);
        assertThat(photoStore.findPhotoByUserId(userId))
                .map(StaffProfilePhotoRecord::storageKey)
                .contains(storageKey);
        StaffSelfProfileDetails profile = profileQuery.findByUserId(userId).orElseThrow();
        assertThat(profile.photoPresent()).isTrue();
        assertThat(profile.photo().contentType()).isEqualTo("image/png");
        assertThat(profile.toString()).doesNotContain(storageKey, "a".repeat(64));
    }

    @Test
    void replacingAndRemovingPhotoMetadataAdvanceTheProfileVersionAtomically() {
        Instant occurredAt = Instant.parse("2026-09-19T12:00:00Z");
        AuthenticatedActor actor = new AuthenticatedActor(userId, "self-profile");
        photoStore.save(userId, "staff-profiles/" + userId + "/one.png",
                "image/png", 32, "a".repeat(64), 0, actor, occurredAt);
        photoStore.save(userId, "staff-profiles/" + userId + "/two.png",
                "image/png", 64, "b".repeat(64), 1, actor, occurredAt.plusSeconds(1));

        assertThat(photoStore.delete(userId, 2))
                .isEqualTo("staff-profiles/" + userId + "/two.png");
        assertThat(profileQuery.findByUserId(userId).orElseThrow().photoPresent()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select version from gym.users where id = ?", Long.class, userId))
                .isEqualTo(3L);
    }

    @Test
    void restrictiveForeignKeyPreventsDeletingAnOwnerWithPhotoMetadata() {
        Instant occurredAt = Instant.parse("2026-09-19T12:00:00Z");
        photoStore.save(userId, "staff-profiles/" + userId + "/delete.png",
                "image/png", 32, "c".repeat(64), 0,
                new AuthenticatedActor(userId, "self-profile"), occurredAt);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.users where id = ?", userId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void schemaHasNoBinaryPhotoColumnAndHasNamedOwnershipConstraints() {
        List<String> binaryColumns = jdbcTemplate.queryForList("""
                select column_name
                  from information_schema.columns
                 where table_schema = 'gym'
                   and table_name = 'staff_profile_photos'
                   and data_type = 'bytea'
                """, String.class);
        assertThat(binaryColumns).isEmpty();

        List<String> constraints = jdbcTemplate.queryForList("""
                select constraint_name
                  from information_schema.table_constraints
                 where table_schema = 'gym'
                   and table_name = 'staff_profile_photos'
                """, String.class);
        assertThat(constraints).contains(
                "uq_staff_profile_photos_user",
                "fk_staff_profile_photos_user",
                "ck_staff_profile_photos_content_type",
                "ck_staff_profile_photos_checksum_sha256");
    }
}
