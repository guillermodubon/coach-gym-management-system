package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.user.StaffPasswordChanged;
import io.github.guillermodubon.coachgym.user.StaffProfilePhotoChanged;
import io.github.guillermodubon.coachgym.user.StaffProfileUpdated;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffSelfProfileAuditEntryJpaEntityTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000007201");
    private static final Instant NOW = Instant.parse("2026-09-19T12:05:00Z");

    @Test
    void mapsProfileUpdateToExactActorResourceAndFieldNameMetadata() {
        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(
                new StaffProfileUpdated(
                        USER_ID, Set.of("firstName", "lastName"), NOW,
                        "admin"));

        assertCommonFields(
                entry,
                "STAFF_PROFILE_UPDATED",
                "Staff self-profile updated.");
        assertThat(entry.metadata())
                .containsExactlyEntriesOf(Map.of(
                        "changedFields", List.of("firstName", "lastName")));
        assertThat(entry.metadata().toString()).doesNotContain(
                "oldValue", "newValue", "password", "phone", "email",
                "storageKey", "path", "photoBytes");
    }

    @Test
    void mapsPhotoUpdateAndRemovalAsPresenceOnlyActions() {
        AuditEntryJpaEntity updated = AuditEntryJpaEntity.from(
                new StaffProfilePhotoChanged(USER_ID, true, NOW, "receptionist"));
        AuditEntryJpaEntity removed = AuditEntryJpaEntity.from(
                new StaffProfilePhotoChanged(USER_ID, false, NOW, "receptionist"));

        assertThat(updated.actionCode()).isEqualTo("STAFF_PROFILE_PHOTO_UPDATED");
        assertThat(updated.metadata()).containsExactlyEntriesOf(
                Map.of("photoPresent", true));
        assertThat(removed.actionCode()).isEqualTo("STAFF_PROFILE_PHOTO_REMOVED");
        assertThat(removed.metadata()).containsExactlyEntriesOf(
                Map.of("photoPresent", false));
        assertThat(updated.metadata().toString()).doesNotContain(
                "bytes", "storage", "checksum", "filename", "path");
    }

    @Test
    void mapsPasswordChangeToReauthenticationPolicyOnly() {
        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(
                new StaffPasswordChanged(USER_ID, true, NOW, "admin"));

        assertCommonFields(
                entry,
                "STAFF_PASSWORD_CHANGED",
                "Staff password changed.");
        assertThat(entry.metadata()).containsExactlyEntriesOf(
                Map.of("reauthenticationRequired", true));
        assertThat(entry.metadata().toString()).doesNotContain(
                "password", "hash", "current", "new", "session", "token");
    }

    private static void assertCommonFields(
            AuditEntryJpaEntity entry,
            String actionCode,
            String summary) {
        assertThat(entry.actionCode()).isEqualTo(actionCode);
        assertThat(entry.resourceType()).isEqualTo("STAFF_PROFILE");
        assertThat(entry.resourceId()).isEqualTo(USER_ID);
        assertThat(entry.resourceCodeSnapshot()).isNull();
        assertThat(entry.actorUserId()).isEqualTo(USER_ID);
        assertThat(entry.actorIdentifierSnapshot()).isIn("admin", "receptionist");
        assertThat(entry.occurredAt()).isEqualTo(NOW);
        assertThat(entry.summary()).isEqualTo(summary);
    }
}
