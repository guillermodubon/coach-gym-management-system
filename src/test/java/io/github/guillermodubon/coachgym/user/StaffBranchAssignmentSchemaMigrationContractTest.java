package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class StaffBranchAssignmentSchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V32__create_staff_scopes_and_branch_assignments.sql");
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void createsOneScopeAndAppendOnlyAssignmentSchema() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("create table gym.staff_scopes")
                .contains("user_id uuid primary key")
                .contains("scope_type varchar(20) not null")
                .contains("create table gym.staff_branch_assignments")
                .contains("id uuid primary key")
                .contains("end_reason varchar(1000)")
                .contains("uq_staff_branch_assignments_active_user_branch")
                .contains("idx_staff_branch_assignments_branch_active")
                .contains("idx_staff_branch_assignments_user_history")
                .doesNotContain("on delete cascade", "tenant_id", "branch_id on gym.clients");
    }

    @Test
    void protectsForeignKeysStatusEndMetadataVersionsAndHistory() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("ck_staff_scopes_scope_type")
                .contains("ck_staff_scopes_version_non_negative")
                .contains("ck_staff_branch_assignments_status")
                .contains("ck_staff_branch_assignments_end_metadata")
                .contains("ck_staff_branch_assignments_end_order")
                .contains("ck_staff_branch_assignments_version_non_negative")
                .contains("fk_staff_scopes_user")
                .contains("fk_staff_scopes_granted_by_user")
                .contains("fk_staff_branch_assignments_user")
                .contains("fk_staff_branch_assignments_branch")
                .contains("fk_staff_branch_assignments_assigned_by_user")
                .contains("fk_staff_branch_assignments_ended_by_user")
                .contains("on delete restrict")
                .contains("trg_staff_scopes_reject_delete")
                .contains("trg_staff_scopes_validate_update")
                .contains("trg_staff_scopes_validate_role")
                .contains("trg_user_roles_validate_staff_scope")
                .contains("trg_staff_branch_assignments_protect_history")
                .contains("active to ended")
                .doesNotContain("delete from gym.staff_scopes", "delete from gym.staff_branch_assignments");
    }

    @Test
    void migratesSupportedExistingUsersToDeterministicScopeAndInitialAssignment() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("role.role_code not in ('admin', 'receptionist')")
                .contains("count(role.role_code) <> 1")
                .contains("organization.is_canonical")
                .contains("branch.is_initial_branch")
                .contains("exactly one active canonical initial branch")
                .contains("when 'admin' then 'organization'")
                .contains("when 'receptionist' then 'branch'")
                .contains("where role.role_code = 'receptionist'")
                .contains("md5(user_account.id::text || ':initial-branch')::uuid")
                .contains("granted_by_user_id")
                .contains("assigned_by_user_id");
    }

    @Test
    void migrationIsTheUniqueNextVersion() throws Exception {
        try (Stream<Path> files = Files.list(MIGRATION.getParent())) {
            var versions = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> VERSIONED_MIGRATION.matcher(path.getFileName().toString()))
                    .filter(java.util.regex.Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            assertThat(versions).doesNotHaveDuplicates();
            assertThat(versions).contains(30, 31, 32, 33, 34, 35, 36);
            assertThat(versions).isSortedAccordingTo(Comparator.naturalOrder());
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
