package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.user.application.StaffAssignmentAuthorizationQuery;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentAdminQuery;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentDuplicateException;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSearchPage;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSearchQuery;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSearchResult;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSortDirection;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSortField;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentStore;
import io.github.guillermodubon.coachgym.user.application.StaffScopeStore;
import io.github.guillermodubon.coachgym.user.application.StaffScopeVersionConflictException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@ActiveProfiles("test")
class StaffBranchAssignmentPersistenceIntegrationTest {

    private static final UUID INITIAL_BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StaffScopeQuery scopeQuery;

    @Autowired
    private StaffScopeStore scopeStore;

    @Autowired
    private StaffBranchAssignmentQuery assignmentQuery;

    @Autowired
    private StaffBranchAssignmentStore assignmentStore;

    @Autowired
    private StaffAssignmentAuthorizationQuery authorizationQuery;

    @Autowired
    private StaffBranchAssignmentAdminQuery adminQuery;

    @Autowired
    private AuthorizedBranchQuery authorizedBranchQuery;

    @Autowired
    private BranchOperationContextResolver branchOperationContextResolver;

    @Autowired
    private ActiveBranchContextManager activeBranchContextManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearFixtures() {
        jdbcTemplate.execute(
                "truncate table gym.staff_branch_assignments, gym.staff_scopes, gym.users cascade");
        jdbcTemplate.update("""
                delete from gym.clients
                 where first_name in ('Race', 'Assignment Race')
                """);
        jdbcTemplate.update("""
                update gym.gym_branches
                   set status = 'INACTIVE', version = version + 1
                 where status = 'ACTIVE'
                   and (code like 'LOCK_%' or code like 'ASSIGNMENT_LOCK_%')
                """);
    }

    @Test
    void readsScopeAndAuthorizationFactsWithoutCredentialOrProfileData() {
        UUID adminId = insertUser("persistence-admin", "ADMIN");
        insertScope(adminId, StaffScopeType.ORGANIZATION);

        StaffScopeDetails scope = scopeQuery.findScope(adminId).orElseThrow();
        StaffAuthorizationContext context = scopeQuery.findAuthorizationContext(adminId)
                .orElseThrow();

        assertThat(scope.scopeType()).isEqualTo(StaffScopeType.ORGANIZATION);
        assertThat(scope.roles()).containsExactly(RoleCode.ADMIN);
        assertThat(context.organizationAdmin()).isTrue();
        assertThat(context.assignedBranchIds()).isEmpty();
    }

    @Test
    void scopeTransitionsUseTheExpectedVersionAndRecordTheActor() {
        UUID targetId = insertUser("persistence-scope-target", "ADMIN");
        UUID actorId = insertUser("persistence-scope-actor", "ADMIN");
        insertScope(targetId, StaffScopeType.ORGANIZATION);
        insertScope(actorId, StaffScopeType.ORGANIZATION);
        Instant occurredAt = Instant.parse("2026-01-15T10:00:00Z");

        StaffScopeDetails changed = scopeStore.update(
                new ChangeStaffScopeCommand(targetId, StaffScopeType.BRANCH, "operational scope", 0),
                actorId,
                occurredAt);

        assertThat(changed.scopeType()).isEqualTo(StaffScopeType.BRANCH);
        assertThat(changed.grantedByUserId()).isEqualTo(actorId);
        assertThat(changed.version()).isEqualTo(1);
        assertThatThrownBy(() -> scopeStore.update(
                new ChangeStaffScopeCommand(targetId, StaffScopeType.ORGANIZATION, "stale", 0),
                actorId,
                occurredAt.plusSeconds(1)))
                .isInstanceOf(StaffScopeVersionConflictException.class);
    }

