package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class BranchCoverageSchemaMigrationIntegrationTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000001");
    private static final UUID INITIAL_BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");
    private static final UUID ACTIVE_BRANCH_ID = UUID.fromString(
            "81000000-0000-0000-0000-000000000001");
    private static final UUID INACTIVE_BRANCH_ID = UUID.fromString(
            "82000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString(
            "83000000-0000-0000-0000-000000000001");
    private static final UUID MULTI_BRANCH_PLAN_ID = UUID.fromString(
            "84000000-0000-0000-0000-000000000001");
    private static final UUID SINGLE_BRANCH_PLAN_ID = UUID.fromString(
            "84000000-0000-0000-0000-000000000002");
    private static final UUID INACTIVE_ONLY_PLAN_ID = UUID.fromString(
            "84000000-0000-0000-0000-000000000003");
    private static final UUID UNUSED_PLAN_ID = UUID.fromString(
            "84000000-0000-0000-0000-000000000004");
    private static final UUID CLIENT_ONE_ID = UUID.fromString(
            "85000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_TWO_ID = UUID.fromString(
            "85000000-0000-0000-0000-000000000002");
    private static final UUID CLIENT_THREE_ID = UUID.fromString(
            "85000000-0000-0000-0000-000000000003");
    private static final UUID CLIENT_FOUR_ID = UUID.fromString(
            "85000000-0000-0000-0000-000000000004");
    private static final UUID MEMBERSHIP_ONE_ID = UUID.fromString(
            "86000000-0000-0000-0000-000000000001");
    private static final UUID MEMBERSHIP_TWO_ID = UUID.fromString(
            "86000000-0000-0000-0000-000000000002");
    private static final UUID MEMBERSHIP_THREE_ID = UUID.fromString(
            "86000000-0000-0000-0000-000000000003");
    private static final UUID MEMBERSHIP_FOUR_ID = UUID.fromString(
            "86000000-0000-0000-0000-000000000004");
    private static final UUID PERIOD_ONE_ID = UUID.fromString(
            "87000000-0000-0000-0000-000000000001");
    private static final UUID PERIOD_TWO_ID = UUID.fromString(
            "87000000-0000-0000-0000-000000000002");
    private static final UUID PERIOD_THREE_ID = UUID.fromString(
            "87000000-0000-0000-0000-000000000003");
    private static final UUID PERIOD_FOUR_ID = UUID.fromString(
            "87000000-0000-0000-0000-000000000004");
    private static final UUID CLIENT_FIVE_ID = UUID.fromString(
            "85000000-0000-0000-0000-000000000005");
    private static final UUID MEMBERSHIP_FIVE_ID = UUID.fromString(
            "86000000-0000-0000-0000-000000000005");
    private static final UUID PERIOD_FIVE_ID = UUID.fromString(
            "87000000-0000-0000-0000-000000000005");
    private static final UUID ACCESS_RECORD_ID = UUID.fromString(
            "88000000-0000-0000-0000-000000000001");
    private static final UUID PAYMENT_ID = UUID.fromString(
            "88000000-0000-0000-0000-000000000002");
    private static final Instant PLAN_UPDATED_AT = Instant.parse(
            "2026-09-20T10:15:30Z");
    private static final Instant PERIOD_UPDATED_AT = Instant.parse(
            "2026-09-21T11:20:30Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void cleanFlywayChainCreatesCoverageSchemaAndValidates() {
        Flyway flyway = migrateFromEmptyDatabase();
        JdbcTemplate jdbc = jdbcTemplate();

        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("37");
        assertThat(jdbc.queryForObject("""
                select count(*)
                from flyway_schema_history
                where version = '37' and success
                """, Integer.class)).isEqualTo(1);

        assertThat(jdbc.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'gym'
                  and table_name in (
                      'membership_plan_branches',
                      'membership_period_coverage_snapshots',
                      'membership_period_branch_coverage',
                      'branch_access_policy_overrides')
                """, String.class)).containsExactlyInAnyOrder(
                "membership_plan_branches",
                "membership_period_coverage_snapshots",
                "membership_period_branch_coverage",
                "branch_access_policy_overrides");
        assertThat(jdbc.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = 'gym'
                  and indexname = 'idx_membership_plan_branches_branch_plan'
                """, String.class)).containsExactly(
                "idx_membership_plan_branches_branch_plan");
        assertThat(jdbc.queryForObject("""
                select count(*)
                from gym.branch_access_policy_overrides
                where branch_id = ? and policy_mode = 'INHERIT' and version = 0
                """, Integer.class, INITIAL_BRANCH_ID)).isEqualTo(1);
        assertRestrictiveForeignKeys(jdbc);
    }

    @Test
    void backfillsPlanCoverageAndPeriodSnapshotsWithoutRewritingHistory() {
        Flyway legacyFlyway = migrateToVersion36();
        JdbcTemplate jdbc = jdbcTemplate();
        seedLegacyRows(jdbc);

        Map<String, Object> planBefore = jdbc.queryForMap("""
                select version, updated_at from gym.membership_plans where id = ?
                """, MULTI_BRANCH_PLAN_ID);
        Map<String, Object> periodBefore = jdbc.queryForMap("""
                select version, updated_at from gym.membership_periods where id = ?
                """, PERIOD_ONE_ID);
        Map<String, Object> settingsBefore = jdbc.queryForMap("""
                select require_confirmed_payment_for_access, version, updated_at
                from gym.gym_settings where id = 1
                """);
        Map<String, Object> accessBefore = jdbc.queryForMap("""
                select decision, reason_code, occurred_at
                from gym.access_records where id = ?
                """, ACCESS_RECORD_ID);
        Map<String, Object> paymentBefore = jdbc.queryForMap("""
                select status, amount, registered_at_branch_id
                from gym.payments where id = ?
                """, PAYMENT_ID);
        int accessCountBefore = count(jdbc, "access_records");
        int paymentCountBefore = count(jdbc, "payments");

        Flyway currentFlyway = flyway();
        currentFlyway.migrate();
        currentFlyway.validate();

        assertThat(jdbc.queryForObject("""
                select branch_coverage_scope from gym.membership_plans where id = ?
                """, String.class, MULTI_BRANCH_PLAN_ID)).isEqualTo("SELECTED_BRANCHES");
        assertThat(jdbc.queryForObject("""
                select branch_coverage_scope from gym.membership_plans where id = ?
                """, String.class, SINGLE_BRANCH_PLAN_ID)).isEqualTo("SINGLE_BRANCH");
        assertThat(jdbc.queryForObject("""
                select branch_coverage_scope from gym.membership_plans where id = ?
                """, String.class, INACTIVE_ONLY_PLAN_ID)).isEqualTo("SINGLE_BRANCH");
        assertThat(jdbc.queryForObject("""
                select branch_coverage_scope from gym.membership_plans where id = ?
                """, String.class, UNUSED_PLAN_ID)).isEqualTo("SINGLE_BRANCH");

        assertThat(planBranches(jdbc, MULTI_BRANCH_PLAN_ID))
                .containsExactlyInAnyOrder(INITIAL_BRANCH_ID, ACTIVE_BRANCH_ID);
        assertThat(planBranches(jdbc, SINGLE_BRANCH_PLAN_ID))
                .containsExactly(ACTIVE_BRANCH_ID);
        assertThat(planBranches(jdbc, INACTIVE_ONLY_PLAN_ID))
                .containsExactly(INITIAL_BRANCH_ID);
        assertThat(planBranches(jdbc, UNUSED_PLAN_ID))
                .containsExactly(INITIAL_BRANCH_ID);

        assertSnapshot(jdbc, PERIOD_ONE_ID, INITIAL_BRANCH_ID, 7L);
        assertSnapshot(jdbc, PERIOD_TWO_ID, ACTIVE_BRANCH_ID, 7L);
        assertSnapshot(jdbc, PERIOD_THREE_ID, INACTIVE_BRANCH_ID, 3L);
        assertSnapshot(jdbc, PERIOD_FOUR_ID, ACTIVE_BRANCH_ID, 4L);
        assertThat(jdbc.queryForObject("""
                select count(*)
                from gym.branch_access_policy_overrides
                where policy_mode = 'INHERIT' and version = 0
                """, Integer.class)).isEqualTo(3);

        assertThat(jdbc.queryForMap("""
                select version, updated_at from gym.membership_plans where id = ?
                """, MULTI_BRANCH_PLAN_ID)).isEqualTo(planBefore);
        assertThat(jdbc.queryForMap("""
                select version, updated_at from gym.membership_periods where id = ?
                """, PERIOD_ONE_ID)).isEqualTo(periodBefore);
        assertThat(jdbc.queryForMap("""
                select require_confirmed_payment_for_access, version, updated_at
                from gym.gym_settings where id = 1
                """)).isEqualTo(settingsBefore);
        assertThat(jdbc.queryForMap("""
                select decision, reason_code, occurred_at
                from gym.access_records where id = ?
                """, ACCESS_RECORD_ID)).isEqualTo(accessBefore);
        assertThat(jdbc.queryForMap("""
                select status, amount, registered_at_branch_id
                from gym.payments where id = ?
                """, PAYMENT_ID)).isEqualTo(paymentBefore);
        assertThat(count(jdbc, "access_records")).isEqualTo(accessCountBefore);
        assertThat(count(jdbc, "payments")).isEqualTo(paymentCountBefore);

        assertCoverageDatabaseInvariants(jdbc);

        insertAccessRecord(
                jdbc,
                UUID.fromString("88000000-0000-0000-0000-000000000003"),
                CLIENT_TWO_ID,
                "MEMBERSHIP_NOT_VALID_AT_BRANCH");
        assertThat(jdbc.queryForObject("""
                select reason_code from gym.access_records
                where id = '88000000-0000-0000-0000-000000000003'
                """, String.class)).isEqualTo("MEMBERSHIP_NOT_VALID_AT_BRANCH");
    }

    @Test
    void failsBeforeSchemaChangesWhenInitialBranchIsAmbiguous() {
        Flyway legacyFlyway = migrateToVersion36();
        JdbcTemplate jdbc = jdbcTemplate();
        jdbc.update("""
                update gym.gym_branches
                set is_initial_branch = false
                where id = ?
                """, INITIAL_BRANCH_ID);

        assertThatThrownBy(() -> flyway().migrate())
                .hasMessageContaining(
                        "exactly one active canonical initial branch is required for branch coverage backfill");
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'gym'
                  and table_name = 'membership_plans'
                  and column_name = 'branch_coverage_scope'
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from flyway_schema_history
                where version = '37' and success
                """, Integer.class)).isZero();
        legacyFlyway.validate();
    }

    private static void assertCoverageDatabaseInvariants(JdbcTemplate jdbc) {
        assertThatThrownBy(() -> jdbc.update("""
                insert into gym.membership_plan_branches (membership_plan_id, branch_id)
                values (?, ?)
                """, MULTI_BRANCH_PLAN_ID, INITIAL_BRANCH_ID))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                insert into gym.membership_plan_branches (membership_plan_id, branch_id)
                values (?, ?)
                """, UNUSED_PLAN_ID, INACTIVE_BRANCH_ID))
                .isInstanceOf(DataAccessException.class);

        UUID otherOrganizationId = UUID.fromString(
                "89000000-0000-0000-0000-000000000001");
        UUID otherBranchId = UUID.fromString(
                "89000000-0000-0000-0000-000000000002");
        jdbc.update("""
                insert into gym.organizations
                    (id, code, legal_name, brand_name, default_timezone,
                     default_currency, status, is_canonical, version)
                values (?, 'MIGRATION_OTHER', 'Other Gym', 'Other Gym',
                        'America/El_Salvador', 'USD', 'ACTIVE', false, 0)
                """, otherOrganizationId);
        jdbc.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, version)
                values (?, ?, 'OTHER', 'Other Branch',
                        'America/El_Salvador', 'ACTIVE', 0)
                """, otherBranchId, otherOrganizationId);
        assertThatThrownBy(() -> jdbc.update("""
                insert into gym.membership_plan_branches (membership_plan_id, branch_id)
                values (?, ?)
                """, UNUSED_PLAN_ID, otherBranchId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                update gym.branch_access_policy_overrides
                set policy_mode = 'UNSUPPORTED'
                where branch_id = ?
                """, INITIAL_BRANCH_ID))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                update gym.branch_access_policy_overrides
                set version = -1
                where branch_id = ?
                """, INITIAL_BRANCH_ID))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                update gym.membership_period_coverage_snapshots
                set source_plan_version = source_plan_version + 1
                where membership_period_id = ?
                """, PERIOD_ONE_ID))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                delete from gym.membership_period_coverage_snapshots
                where membership_period_id = ?
                """, PERIOD_ONE_ID))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                update gym.membership_period_branch_coverage
                set branch_id = ?
                where membership_period_id = ? and branch_id = ?
                """, ACTIVE_BRANCH_ID, PERIOD_ONE_ID, INITIAL_BRANCH_ID))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                delete from gym.membership_period_branch_coverage
                where membership_period_id = ? and branch_id = ?
                """, PERIOD_ONE_ID, INITIAL_BRANCH_ID))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into gym.membership_period_branch_coverage
                    (membership_period_id, branch_id)
                values (?, ?)
                """, PERIOD_ONE_ID, ACTIVE_BRANCH_ID))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into gym.membership_plans
                    (id, name, duration_value, duration_unit, list_price, currency,
                     branch_coverage_scope)
                values (?, 'Invalid Scope', 1, 'MONTH', 25.00, 'USD', 'UNSUPPORTED')
                """, UUID.fromString("84000000-0000-0000-0000-000000000005")))
                .isInstanceOf(DataAccessException.class);

        insertClient(jdbc, CLIENT_FIVE_ID, "Five", "555-0105");
        insertMembership(jdbc, MEMBERSHIP_FIVE_ID, CLIENT_FIVE_ID);
        insertPeriod(jdbc, PERIOD_FIVE_ID, MEMBERSHIP_FIVE_ID,
                UNUSED_PLAN_ID, INITIAL_BRANCH_ID);

        assertThatThrownBy(() -> jdbc.update("""
                insert into gym.membership_period_coverage_snapshots
                    (membership_period_id, coverage_scope_snapshot,
                     captured_at, source_plan_version)
                values (?, 'SINGLE_BRANCH', current_timestamp, -1)
                """, PERIOD_FIVE_ID))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into gym.membership_period_coverage_snapshots
                    (membership_period_id, coverage_scope_snapshot,
                     captured_at, source_plan_version)
                values (?, 'UNSUPPORTED', current_timestamp, 0)
                """, PERIOD_FIVE_ID))
                .isInstanceOf(DataAccessException.class);

        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(jdbc.getDataSource()));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("""
                    insert into gym.membership_period_coverage_snapshots
                        (membership_period_id, coverage_scope_snapshot,
                         captured_at, source_plan_version)
                    values (?, 'SELECTED_BRANCHES', current_timestamp, 0)
                    """, PERIOD_FIVE_ID);
            jdbc.update("""
                    insert into gym.membership_period_branch_coverage
                        (membership_period_id, branch_id)
                    values (?, ?)
                    """, PERIOD_FIVE_ID, INITIAL_BRANCH_ID);
        })).hasStackTraceContaining(
                "membership-period branch coverage has invalid scope cardinality");
        assertThat(jdbc.queryForObject("""
                select count(*) from gym.membership_period_coverage_snapshots
                where membership_period_id = ?
                """, Integer.class, PERIOD_FIVE_ID)).isZero();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("""
                    insert into gym.membership_period_coverage_snapshots
                        (membership_period_id, coverage_scope_snapshot,
                         captured_at, source_plan_version)
                    values (?, 'SINGLE_BRANCH', current_timestamp, 0)
                    """, PERIOD_FIVE_ID);
            jdbc.update("""
                    insert into gym.membership_period_branch_coverage
                        (membership_period_id, branch_id)
                    values (?, ?)
                    """, PERIOD_FIVE_ID, INITIAL_BRANCH_ID);
            jdbc.update("""
                    insert into gym.membership_period_branch_coverage
                        (membership_period_id, branch_id)
                    values (?, ?)
                    """, PERIOD_FIVE_ID, INITIAL_BRANCH_ID);
        })).hasStackTraceContaining("pk_membership_period_branch_coverage");
        assertThat(jdbc.queryForObject("""
                select count(*) from gym.membership_period_coverage_snapshots
                where membership_period_id = ?
                """, Integer.class, PERIOD_FIVE_ID)).isZero();

        assertThatThrownBy(() -> jdbc.update("""
                insert into gym.membership_plan_branches (membership_plan_id, branch_id)
                values (?, ?)
                """, UUID.randomUUID(), INITIAL_BRANCH_ID))
                .isInstanceOf(DataAccessException.class);
    }

    private static void assertSnapshot(
            JdbcTemplate jdbc, UUID periodId, UUID expectedBranchId, long sourcePlanVersion) {
        Map<String, Object> snapshot = jdbc.queryForMap("""
                select coverage_scope_snapshot, captured_at, source_plan_version
                from gym.membership_period_coverage_snapshots
                where membership_period_id = ?
                """, periodId);
        assertThat(snapshot.get("coverage_scope_snapshot")).isEqualTo("SINGLE_BRANCH");
        assertThat(snapshot.get("captured_at")).isNotNull();
        assertThat(snapshot.get("source_plan_version")).isEqualTo(sourcePlanVersion);
        assertThat(jdbc.queryForList("""
                select branch_id
                from gym.membership_period_branch_coverage
                where membership_period_id = ?
                """, UUID.class, periodId)).containsExactly(expectedBranchId);
    }

    private static List<UUID> planBranches(
            JdbcTemplate jdbc, UUID... planIds) {
        if (planIds.length == 1) {
            return jdbc.queryForList("""
                    select branch_id from gym.membership_plan_branches
                    where membership_plan_id = ? order by branch_id
                    """, UUID.class, planIds[0]);
        }
        return jdbc.queryForList("""
                select branch_id from gym.membership_plan_branches
                where membership_plan_id in (?, ?) order by branch_id
                """, UUID.class, (Object[]) planIds);
    }

    private static void assertRestrictiveForeignKeys(JdbcTemplate jdbc) {
        List<String> restrictiveKeys = jdbc.queryForList("""
                select constraint_name
                from information_schema.referential_constraints
                where constraint_schema = 'gym'
                  and constraint_name in (
                      'fk_membership_plan_branches_plan',
                      'fk_membership_plan_branches_branch',
                      'fk_membership_period_coverage_snapshots_period',
                      'fk_membership_period_branch_coverage_snapshot',
                      'fk_membership_period_branch_coverage_branch',
                      'fk_branch_access_policy_overrides_branch')
                  and delete_rule = 'RESTRICT'
                """, String.class);
        assertThat(restrictiveKeys).containsExactlyInAnyOrder(
                "fk_membership_plan_branches_plan",
                "fk_membership_plan_branches_branch",
                "fk_membership_period_coverage_snapshots_period",
                "fk_membership_period_branch_coverage_snapshot",
                "fk_membership_period_branch_coverage_branch",
                "fk_branch_access_policy_overrides_branch");
    }

    private static Flyway migrateFromEmptyDatabase() {
        Flyway flyway = flyway();
        flyway.clean();
        flyway.migrate();
        return flyway;
    }

    private static Flyway migrateToVersion36() {
        Flyway flyway = flyway();
        flyway.clean();
        Flyway legacyFlyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(migrationLocation())
                .schemas("public", "gym")
                .cleanDisabled(false)
                .target("36")
                .load();
        legacyFlyway.migrate();
        return legacyFlyway;
    }

    private static Flyway flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(migrationLocation())
                .schemas("public", "gym")
                .cleanDisabled(false)
                .load();
    }

    private static String migrationLocation() {
        return "filesystem:" + Path.of("src/main/resources/db/migration")
                .toAbsolutePath()
                .toString()
                .replace('\\', '/');
    }

    private static JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private static void seedLegacyRows(JdbcTemplate jdbc) {
        jdbc.update("""
                insert into gym.users
                    (id, username, email, password_hash, first_name, last_name, status)
                values (?, 'coverage-migration-user', 'coverage-migration@example.test',
                        'test-fixture-hash', 'Coverage', 'Migration', 'ACTIVE')
                """, USER_ID);
        jdbc.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, version)
                values (?, ?, 'NORTH', 'North Branch',
                        'America/El_Salvador', 'ACTIVE', 0),
                       (?, ?, 'CLOSED', 'Closed Branch',
                        'America/El_Salvador', 'INACTIVE', 0)
                """, ACTIVE_BRANCH_ID, ORGANIZATION_ID, INACTIVE_BRANCH_ID, ORGANIZATION_ID);

        insertPlan(jdbc, MULTI_BRANCH_PLAN_ID, "Multi branch", 7L);
        insertPlan(jdbc, SINGLE_BRANCH_PLAN_ID, "Single branch", 4L);
        insertPlan(jdbc, INACTIVE_ONLY_PLAN_ID, "Inactive history", 3L);
        insertPlan(jdbc, UNUSED_PLAN_ID, "Unused", 0L);

        insertClient(jdbc, CLIENT_ONE_ID, "One", "555-0101");
        insertClient(jdbc, CLIENT_TWO_ID, "Two", "555-0102");
        insertClient(jdbc, CLIENT_THREE_ID, "Three", "555-0103");
        insertClient(jdbc, CLIENT_FOUR_ID, "Four", "555-0104");
        insertMembership(jdbc, MEMBERSHIP_ONE_ID, CLIENT_ONE_ID);
        insertMembership(jdbc, MEMBERSHIP_TWO_ID, CLIENT_TWO_ID);
        insertMembership(jdbc, MEMBERSHIP_THREE_ID, CLIENT_THREE_ID);
        insertMembership(jdbc, MEMBERSHIP_FOUR_ID, CLIENT_FOUR_ID);
        insertPeriod(jdbc, PERIOD_ONE_ID, MEMBERSHIP_ONE_ID,
                MULTI_BRANCH_PLAN_ID, INITIAL_BRANCH_ID);
        insertPeriod(jdbc, PERIOD_TWO_ID, MEMBERSHIP_TWO_ID,
                MULTI_BRANCH_PLAN_ID, ACTIVE_BRANCH_ID);
        insertPeriod(jdbc, PERIOD_THREE_ID, MEMBERSHIP_THREE_ID,
                INACTIVE_ONLY_PLAN_ID, INACTIVE_BRANCH_ID);
        insertPeriod(jdbc, PERIOD_FOUR_ID, MEMBERSHIP_FOUR_ID,
                SINGLE_BRANCH_PLAN_ID, ACTIVE_BRANCH_ID);

        jdbc.update("""
                update gym.gym_settings
                set require_confirmed_payment_for_access = true,
                    updated_by_user_id = ?,
                    version = 9
                where id = 1
                """, USER_ID);
        insertAccessRecord(jdbc, ACCESS_RECORD_ID, CLIENT_ONE_ID, "CLIENT_INACTIVE");
        jdbc.update("""
                insert into gym.payments
                    (id, client_id, amount, currency, payment_method, status,
                     paid_at, registered_by_user_id, registered_at_branch_id)
                values (?, ?, 25.00, 'USD', 'CASH', 'PAID',
                        current_timestamp, ?, ?)
                """, PAYMENT_ID, CLIENT_ONE_ID, USER_ID, INITIAL_BRANCH_ID);
    }

    private static void insertPlan(JdbcTemplate jdbc, UUID id, String name, long version) {
        jdbc.update("""
                insert into gym.membership_plans
                    (id, name, duration_value, duration_unit, list_price, currency,
                     created_by_user_id, updated_by_user_id, created_at, updated_at, version)
                values (?, ?, 1, 'MONTH', 25.00, 'USD', ?, ?, ?, ?, ?)
                """, id, name, USER_ID, USER_ID,
                Timestamp.from(PLAN_UPDATED_AT), Timestamp.from(PLAN_UPDATED_AT), version);
    }

    private static void insertClient(JdbcTemplate jdbc, UUID id, String name, String phone) {
        jdbc.update("""
                insert into gym.clients
                    (id, first_name, last_name, phone, created_by_user_id)
                values (?, ?, 'Fixture', ?, ?)
                """, id, name, phone, USER_ID);
    }

    private static void insertMembership(JdbcTemplate jdbc, UUID id, UUID clientId) {
        jdbc.update("""
                insert into gym.memberships (id, client_id, created_by_user_id)
                values (?, ?, ?)
                """, id, clientId, USER_ID);
    }

    private static void insertPeriod(
            JdbcTemplate jdbc, UUID id, UUID membershipId, UUID planId, UUID branchId) {
        jdbc.update("""
                insert into gym.membership_periods
                    (id, membership_id, period_number, period_source, membership_plan_id,
                     plan_code_snapshot, plan_name_snapshot, duration_value_snapshot,
                     duration_unit_snapshot, list_price, currency, discount_amount,
                     final_price, starts_on, base_ends_on, effective_ends_on,
                     created_by_user_id, registered_at_branch_id, created_at, updated_at)
                values (?, ?, 1, 'INITIAL', ?, 'PLAN-COVERAGE', 'Coverage Plan',
                        1, 'MONTH', 25.00, 'USD', 0, 25.00,
                        date '2026-01-01', date '2026-02-01', date '2026-02-01',
                        ?, ?, ?, ?)
                """, id, membershipId, planId, USER_ID, branchId,
                Timestamp.from(PERIOD_UPDATED_AT), Timestamp.from(PERIOD_UPDATED_AT));
    }

    private static void insertAccessRecord(
            JdbcTemplate jdbc, UUID id, UUID clientId, String reasonCode) {
        String clientCode = jdbc.queryForObject(
                "select client_code from gym.clients where id = ?", String.class, clientId);
        jdbc.update("""
                insert into gym.access_records
                    (id, entered_code, client_id, client_code_snapshot, decision,
                     reason_code, details, occurred_at, recorded_by_user_id,
                     identification_source, branch_id)
                values (?, ?, ?, ?, 'DENIED', ?, 'Migration fixture access attempt',
                        current_timestamp, ?, 'CLIENT_CODE', ?)
                """, id, clientCode, clientId, clientCode, reasonCode,
                USER_ID, INITIAL_BRANCH_ID);
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("select count(*) from gym." + table, Integer.class);
    }
}
