package io.github.guillermodubon.coachgym.client;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

abstract class AbstractClientProfileApiIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    protected UUID insertProfileClient(
            String firstName,
            String lastName,
            String email,
            String phone) {
        UUID id = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.ofInstant(
                Instant.parse("2026-09-08T18:00:00Z"),
                ZoneOffset.UTC);

        jdbcTemplate.update("""
                insert into gym.clients
                    (id, first_name, last_name, email, phone,
                     date_of_birth, status,
                     created_by_user_id, updated_by_user_id,
                     created_at, updated_at, version)
                values (?, ?, ?, ?, ?, date '1995-05-15', 'ACTIVE',
                        ?, ?, ?, ?, 0)
                """,
                id,
                firstName,
                lastName,
                email,
                phone,
                adminId,
                adminId,
                occurredAt,
                occurredAt);
        return id;
    }

    protected long clientVersion(UUID clientId) {
        Long version = jdbcTemplate.queryForObject(
                "select version from gym.clients where id = ?",
                Long.class,
                clientId);
        return version == null ? 0L : version;
    }

    protected String clientCode(UUID clientId) {
        return jdbcTemplate.queryForObject(
                "select client_code from gym.clients where id = ?",
                String.class,
                clientId);
    }

    protected String clientStatus(UUID clientId) {
        return jdbcTemplate.queryForObject(
                "select status from gym.clients where id = ?",
                String.class,
                clientId);
    }

    protected static String updatePayload(
            String email,
            long version) {

        return """
            {
              "firstName": "Updated",
              "lastName": "Profile",
              "email": "%s",
              "phone": "+50370009999",
              "dateOfBirth": "1995-05-15",
              "emergencyContact": {
                "fullName": "Emergency Contact",
                "relationship": "Family",
                "phone": "+50371112222"
              },
              "version": %d
            }
            """.formatted(email, version);
    }
    protected static String lifecyclePayload(
            String reason,
            long version) {
        return """
                {
                  "reason": "%s",
                  "version": %d
                }
                """.formatted(reason, version);
    }
}