    @Test
    void assignmentLifecycleIsAppendOnlyAndOptimisticallyVersioned() {
        UUID adminId = insertUser("assignment-admin", "ADMIN");
        UUID receptionistId = insertUser("assignment-receptionist", "RECEPTIONIST");
        insertScope(adminId, StaffScopeType.ORGANIZATION);
        insertScope(receptionistId, StaffScopeType.BRANCH);
        Instant assignedAt = Instant.parse("2026-01-01T10:00:00Z");

        StaffBranchAssignmentDetails assigned = assignmentStore.assign(
                new AssignStaffToBranchCommand(receptionistId, INITIAL_BRANCH_ID, "bootstrap"),
                adminId,
                assignedAt);

        assertThat(assignmentQuery.findActive(receptionistId))
                .extracting(StaffBranchAssignmentDetails::id)
                .containsExactly(assigned.id());
        assertThatThrownBy(() -> assignmentStore.assign(
                new AssignStaffToBranchCommand(receptionistId, INITIAL_BRANCH_ID, "duplicate"),
                adminId,
                assignedAt.plusSeconds(1)))
                .isInstanceOf(StaffBranchAssignmentDuplicateException.class);

        StaffBranchAssignmentDetails ended = assignmentStore.end(
                new EndStaffBranchAssignmentCommand(assigned.id(), "moved", assigned.version()),
                adminId,
                assignedAt.plusSeconds(60));
        assertThat(ended.status()).isEqualTo(StaffBranchAssignmentStatus.ENDED);
        assertThat(assignmentQuery.findActive(receptionistId)).isEmpty();
        assertThatThrownBy(() -> assignmentStore.end(
                new EndStaffBranchAssignmentCommand(assigned.id(), "stale", assigned.version()),
                adminId,
                assignedAt.plusSeconds(120)))
                .isInstanceOf(StaffBranchAssignmentStateConflictException.class);
    }

