package io.github.guillermodubon.coachgym.client;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientProfileDatabaseConstraintIntegrationTest
        extends AbstractClientProfileApiIntegrationTest {

    @Test
    void databaseRejectsInvalidHistoryAndDuplicatePhotoMetadata() {
        var clientId = insertProfileClient(
                "Constraint",
                "Client",
                "constraint.client@example.com",
                "+50370008019");
        OffsetDateTime occurredAt = OffsetDateTime.of(
                2026, 9, 8, 18, 0, 0, 0, ZoneOffset.UTC);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.client_status_history
                    (id, client_id, previous_status, new_status,
                     reason, occurred_at, changed_by_user_id)
                values (?, ?, 'ACTIVE', 'ACTIVE', ?, ?, ?)
                """,
                UUID.randomUUID(),
                clientId,
                "Invalid same-state transition",
                occurredAt,
                adminId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("""
                insert into gym.client_photos
                    (id, client_id, storage_key, content_type,
                     size_bytes, checksum_sha256,
                     created_by_user_id, updated_by_user_id,
                     created_at, updated_at, version)
                values (?, ?, ?, 'image/png', 8, ?, ?, ?, ?, ?, 0)
                """,
                UUID.randomUUID(),
                clientId,
                "integration/first.png",
                "a".repeat(64),
                adminId,
                adminId,
                occurredAt,
                occurredAt);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.client_photos
                    (id, client_id, storage_key, content_type,
                     size_bytes, checksum_sha256,
                     created_by_user_id, updated_by_user_id,
                     created_at, updated_at, version)
                values (?, ?, ?, 'image/png', 8, ?, ?, ?, ?, ?, 0)
                """,
                UUID.randomUUID(),
                clientId,
                "integration/second.png",
                "b".repeat(64),
                adminId,
                adminId,
                occurredAt,
                occurredAt))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
