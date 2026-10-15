package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.user.StaffPasswordChanged;
import io.github.guillermodubon.coachgym.user.StaffProfilePhotoChanged;
import io.github.guillermodubon.coachgym.user.StaffProfileUpdated;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

class StaffSelfProfileAuditIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-19T12:10:00Z");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void clearStaffProfileAuditFixtures() {
        jdbcTemplate.update(
                "delete from gym.audit_entries where resource_type = 'STAFF_PROFILE'");
    }

    @Test
    void persistsOneSafeJsonbAuditEntryPerMeaningfulSelfProfileEvent() {
        eventPublisher.publishEvent(new StaffProfileUpdated(
                adminId, Set.of("firstName", "lastName"), NOW, ADMIN_USERNAME));
        eventPublisher.publishEvent(new StaffProfilePhotoChanged(
                adminId, true, NOW.plusSeconds(1), ADMIN_USERNAME));
        eventPublisher.publishEvent(new StaffPasswordChanged(
                adminId, true, NOW.plusSeconds(2), ADMIN_USERNAME));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select action_code, resource_type, resource_id,
                       actor_user_id, actor_identifier_snapshot,
                       metadata -> 'changedFields' as changed_fields,
                       metadata ->> 'photoPresent' as photo_present,
                       metadata ->> 'reauthenticationRequired' as reauthentication_required,
                       metadata::text as metadata
                from gym.audit_entries
                where resource_type = 'STAFF_PROFILE'
                order by occurred_at, id
                """);

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(row -> row.get("action_code"))
                .containsExactly(
                        "STAFF_PROFILE_UPDATED",
                        "STAFF_PROFILE_PHOTO_UPDATED",
                        "STAFF_PASSWORD_CHANGED");
        for (Map<String, Object> row : rows) {
            assertThat(row)
                    .containsEntry("resource_type", "STAFF_PROFILE")
                    .containsEntry("resource_id", adminId)
                    .containsEntry("actor_user_id", adminId)
                    .containsEntry("actor_identifier_snapshot", ADMIN_USERNAME);
            assertThat(row.get("metadata").toString()).doesNotContain(
                    "password", "hash", "storage", "path", "bytes",
                    "phone", "email", "oldValue", "newValue");
        }
        assertThat(rows.getFirst().get("changed_fields").toString())
                .contains("firstName", "lastName");
        assertThat(rows.get(1)).containsEntry("photo_present", "true");
        assertThat(rows.get(2)).containsEntry("reauthentication_required", "true");
    }
}
