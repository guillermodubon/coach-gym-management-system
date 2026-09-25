package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class BranchCoverageSchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V37__add_membership_branch_coverage_and_access_policy.sql");

    @Test
    void addsBranchCoverageSnapshotsAndVersionedPaymentOverrides() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("add column branch_coverage_scope varchar(24)")
                .contains("create table gym.membership_plan_branches")
                .contains("pk_membership_plan_branches")
                .contains("idx_membership_plan_branches_branch_plan")
                .contains("create table gym.membership_period_coverage_snapshots")
                .contains("create table gym.membership_period_branch_coverage")
                .contains("coverage_scope_snapshot in (")
                .contains("source_plan_version >= 0")
                .contains("snapshot_created_xact_id xid8")
                .contains("create table gym.branch_access_policy_overrides")
                .contains("policy_mode in ('inherit', 'required', 'not_required')")
                .contains("version >= 0")
                .contains("'membership_not_valid_at_branch'");
    }

    @Test
    void migrationBackfillIsDeterministicAndDoesNotRewriteBusinessHistory()
            throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("exactly one active canonical initial branch")
                .contains("period.registered_at_branch_id")
                .contains("'single_branch'")
                .contains("current_timestamp")
                .contains("plan.version")
                .contains("drop trigger trg_membership_plans_set_updated_at")
                .contains("create trigger trg_membership_plans_set_updated_at")
                .doesNotContain("update gym.membership_periods")
                .doesNotContain("update gym.access_records")
                .doesNotContain("update gym.payments")
                .doesNotContain("flyway_schema_history")
                .doesNotContain("on delete cascade")
                .doesNotContain("if not exists")
                .doesNotContain("drop table");
    }

    @Test
    void coverageSnapshotsHaveAppendOnlyAndTransactionalCaptureGuards()
            throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("trg_membership_period_coverage_snapshots_append_only")
                .contains("trg_membership_period_branch_coverage_append_only")
                .contains("snapshot_xact_id is distinct from pg_current_xact_id()")
                .contains("deferrable initially deferred")
                .contains("membership-period branch coverage has invalid scope cardinality")
                .contains("on delete restrict");
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
