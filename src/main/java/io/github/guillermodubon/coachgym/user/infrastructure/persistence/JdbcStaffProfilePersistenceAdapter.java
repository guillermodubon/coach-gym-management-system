package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffProfilePhotoDetails;
import io.github.guillermodubon.coachgym.user.StaffSelfProfileDetails;
import io.github.guillermodubon.coachgym.user.application.StaffProfileDataAccessException;
import io.github.guillermodubon.coachgym.user.application.StaffProfileNotFoundException;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoNotFoundException;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoRecord;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoStore;
import io.github.guillermodubon.coachgym.user.application.StaffProfileQuery;
import io.github.guillermodubon.coachgym.user.application.StaffProfileStore;
import io.github.guillermodubon.coachgym.user.application.StaffPasswordStore;
import io.github.guillermodubon.coachgym.user.application.StaffProfileValidationException;
import io.github.guillermodubon.coachgym.user.application.StaffProfileVersionConflictException;
import io.github.guillermodubon.coachgym.user.application.UpdateStaffSelfProfileCommand;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC persistence boundary for staff self-profile data and photo metadata. */
@Repository
class JdbcStaffProfilePersistenceAdapter
        implements StaffProfileQuery, StaffProfileStore, StaffProfilePhotoStore,
        StaffPasswordStore {

    private static final String PROFILE_SELECT = """
            select u.id, u.username, u.email, u.first_name, u.last_name,
                   u.status, u.version,
                   r.role_code,
                   p.id as photo_id, p.content_type as photo_content_type,
                   p.size_bytes as photo_size_bytes,
                   p.updated_at as photo_updated_at,
                   p.version as photo_version,
                   p.storage_key as photo_storage_key,
                   p.checksum_sha256 as photo_checksum_sha256
              from gym.users u
              left join gym.user_roles ur on ur.user_id = u.id
              left join gym.roles r on r.id = ur.role_id
              left join gym.staff_profile_photos p on p.user_id = u.id
             where u.id = :userId
               and u.status = 'ACTIVE'
             order by r.role_code
            """;

    private final JdbcClient jdbcClient;

    JdbcStaffProfilePersistenceAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffSelfProfileDetails> findByUserId(UUID userId) {
        requireUserId(userId);
        try {
            List<ProfileRows> rows = profileRows(userId);
            return rows.isEmpty()
                    ? Optional.empty()
                    : Optional.of(ProfileRows.toDetails(rows));
        } catch (DataAccessException exception) {
            throw new StaffProfileDataAccessException(
                    "Staff profile could not be read.", exception);
        }
    }

    @Override
    @Transactional
    public StaffSelfProfileDetails update(
            UUID userId,
            UpdateStaffSelfProfileCommand command) {
        requireUserId(userId);
        if (command == null) {
            throw new StaffProfileValidationException("Staff profile command is required.");
        }
        try {
            int updated = jdbcClient.sql("""
                            update gym.users
                               set first_name = :firstName,
                                   last_name = :lastName,
                                   version = version + 1
                             where id = :userId
                               and status = 'ACTIVE'
                               and version = :expectedVersion
                            """)
                    .param("firstName", command.firstName())
                    .param("lastName", command.lastName())
                    .param("userId", userId)
                    .param("expectedVersion", command.expectedVersion())
                    .update();
            if (updated == 0) {
                requireActiveUserOrConflict(userId);
            }
            return findByUserId(userId).orElseThrow(StaffProfileNotFoundException::new);
        } catch (StaffProfileNotFoundException | StaffProfileVersionConflictException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new StaffProfileDataAccessException(
                    "Staff profile could not be updated.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findPasswordHash(UUID userId) {
        requireUserId(userId);
        try {
            return jdbcClient.sql("""
                            select password_hash
                              from gym.users
                             where id = :userId
                               and status = 'ACTIVE'
                            """)
                    .param("userId", userId)
                    .query(String.class)
                    .optional();
        } catch (DataAccessException exception) {
            throw new StaffProfileDataAccessException(
                    "Staff password could not be read.", exception);
        }
    }

    @Override
    @Transactional
    public StaffSelfProfileDetails updatePassword(
            UUID userId,
            String encodedPassword,
            long expectedProfileVersion) {
        requireUserId(userId);
        if (encodedPassword == null || encodedPassword.isBlank()
                || expectedProfileVersion < 0) {
            throw new StaffProfileValidationException(
                    "Staff password update data is invalid.");
        }
        try {
            int updated = jdbcClient.sql("""
                            update gym.users
                               set password_hash = :passwordHash,
                                   version = version + 1
                             where id = :userId
                               and status = 'ACTIVE'
                               and version = :expectedVersion
                            """)
                    .param("passwordHash", encodedPassword)
                    .param("userId", userId)
                    .param("expectedVersion", expectedProfileVersion)
                    .update();
            if (updated == 0) {
                requireActiveUserOrConflict(userId);
            }
            return findByUserId(userId).orElseThrow(StaffProfileNotFoundException::new);
        } catch (StaffProfileNotFoundException | StaffProfileVersionConflictException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new StaffProfileDataAccessException(
                    "Staff password could not be changed.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffProfilePhotoRecord> findPhotoByUserId(UUID userId) {
        requireUserId(userId);
        try {
            return jdbcClient.sql("""
                            select p.id, p.content_type, p.size_bytes,
                                   p.updated_at, p.version, p.storage_key,
                                   p.checksum_sha256
                              from gym.staff_profile_photos p
                              join gym.users u on u.id = p.user_id
                             where p.user_id = :userId
                               and u.status = 'ACTIVE'
                            """)
                    .param("userId", userId)
                    .query((rs, row) -> new StaffProfilePhotoRecord(
                            new StaffProfilePhotoDetails(
                                    rs.getObject("id", UUID.class),
                                    rs.getString("content_type"),
                                    rs.getLong("size_bytes"),
                                    rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                                    rs.getLong("version")),
                            rs.getString("storage_key"),
                            rs.getString("checksum_sha256")))
                    .optional();
        } catch (DataAccessException exception) {
            throw new StaffProfileDataAccessException(
                    "Staff profile photo metadata could not be read.", exception);
        }
    }

    @Override
    @Transactional
    public StaffProfilePhotoDetails save(
            UUID userId,
            String storageKey,
            String contentType,
            long sizeBytes,
            String checksumSha256,
            long expectedProfileVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {
        requireUserId(userId);
        requireActor(actor);
        if (storageKey == null || storageKey.isBlank()
                || storageKey.strip().length() > 500
                || contentType == null || contentType.isBlank()
                || checksumSha256 == null || !checksumSha256.matches("[0-9a-f]{64}")
                || sizeBytes < 1 || sizeBytes > StaffProfilePhotoDetails.MAX_SIZE_BYTES
                || occurredAt == null || expectedProfileVersion < 0) {
            throw new StaffProfileValidationException(
                    "Staff profile photo metadata is invalid.");
        }
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
        try {
            long nextVersion = lockActiveUserVersion(userId, expectedProfileVersion);
            UUID photoId = UUID.randomUUID();
            jdbcClient.sql("""
                            insert into gym.staff_profile_photos
                                (id, user_id, storage_key, content_type, size_bytes,
                                 checksum_sha256, created_at, updated_at,
                                 created_by_user_id, updated_by_user_id, version)
                            values (:id, :userId, :storageKey, :contentType, :sizeBytes,
                                    :checksum, :timestamp, :timestamp,
                                    :actorId, :actorId, :version)
                            on conflict (user_id) do update set
                                storage_key = excluded.storage_key,
                                content_type = excluded.content_type,
                                size_bytes = excluded.size_bytes,
                                checksum_sha256 = excluded.checksum_sha256,
                                updated_at = excluded.updated_at,
                                updated_by_user_id = excluded.updated_by_user_id,
                                version = excluded.version
                            """)
                    .param("id", photoId)
                    .param("userId", userId)
                    .param("storageKey", storageKey.strip())
                    .param("contentType", contentType.strip().toLowerCase(java.util.Locale.ROOT))
                    .param("sizeBytes", sizeBytes)
                    .param("checksum", checksumSha256)
                    .param("timestamp", timestamp)
                    .param("actorId", actor.id())
                    .param("version", nextVersion)
                    .update();
            incrementUserVersion(userId, nextVersion);
            return findPhotoByUserId(userId).orElseThrow(StaffProfilePhotoNotFoundException::new)
                    .details();
        } catch (StaffProfileNotFoundException | StaffProfileVersionConflictException
                 | StaffProfilePhotoNotFoundException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new StaffProfileDataAccessException(
                    "Staff profile photo metadata could not be saved.", exception);
        }
    }

    @Override
    @Transactional
    public String delete(UUID userId, long expectedProfileVersion) {
        requireUserId(userId);
        if (expectedProfileVersion < 0) {
            throw new StaffProfileValidationException(
                    "Expected profile version must not be negative.");
        }
        try {
            long nextVersion = lockActiveUserVersion(userId, expectedProfileVersion);
            String storageKey = jdbcClient.sql("""
                            select storage_key
                              from gym.staff_profile_photos
                             where user_id = :userId
                            """)
                    .param("userId", userId)
                    .query(String.class)
                    .optional()
                    .orElseThrow(StaffProfilePhotoNotFoundException::new);
            int deleted = jdbcClient.sql("""
                            delete from gym.staff_profile_photos
                             where user_id = :userId
                            """)
                    .param("userId", userId)
                    .update();
            if (deleted != 1) {
                throw new StaffProfilePhotoNotFoundException();
            }
            incrementUserVersion(userId, nextVersion);
            return storageKey;
        } catch (StaffProfileNotFoundException | StaffProfileVersionConflictException
                 | StaffProfilePhotoNotFoundException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new StaffProfileDataAccessException(
                    "Staff profile photo metadata could not be deleted.", exception);
        }
    }

    private List<ProfileRows> profileRows(UUID userId) {
        return jdbcClient.sql(PROFILE_SELECT)
                .param("userId", userId)
                .query((rs, row) -> new ProfileRows(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        StaffAccountStatus.valueOf(rs.getString("status")),
                        rs.getLong("version"),
                        rs.getString("role_code") == null
                                ? null : RoleCode.valueOf(rs.getString("role_code")),
                        rs.getObject("photo_id", UUID.class),
                        rs.getString("photo_content_type"),
                        rs.getObject("photo_size_bytes", Long.class),
                        rs.getObject("photo_updated_at", OffsetDateTime.class),
                        rs.getObject("photo_version", Long.class)))
                .list();
    }

    private long lockActiveUserVersion(UUID userId, long expectedVersion) {
        Long currentVersion = jdbcClient.sql("""
                        select version
                          from gym.users
                         where id = :userId
                           and status = 'ACTIVE'
                         for update
                        """)
                .param("userId", userId)
                .query(Long.class)
                .optional()
                .orElseThrow(StaffProfileNotFoundException::new);
        if (currentVersion != expectedVersion) {
            throw new StaffProfileVersionConflictException(userId);
        }
        return currentVersion + 1;
    }

    private void incrementUserVersion(UUID userId, long nextVersion) {
        int updated = jdbcClient.sql("""
                        update gym.users
                           set version = :nextVersion
                         where id = :userId
                           and version = :expectedVersion
                        """)
                .param("nextVersion", nextVersion)
                .param("userId", userId)
                .param("expectedVersion", nextVersion - 1)
                .update();
        if (updated != 1) {
            throw new StaffProfileVersionConflictException(userId);
        }
    }

    private void requireActiveUserOrConflict(UUID userId) {
        boolean exists = jdbcClient.sql("""
                        select exists(
                            select 1 from gym.users
                             where id = :userId and status = 'ACTIVE')
                        """)
                .param("userId", userId)
                .query(Boolean.class)
                .single();
        if (!exists) {
            throw new StaffProfileNotFoundException();
        }
        throw new StaffProfileVersionConflictException(userId);
    }

    private static void requireUserId(UUID userId) {
        if (userId == null) {
            throw new StaffProfileValidationException("Staff user id is required.");
        }
    }

    private static void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null) {
            throw new StaffProfileValidationException("Authenticated actor is required.");
        }
    }

    private record ProfileRows(
            UUID userId,
            String username,
            String email,
            String firstName,
            String lastName,
            StaffAccountStatus status,
            long version,
            RoleCode role,
            UUID photoId,
            String photoContentType,
            Long photoSizeBytes,
            OffsetDateTime photoUpdatedAt,
            Long photoVersion) {

        private static StaffSelfProfileDetails toDetails(List<ProfileRows> rows) {
            ProfileRows first = rows.get(0);
            Set<RoleCode> roles = EnumSet.noneOf(RoleCode.class);
            for (ProfileRows row : rows) {
                if (row.role != null) {
                    roles.add(row.role);
                }
            }
            StaffProfilePhotoDetails photo = first.photoId == null
                    ? null
                    : new StaffProfilePhotoDetails(
                            first.photoId,
                            first.photoContentType,
                            first.photoSizeBytes,
                            first.photoUpdatedAt.toInstant(),
                            first.photoVersion);
            return new StaffSelfProfileDetails(
                    first.userId,
                    first.username,
                    first.email,
                    first.firstName,
                    first.lastName,
                    roles,
                    first.status,
                    photo,
                    first.version);
        }
    }
}