    @Test
    void administrativeHistoryIsFilteredBoundedAndDeterministicallyOrdered() {
        UUID adminId = insertUser("search-admin", "ADMIN");
        UUID firstSubject = insertUser("search-first", "RECEPTIONIST");
        UUID secondSubject = insertUser("search-second", "RECEPTIONIST");
        insertScope(adminId, StaffScopeType.ORGANIZATION);
        insertScope(firstSubject, StaffScopeType.BRANCH);
        insertScope(secondSubject, StaffScopeType.BRANCH);
        Instant timestamp = Instant.parse("2026-02-01T10:00:00Z");
        assignmentStore.assign(
                new AssignStaffToBranchCommand(firstSubject, INITIAL_BRANCH_ID, "first"),
                adminId, timestamp);
        assignmentStore.assign(
                new AssignStaffToBranchCommand(secondSubject, INITIAL_BRANCH_ID, "second"),
                adminId, timestamp);

        StaffBranchAssignmentSearchPage administrativePage = adminQuery.findPage(
                new StaffBranchAssignmentSearchQuery(
                        null, "search", RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                        INITIAL_BRANCH_ID, StaffBranchAssignmentStatus.ACTIVE,
                        timestamp.minusSeconds(1), timestamp.plusSeconds(1),
                        0, 10,
                        StaffBranchAssignmentSortField.STAFF_IDENTIFIER,
                        StaffBranchAssignmentSortDirection.ASC));
        assertThat(administrativePage.totalElements()).isEqualTo(2);
        assertThat(administrativePage.items())
                .extracting(StaffBranchAssignmentSearchResult::staffIdentifier)
                .containsExactly("search-first", "search-second");

        // Exercise the public bounded query through the assignment port and verify
        // its deterministic newest-first contract.
        StaffBranchAssignmentPage history = assignmentQuery.findPage(
                firstSubject, 0, 10);
        assertThat(history.items()).hasSize(1);
        assertThat(history.items().getFirst().userId()).isEqualTo(firstSubject);
        assertThat(history.items().getFirst().assignedAt()).isEqualTo(timestamp);
        assertThat(StaffBranchAssignmentSearchQuery.defaults().size()).isEqualTo(25);
        assertThatThrownBy(() -> new StaffBranchAssignmentSearchQuery(
                null, "x".repeat(121), null, null, null, null,
                null, null, 0, 25, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void antiLockoutFactsAndAuthorizedBranchProjectionAreDatabaseBacked() {
        UUID orgAdminId = insertUser("facts-org-admin", "ADMIN");
        UUID branchAdminId = insertUser("facts-branch-admin", "ADMIN");
        UUID receptionistId = insertUser("facts-receptionist", "RECEPTIONIST");
        insertScope(orgAdminId, StaffScopeType.ORGANIZATION);
        insertScope(branchAdminId, StaffScopeType.BRANCH);
        insertScope(receptionistId, StaffScopeType.BRANCH);
        assignmentStore.assign(
                new AssignStaffToBranchCommand(branchAdminId, INITIAL_BRANCH_ID, "branch admin"),
                orgAdminId, Instant.parse("2026-03-01T10:00:00Z"));
        assignmentStore.assign(
                new AssignStaffToBranchCommand(receptionistId, INITIAL_BRANCH_ID, "reception"),
                orgAdminId, Instant.parse("2026-03-01T10:01:00Z"));

        assertThat(authorizationQuery.countActiveOrganizationAdministrators()).isGreaterThanOrEqualTo(1);
        assertThat(authorizationQuery.isLastActiveOrganizationAdministrator(orgAdminId)).isTrue();
        assertThat(authorizationQuery.hasActiveBranchAssignment(
                branchAdminId, INITIAL_BRANCH_ID)).isTrue();
        assertThat(authorizationQuery.branchAdministratorControlsBranch(
                branchAdminId, INITIAL_BRANCH_ID)).isTrue();
        assertThat(authorizationQuery.branchAdministratorControlsTarget(
                branchAdminId, receptionistId)).isTrue();
        assertThat(assignmentQuery.findActiveByBranch(INITIAL_BRANCH_ID))
                .extracting(StaffBranchAssignmentDetails::userId)
                .containsExactlyInAnyOrder(branchAdminId, receptionistId);
        assertThat(authorizedBranchQuery.findAuthorizedActiveBranches(orgAdminId))
                .extracting(AuthorizedBranchSummary::id)
                .containsExactly(INITIAL_BRANCH_ID);
        assertThat(authorizedBranchQuery.findAuthorizedActiveBranches(receptionistId))
                .extracting(AuthorizedBranchSummary::id)
                .containsExactly(INITIAL_BRANCH_ID);
    }

    @Test
    void branchDeactivationSerializesWithCreationAndStaleCreationFailsClosed() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        UUID administratorId = insertUser("branch-lock-admin", "ADMIN");
        insertScope(administratorId, StaffScopeType.ORGANIZATION);

        UUID firstBranch = insertBranch("LOCK_FIRST");
        UUID firstReceptionist = assignedReceptionist(
                administratorId, firstBranch, "branch-lock-first");
        CountDownLatch firstContextResolved = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> createBeforeDeactivation = executor.submit(() ->
                    transactions.execute(status -> {
                        var context = branchOperationContextResolver.resolveOperation(firstReceptionist);
                        if (!firstBranch.equals(context.activeBranchId())) {
                            throw new AssertionError("The active branch context was not resolved.");
                        }
                        firstContextResolved.countDown();
                        await(allowFirstCommit);
                        UUID clientId = UUID.randomUUID();
                        jdbcTemplate.update("""
                                insert into gym.clients
                                    (id, first_name, last_name, phone, home_branch_id)
                                values (?, 'Race', 'Before', '5550201', ?)
                                """, clientId, context.activeBranchId());
                        return clientId;
                    }));
            assertThat(firstContextResolved.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Integer> deactivateAfterCreate = executor.submit(() ->
                    transactions.execute(status -> jdbcTemplate.update("""
                            update gym.gym_branches
                               set status = 'INACTIVE', version = version + 1
                             where id = ?
                            """, firstBranch)));
            awaitDatabaseLock("update gym.gym_branches");
            allowFirstCommit.countDown();

            UUID createdClient = createBeforeDeactivation.get(10, TimeUnit.SECONDS);
            assertThat(deactivateAfterCreate.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select home_branch_id from gym.clients where id = ?",
                    UUID.class, createdClient)).isEqualTo(firstBranch);

            UUID secondBranch = insertBranch("LOCK_SECOND");
            UUID secondReceptionist = assignedReceptionist(
                    administratorId, secondBranch, "branch-lock-second");
            CountDownLatch branchDeactivationCommitted = new CountDownLatch(1);
            CountDownLatch allowDeactivationCommit = new CountDownLatch(1);
            Future<Integer> deactivateBeforeCreate = executor.submit(() ->
                    transactions.execute(status -> {
                        int updated = jdbcTemplate.update("""
                                update gym.gym_branches
                                   set status = 'INACTIVE', version = version + 1
                                 where id = ?
                                """, secondBranch);
                        branchDeactivationCommitted.countDown();
                        await(allowDeactivationCommit);
                        return updated;
                    }));
            assertThat(branchDeactivationCommitted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<UUID> rejectedCreate = executor.submit(() ->
                    transactions.execute(status -> {
                        var context = branchOperationContextResolver.resolveOperation(secondReceptionist);
                        UUID clientId = UUID.randomUUID();
                        jdbcTemplate.update("""
                                insert into gym.clients
                                    (id, first_name, last_name, phone, home_branch_id)
                                values (?, 'Race', 'After', '5550202', ?)
                                """, clientId, context.activeBranchId());
                        return clientId;
                    }));
            awaitDatabaseLock("for share of b");
            allowDeactivationCommit.countDown();

            assertThat(deactivateBeforeCreate.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThatThrownBy(() -> rejectedCreate.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ActiveBranchContextUnavailableException.class);
            assertThat(jdbcTemplate.queryForObject("""
                    select count(*) from gym.clients
                     where first_name = 'Race' and last_name = 'After'
                    """, Integer.class)).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void assignmentEndSerializesWithMutationAndStaleAssignmentFailsClosed() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        UUID administratorId = insertUser("assignment-lock-admin", "ADMIN");
        insertScope(administratorId, StaffScopeType.ORGANIZATION);
        UUID activeBranch = insertBranch("ASSIGNMENT_LOCK_ACTIVE");
        UUID remainingBranch = insertBranch("ASSIGNMENT_LOCK_REMAINING");
        UUID receptionistId = insertUser("assignment-lock-receptionist", "RECEPTIONIST");
        insertScope(receptionistId, StaffScopeType.BRANCH);
        StaffBranchAssignmentDetails activeAssignment = assignmentStore.assign(
                new AssignStaffToBranchCommand(receptionistId, activeBranch, "lock fixture"),
                administratorId,
                Instant.now());
        assignmentStore.assign(
                new AssignStaffToBranchCommand(receptionistId, remainingBranch, "lock fixture"),
                administratorId,
                Instant.now());

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            activeBranchContextManager.select(
                    receptionistId, new SelectActiveBranchCommand(activeBranch));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        CountDownLatch operationResolved = new CountDownLatch(1);
        CountDownLatch allowOperationCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> createBeforeAssignmentEnd = executor.submit(() -> {
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                try {
                    return transactions.execute(status -> {
                        var context = branchOperationContextResolver.resolveOperation(receptionistId);
                        if (!activeBranch.equals(context.activeBranchId())) {
                            throw new AssertionError("The selected branch was not resolved.");
                        }
                        operationResolved.countDown();
                        await(allowOperationCommit);
                        UUID clientId = UUID.randomUUID();
                        jdbcTemplate.update("""
                                insert into gym.clients
                                    (id, first_name, last_name, phone, home_branch_id)
                                values (?, 'Assignment Race', 'Before End', '5550203', ?)
                                """, clientId, context.activeBranchId());
                        return clientId;
                    });
                } finally {
                    RequestContextHolder.resetRequestAttributes();
                }
            });
            assertThat(operationResolved.await(5, TimeUnit.SECONDS)).isTrue();

            Future<StaffBranchAssignmentDetails> endAfterOperation = executor.submit(() ->
                    transactions.execute(status -> assignmentStore.end(
                            new EndStaffBranchAssignmentCommand(
                                    activeAssignment.id(), "concurrent reassignment",
                                    activeAssignment.version()),
                            administratorId,
                            Instant.now())));
            awaitDatabaseLock("update gym.staff_branch_assignments");
            allowOperationCommit.countDown();

            UUID createdClient = createBeforeAssignmentEnd.get(10, TimeUnit.SECONDS);
            assertThat(endAfterOperation.get(10, TimeUnit.SECONDS).status())
                    .isEqualTo(StaffBranchAssignmentStatus.ENDED);
            assertThat(jdbcTemplate.queryForObject(
                    "select home_branch_id from gym.clients where id = ?", UUID.class, createdClient))
                    .isEqualTo(activeBranch);

            Future<UUID> rejectedCreate = executor.submit(() -> {
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                try {
                    return transactions.execute(status -> {
                        var context = branchOperationContextResolver.resolveOperation(receptionistId);
                        UUID clientId = UUID.randomUUID();
                        jdbcTemplate.update("""
                                insert into gym.clients
                                    (id, first_name, last_name, phone, home_branch_id)
                                values (?, 'Assignment Race', 'After End', '5550204', ?)
                                """, clientId, context.activeBranchId());
                        return clientId;
                    });
                } finally {
                    RequestContextHolder.resetRequestAttributes();
                }
            });
            assertThatThrownBy(() -> rejectedCreate.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ActiveBranchContextUnavailableException.class);
            assertThat(jdbcTemplate.queryForObject("""
                    select count(*) from gym.clients
                     where first_name = 'Assignment Race' and last_name = 'After End'
                    """, Integer.class)).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void persistencePortsDoNotExposeDestructiveAssignmentOperation() {
        assertThat(List.of(StaffBranchAssignmentStore.class.getMethods()))
                .allMatch(method -> !method.getName().equalsIgnoreCase("delete"));
    }

    private UUID insertUser(String username, String roleCode) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.users
                    (id, username, email, password_hash, first_name, last_name, status)
                values (?, ?, ?, 'test-hash', 'Persist', 'Test', 'ACTIVE')
                """, userId, username, username + "@example.com");
        jdbcTemplate.update("""
                insert into gym.user_roles (user_id, role_id)
                select ?, id from gym.roles where role_code = ?
                """, userId, roleCode);
        return userId;
    }

    private void insertScope(UUID userId, StaffScopeType scopeType) {
        jdbcTemplate.update("""
                insert into gym.staff_scopes (user_id, scope_type, version)
                values (?, ?, 0)
                """, userId, scopeType.name());
    }

    private UUID insertBranch(String code) {
        UUID branchId = UUID.randomUUID();
        String prefix = code.substring(0, Math.min(code.length(), 22));
        String uniqueCode = prefix + "_"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, is_initial_branch, version)
                select ?, id, ?, ?, 'America/El_Salvador', 'ACTIVE', false, 0
                  from gym.organizations
                 where is_canonical = true
                """, branchId, uniqueCode, uniqueCode + " Branch");
        return branchId;
    }

    private UUID assignedReceptionist(UUID administratorId, UUID branchId, String username) {
        UUID receptionistId = insertUser(username, "RECEPTIONIST");
        insertScope(receptionistId, StaffScopeType.BRANCH);
        assignmentStore.assign(
                new AssignStaffToBranchCommand(receptionistId, branchId, "concurrency fixture"),
                administratorId,
                Instant.now());
        return receptionistId;
    }

    private void awaitDatabaseLock(String queryFragment) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbcTemplate.queryForObject("""
                    select exists (
                        select 1
                          from pg_stat_activity
                         where wait_event_type = 'Lock'
                           and position(lower(?) in lower(query)) > 0)
                    """, Boolean.class, queryFragment);
            if (Boolean.TRUE.equals(waiting)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Expected a transaction to wait on the branch lock.");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrency test synchronization timed out.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrency test was interrupted.", exception);
        }
    }
}
