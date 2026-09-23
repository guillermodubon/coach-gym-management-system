package io.github.guillermodubon.coachgym.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OperationsBranchOwnershipSchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V36__add_operations_branch_ownership.sql");
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void addsImmutableOperationalOwnershipAndNotificationSnapshot() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("alter table gym.equipment")
                .contains("alter table gym.incidents")
                .contains("alter table gym.maintenances")
                .contains("alter table gym.notifications")
                .contains("alter column branch_id set default '7b0bf7d5-5184-43d2-8f9a-200000000002'::uuid")
                .contains("alter column branch_id set not null")
                .contains("fk_equipment_branch")
                .contains("fk_incidents_branch")
                .contains("fk_maintenances_branch")
                .contains("fk_notifications_branch")
                .contains("on delete restrict")
                .contains("equipment branch ownership is immutable")
                .contains("incident branch ownership is immutable")
                .contains("maintenance branch ownership is immutable")
                .contains("notification branch ownership is immutable");
    }

    @Test
    void preservesHistoricalUpdatedAtDuringBackfillAndRestoresTriggers() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("drop trigger trg_equipment_set_updated_at on gym.equipment")
                .contains("create trigger trg_equipment_set_updated_at")
                .contains("drop trigger trg_incidents_set_updated_at on gym.incidents")
                .contains("create trigger trg_incidents_set_updated_at")
                .contains("drop trigger trg_maintenances_set_updated_at on gym.maintenances")
                .contains("create trigger trg_maintenances_set_updated_at")
                .contains("drop trigger trg_notifications_set_updated_at on gym.notifications")
                .contains("create trigger trg_notifications_set_updated_at");
    }

    @Test
    void protectsRelatedBranchConsistencyAndIndexesOperationalLists() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("validate_incident_branch_consistency")
                .contains("validate_maintenance_branch_consistency")
                .contains("validate_notification_branch_consistency")
                .contains("idx_equipment_branch_status_created_at")
                .contains("idx_incidents_branch_status_reported_at")
                .contains("idx_maintenances_branch_status_scheduled_on")
                .contains("idx_notifications_recipient_branch_created_at")
                .contains("canonical initial branch");
    }

    @Test
    void migrationIsUniqueNextVersion() throws Exception {
        try (Stream<Path> files = Files.list(MIGRATION.getParent())) {
            var versions = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> VERSIONED_MIGRATION.matcher(
                            path.getFileName().toString()))
                    .filter(java.util.regex.Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            assertThat(versions).doesNotHaveDuplicates();
            assertThat(versions).contains(34, 35, 36);
            assertThat(versions.get(versions.size() - 1)).isEqualTo(36);
        }
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
