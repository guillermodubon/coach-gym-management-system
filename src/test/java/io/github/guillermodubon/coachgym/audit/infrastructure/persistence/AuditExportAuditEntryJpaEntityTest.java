package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.audit.AuditExportCompleted;
import io.github.guillermodubon.coachgym.audit.AuditSortDirection;
import io.github.guillermodubon.coachgym.audit.AuditSortField;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuditExportAuditEntryJpaEntityTest {

    @Test
    void mapsOnlyBoundedExportMetadata() {
        AuditExportCompleted event = new AuditExportCompleted(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "admin.user",
                Instant.parse("2026-09-17T00:00:00Z"),
                Instant.parse("2026-09-17T01:00:00Z"),
                "actionCode,occurredFrom,occurredUntil",
                AuditSortField.OCCURRED_AT,
                AuditSortDirection.DESC,
                3,
                10_000,
                AuditExportCompleted.FORMAT_CSV,
                Instant.parse("2026-09-17T02:00:00Z"));

        AuditEntryJpaEntity entity = AuditEntryJpaEntity.from(event);

        assertThat(field(entity, "actionCode"))
                .isEqualTo("AUDIT_ENTRIES_EXPORTED");
        assertThat(field(entity, "resourceType")).isEqualTo("AUDIT_EXPORT");
        assertThat(field(entity, "resourceId")).isEqualTo(event.exportId());
        assertThat(metadata(entity)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "occurredFrom", "2026-09-17T00:00:00Z",
                "occurredUntil", "2026-09-17T01:00:00Z",
                "filtersPresent", "actionCode,occurredFrom,occurredUntil",
                "sortField", "OCCURRED_AT",
                "sortDirection", "DESC",
                "rowCount", 3L,
                "maximumRows", 10_000,
                "format", "CSV"));
        assertThat(metadata(entity).toString())
                .doesNotContain("metadata_json", "csv bytes", "password", "token");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(AuditEntryJpaEntity entity) {
        return (Map<String, Object>) ReflectionTestUtils.getField(entity, "metadata");
    }

    private static Object field(AuditEntryJpaEntity entity, String name) {
        return ReflectionTestUtils.getField(entity, name);
    }
}
