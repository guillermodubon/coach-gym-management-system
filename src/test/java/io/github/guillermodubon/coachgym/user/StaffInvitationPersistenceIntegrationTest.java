package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.user.application.StaffIdentityDataAccessException;
import io.github.guillermodubon.coachgym.user.application.StaffAccountActivationDeliveryClaim;
import io.github.guillermodubon.coachgym.user.application.StaffAccountActivationDeliveryStore;
import io.github.guillermodubon.coachgym.auth.application.AdminReauthenticationAttemptStore;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationDraft;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationPage;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationPageQuery;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationPersistence;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationRecord;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationAcceptanceApplicationService;
import io.github.guillermodubon.coachgym.user.application.StaffProfileQuery;
import io.github.guillermodubon.coachgym.user.application.StaffTokenFingerprint;
import io.github.guillermodubon.coachgym.user.application.StaffTokenProtector;
import io.github.guillermodubon.coachgym.user.application.StaffTokenPurpose;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@ActiveProfiles("test")
class StaffInvitationPersistenceIntegrationTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000001");
    private static final UUID ACTIVE_BRANCH_ID = UUID.fromString(
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
    private StaffInvitationPersistence invitations;

    @Autowired
    private StaffInvitationAcceptanceApplicationService acceptanceService;

    @Autowired
    private StaffAccountActivationDeliveryStore activationDeliveries;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private StaffProfileQuery staffProfileQuery;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private io.github.guillermodubon.coachgym.user.application.StaffOneTimeTokenGenerator tokenGenerator;

    @Autowired
    private StaffTokenProtector tokenProtector;

    @Autowired
    private AdminReauthenticationAttemptStore reauthenticationAttempts;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearStaffIdentityTokenRows() {
        jdbcTemplate.execute("truncate table gym.staff_account_activation_deliveries, "
                + "gym.staff_invitation_delivery_attempts, "
                + "gym.staff_invitation_branches, "
                + "gym.staff_invitations, gym.staff_password_recovery_tokens, "
                + "gym.staff_identity_abuse_windows");
        jdbcTemplate.execute("truncate table gym.staff_admin_reauthentication_failures");
    }

    @Test
    void flywayAppliesV38AndCreatesNoPlaintextTokenColumn() {
        Integer applied = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                 where version = '38' and success
                """, Integer.class);
        List<String> tokenColumns = jdbcTemplate.queryForList("""
                select column_name
                  from information_schema.columns
                 where table_schema = 'gym'
                   and table_name in ('staff_invitations', 'staff_password_recovery_tokens')
                   and column_name ilike '%token%'
                 order by table_name, column_name
                """, String.class);
        List<String> deleteRules = jdbcTemplate.queryForList("""
                select constraint_name || ':' || delete_rule
                  from information_schema.referential_constraints
                 where constraint_schema = 'gym'
                   and constraint_name in (
                       'fk_staff_invitations_organization',
                       'fk_staff_invitations_invited_by_user',
                       'fk_staff_invitations_accepted_user',
                       'fk_staff_invitation_branches_invitation',
                       'fk_staff_invitation_branches_branch',
                       'fk_staff_password_recovery_user')
                 order by constraint_name
                """, String.class);
        List<String> identityIndexes = jdbcTemplate.queryForList("""
                select indexname
                  from pg_indexes
                 where schemaname = 'gym'
                   and tablename in (
                       'staff_invitations',
                       'staff_invitation_branches',
                       'staff_password_recovery_tokens')
                 order by indexname
                """, String.class);
        List<String> identityIndexDefinitions = jdbcTemplate.queryForList("""
                select indexdef
                  from pg_indexes
                 where schemaname = 'gym'
                   and tablename in (
                       'staff_invitations',
                       'staff_invitation_branches',
                       'staff_password_recovery_tokens')
                 order by indexname
                """, String.class);

        assertThat(applied).isEqualTo(1);
        assertThat(tokenColumns).containsExactlyInAnyOrder(
                "token_fingerprint", "token_scheme", "token_fingerprint", "token_scheme");
        assertThat(deleteRules).hasSize(6).allMatch(rule -> rule.endsWith(":RESTRICT"));
        assertThat(identityIndexes).containsExactlyInAnyOrder(
                "staff_invitations_pkey",
                "uq_staff_invitations_accepted_user",
                "uq_staff_invitations_token_fingerprint",
                "uq_staff_invitations_pending_email",
                "idx_staff_invitations_inviter_created_at",
                "idx_staff_invitations_organization_created_at",
                "idx_staff_invitations_pending_expiration",
                "pk_staff_invitation_branches",
                "idx_staff_invitation_branches_branch_invitation",
                "staff_password_recovery_tokens_pkey",
                "uq_staff_password_recovery_token_fingerprint",
                "uq_staff_password_recovery_pending_user",
                "idx_staff_password_recovery_pending_expiration");
        assertThat(identityIndexDefinitions).doesNotHaveDuplicates();
    }

    @Test
    void flywayAppliesV39AttemptLedgersWithoutRecipientOrSecretColumns() {
        Integer applied = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                 where version = '39' and success
                """, Integer.class);
        List<String> sensitiveColumns = jdbcTemplate.queryForList("""
                select column_name
                  from information_schema.columns
                 where table_schema = 'gym'
                   and table_name in (
                       'staff_invitation_delivery_attempts',
                       'staff_admin_reauthentication_failures')
                   and column_name ~* '(email|recipient|body|token|password|secret)'
                """, String.class);

        assertThat(applied).isEqualTo(1);
        assertThat(sensitiveColumns).isEmpty();
    }

    @Test
    void flywayAppliesActivationDeliveryOutboxWithoutRecipientOrSecretColumns() {
        Integer applied = jdbcTemplate.queryForObject("""
                select count(*) from flyway_schema_history
                 where version = '40' and success
                """, Integer.class);
        List<String> sensitiveColumns = jdbcTemplate.queryForList("""
                select column_name
                  from information_schema.columns
                 where table_schema = 'gym'
                   and table_name = 'staff_account_activation_deliveries'
                   and column_name ~* '(email|recipient|body|token|password|secret)'
                """, String.class);

        assertThat(applied).isEqualTo(1);
        assertThat(sensitiveColumns).isEmpty();
    }

    @Test
    void invitationCreateStoresOnlyFingerprintAndSnapshotsValidatedBranches() {
        UUID actorId = createOrganizationAdministrator();
        String rawToken = tokenGenerator.generate();
        StaffTokenFingerprint fingerprint = tokenProtector.fingerprint(
                rawToken, StaffTokenPurpose.INVITATION);
        Instant createdAt = now();
        StaffInvitationDraft draft = invitationDraft(
                actorId,
                "branch-admin@example.test",
                RoleCode.ADMIN,
                StaffScopeType.BRANCH,
                Set.of(ACTIVE_BRANCH_ID),
                fingerprint,
                createdAt);

        StaffInvitationRecord created = invitations.create(draft);
        Integer rawTokenMatches = jdbcTemplate.queryForObject("""
                select count(*) from gym.staff_invitations
                 where token_fingerprint = ?
                """, Integer.class, rawToken);

        assertThat(created.status()).isEqualTo(StaffInvitationStatus.PENDING);
        assertThat(created.proposedBranchIds()).containsExactly(ACTIVE_BRANCH_ID);
        assertThat(created.tokenFingerprint()).isEqualTo(fingerprint);
        assertThat(created.version()).isZero();
        assertThat(rawTokenMatches).isZero();
        assertThat(draft.toString()).doesNotContain(rawToken, fingerprint.value());
        assertThat(created.toString()).doesNotContain(rawToken, fingerprint.value(), "branch-admin@example.test");
        assertThat(invitations.findPendingByFingerprint(fingerprint))
                .get()
                .extracting(StaffInvitationRecord::invitationId)
                .isEqualTo(created.invitationId());
    }

    @Test
    void pendingEmailAndTokenFingerprintsAreUniqueAndFailuresAreSafe() {
        UUID actorId = createOrganizationAdministrator();
        String sharedToken = tokenGenerator.generate();
        StaffTokenFingerprint sharedFingerprint = tokenProtector.fingerprint(
                sharedToken, StaffTokenPurpose.INVITATION);
        Instant createdAt = now();
        invitations.create(invitationDraft(
                actorId, "duplicate@example.test", RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                Set.of(), sharedFingerprint, createdAt));

        assertThatThrownBy(() -> invitations.create(invitationDraft(
                actorId, "duplicate@example.test", RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                Set.of(ACTIVE_BRANCH_ID), tokenProtector.fingerprint(
                        tokenGenerator.generate(), StaffTokenPurpose.INVITATION), createdAt)))
                .isInstanceOf(StaffIdentityStateConflictException.class)
                .hasMessageNotContaining("duplicate@example.test");

        assertThatThrownBy(() -> invitations.create(invitationDraft(
                actorId, "different@example.test", RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                Set.of(), sharedFingerprint, createdAt)))
                .isInstanceOf(StaffIdentityDataAccessException.class)
                .hasMessageNotContaining(sharedToken)
                .hasMessageNotContaining(sharedFingerprint.value());
    }

    @Test
    void concurrentInvitationCreationForOneEmailHasExactlyOneWinner() throws Exception {
        UUID actorId = createOrganizationAdministrator();
        String email = "duplicate-race-" + UUID.randomUUID() + "@example.test";
        Instant createdAt = now();
        StaffInvitationDraft firstDraft = invitationDraft(
                actorId, email, RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                Set.of(), newFingerprint(), createdAt);
        StaffInvitationDraft secondDraft = invitationDraft(
                actorId, email, RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                Set.of(ACTIVE_BRANCH_ID), newFingerprint(), createdAt);

        List<Boolean> outcomes = runConcurrently(
                () -> createInvitationIfAvailable(firstDraft),
                () -> createInvitationIfAvailable(secondDraft));

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.staff_invitations
                 where email_normalized = ? and status = 'PENDING'
                """, Integer.class, email)).isEqualTo(1);
    }

    @Test
    void pageQueryIsBoundedFilteredAndDeterministicallySorted() {
        UUID actorId = createOrganizationAdministrator();
        Instant firstAt = now();
        StaffInvitationRecord first = invitations.create(invitationDraft(
                actorId, "page-one@example.test", RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                Set.of(), newFingerprint(), firstAt));
        StaffInvitationRecord second = invitations.create(invitationDraft(
                actorId, "page-two@example.test", RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                Set.of(), newFingerprint(), firstAt.plusSeconds(1)));
        StaffInvitationRecord third = invitations.create(invitationDraft(
                actorId, "page-three@example.test", RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                Set.of(ACTIVE_BRANCH_ID), newFingerprint(), firstAt.plusSeconds(2)));

        StaffInvitationPage firstPage = invitations.findPage(
                new StaffInvitationPageQuery(ORGANIZATION_ID, StaffInvitationStatus.PENDING, 0, 2));
        StaffInvitationPage secondPage = invitations.findPage(
                new StaffInvitationPageQuery(ORGANIZATION_ID, StaffInvitationStatus.PENDING, 1, 2));

        assertThat(firstPage.invitations()).hasSize(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.invitations()).hasSize(1);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(firstPage.invitations()).extracting(StaffInvitationRecord::invitationId)
                .containsExactly(third.invitationId(), second.invitationId());
        assertThat(secondPage.invitations().getFirst().invitationId())
                .isEqualTo(first.invitationId());
        assertThatThrownBy(() -> new StaffInvitationPageQuery(
                ORGANIZATION_ID, null, 0, StaffInvitationPageQuery.MAX_PAGE_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaffInvitationPageQuery(
                ORGANIZATION_ID, null, StaffInvitationPageQuery.MAX_OFFSET + 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rotationAndAcceptanceAreOptimisticAndFinalInvitationsCannotChange() {
        UUID actorId = createOrganizationAdministrator();
        Instant createdAt = now();
        String originalToken = tokenGenerator.generate();
        StaffTokenFingerprint originalFingerprint = tokenProtector.fingerprint(
                originalToken, StaffTokenPurpose.INVITATION);
        StaffInvitationRecord pending = invitations.create(invitationDraft(
                actorId, "accept-me@example.test", RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                Set.of(), originalFingerprint, createdAt));

        Instant resentAt = createdAt.plus(StaffInvitationPolicy.RESEND_COOLDOWN).plusSeconds(1);
        StaffTokenFingerprint rotatedFingerprint = newFingerprint();
        StaffInvitationRecord rotated = invitations.rotatePendingToken(
                pending.invitationId(), rotatedFingerprint, resentAt,
                resentAt.plus(StaffInvitationPolicy.ADMIN_INVITATION_LIFETIME), pending.version());

        assertThat(invitations.findPendingByFingerprint(originalFingerprint)).isEmpty();
        assertThat(rotated.tokenFingerprint()).isEqualTo(rotatedFingerprint);
        assertThat(rotated.version()).isEqualTo(1);
        assertThatThrownBy(() -> invitations.rotatePendingToken(
                pending.invitationId(), newFingerprint(), resentAt.plusSeconds(1),
                resentAt.plus(StaffInvitationPolicy.ADMIN_INVITATION_LIFETIME), pending.version()))
                .isInstanceOf(StaffIdentityStateConflictException.class);

        UUID acceptedUserId = createUser("accept-me@example.test", "ACTIVE");
        assertThatThrownBy(() -> invitations.transition(
                pending.invitationId(), StaffInvitationStatus.ACCEPTED,
                resentAt.minusSeconds(1), acceptedUserId, rotated.version()))
                .isInstanceOf(StaffIdentityDataAccessException.class);
        StaffInvitationRecord accepted = invitations.transition(
                pending.invitationId(), StaffInvitationStatus.ACCEPTED,
                resentAt.plusSeconds(2), acceptedUserId, rotated.version());

        assertThat(accepted.status()).isEqualTo(StaffInvitationStatus.ACCEPTED);
        assertThat(accepted.version()).isEqualTo(2);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                update gym.staff_invitations set version = version + 1 where id = ?
                """, pending.invitationId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.staff_invitations where id = ?", pending.invitationId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void branchOwnershipAndInvitationExpirationAreEnforcedByPostgres() {
        UUID actorId = createOrganizationAdministrator();
        Instant createdAt = now();
        UUID inactiveBranchId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.gym_branches (
                    id, organization_id, code, name, country_code, timezone, status, version)
                values (?, ?, ?, 'Inactive branch', 'SV', 'America/El_Salvador', 'INACTIVE', 0)
                """, inactiveBranchId, ORGANIZATION_ID,
                "OFF_" + UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 24).toUpperCase(java.util.Locale.ROOT));

        assertThatThrownBy(() -> invitations.create(invitationDraft(
                actorId, "inactive-branch@example.test", RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                Set.of(inactiveBranchId), newFingerprint(), createdAt)))
                .isInstanceOf(StaffIdentityDataAccessException.class);

        StaffInvitationRecord invitation = invitations.create(invitationDraft(
                actorId, "immutable-branch@example.test", RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                Set.of(ACTIVE_BRANCH_ID), newFingerprint(), createdAt));
        assertThatThrownBy(() -> jdbcTemplate.update("""
                delete from gym.staff_invitation_branches where invitation_id = ?
                """, invitation.invitationId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                update gym.staff_invitation_branches set branch_id = ? where invitation_id = ?
                """, inactiveBranchId, invitation.invitationId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void invitationCreationLimitIsSerializedAndEnforcedPerAdministrator() {
        UUID actorId = createOrganizationAdministrator();
        Instant createdAt = now();

        for (int index = 0; index < 20; index++) {
            invitations.create(invitationDraft(
                    actorId,
                    "limit-" + index + "-" + UUID.randomUUID() + "@example.test",
                    RoleCode.ADMIN,
                    StaffScopeType.ORGANIZATION,
                    Set.of(),
                    newFingerprint(),
                    createdAt));
        }

        assertThatThrownBy(() -> invitations.create(invitationDraft(
                actorId, "limit-exceeded@example.test", RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                Set.of(), newFingerprint(), createdAt)))
                .isInstanceOf(StaffInvitationRateLimitException.class)
                .hasMessageNotContaining(actorId.toString());
    }

    @Test
    void deliveryAttemptLimitIsSharedByNormalizedEmailAcrossInvitations() {
        UUID actorId = createOrganizationAdministrator();
        String sharedEmail = "same-recipient@example.test";
        Instant base = now();

        for (int index = 0; index < 3; index++) {
            Instant createdAt = base.plusSeconds(index * 3L);
            StaffInvitationRecord invitation = invitations.create(invitationDraft(
                    actorId, sharedEmail, RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                    Set.of(), newFingerprint(), createdAt));
            invitations.reserveDeliveryAttempt(invitation.invitationId(), invitation.version(), createdAt);
            invitations.completeDeliveryAttempt(
                    invitation.invitationId(), invitation.version(), StaffInvitationDeliveryStatus.FAILED,
                    createdAt.plusSeconds(1));
            invitations.transition(
                    invitation.invitationId(), StaffInvitationStatus.REVOKED,
                    createdAt.plusSeconds(2), null, invitation.version());
        }

        Instant fourthAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        StaffInvitationRecord fourth = invitations.create(invitationDraft(
                actorId, sharedEmail, RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                Set.of(), newFingerprint(), fourthAt));
        assertThatThrownBy(() -> invitations.reserveDeliveryAttempt(
                fourth.invitationId(), fourth.version(), fourthAt))
                .isInstanceOf(StaffInvitationRateLimitException.class);
    }

    @Test
    void administratorReauthenticationFailureLimitExpiresAfterFifteenMinutes() {
        UUID actorId = createOrganizationAdministrator();
        Instant base = now();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        for (int index = 0; index < 5; index++) {
            Instant attemptedAt = base.plusSeconds(index);
            Boolean allowed = transaction.execute(status -> {
                boolean mayCheck = reauthenticationAttempts.beginCheck(actorId, attemptedAt);
                if (mayCheck) {
                    reauthenticationAttempts.recordFailure(actorId, attemptedAt);
                }
                return mayCheck;
            });
            assertThat(allowed).isTrue();
        }
        Boolean blocked = transaction.execute(status ->
                reauthenticationAttempts.beginCheck(actorId, base.plusSeconds(10)));
        assertThat(blocked).isFalse();

        Boolean allowedAfterWindow = transaction.execute(status -> {
            Instant recoveredAt = base.plus(Duration.ofMinutes(16));
            boolean mayCheck = reauthenticationAttempts.beginCheck(actorId, recoveredAt);
            if (mayCheck) {
                reauthenticationAttempts.recordFailure(actorId, recoveredAt);
            }
            return mayCheck;
        });
        assertThat(allowedAfterWindow).isTrue();
    }

    @Test
    void databaseRejectsInvalidRoleScopeStatusExpiryVersionAndAccountTransitions() {
        UUID actorId = createOrganizationAdministrator();
        Instant createdAt = now();

        assertThatThrownBy(() -> insertRawInvitation(
                actorId, RoleCode.RECEPTIONIST, StaffScopeType.ORGANIZATION,
                "PENDING", createdAt, createdAt.plus(PasswordRecoveryPolicy.DEFAULT_LIFETIME), 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawInvitation(
                actorId, RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                "UNKNOWN", createdAt, createdAt.plus(StaffInvitationPolicy.ADMIN_INVITATION_LIFETIME), 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawInvitation(
                actorId, RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                "PENDING", createdAt, createdAt.plus(StaffInvitationPolicy.ADMIN_INVITATION_LIFETIME)
                        .plusSeconds(1), 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawInvitation(
                actorId, RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                "PENDING", createdAt, createdAt.plus(StaffInvitationPolicy.ADMIN_INVITATION_LIFETIME), -1))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawInvitation(
                actorId, RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                "PENDING", createdAt, createdAt.plus(StaffInvitationPolicy.RECEPTIONIST_INVITATION_LIFETIME), 0))
                .isInstanceOf(DataAccessException.class);

        UUID suspendedUser = createUser("reactivate-" + UUID.randomUUID() + "@example.test", "SUSPENDED");
        jdbcTemplate.update("""
                update gym.users
                   set status = 'ACTIVE',
                       security_version = security_version + 1,
                       version = version + 1
                 where id = ?
                """, suspendedUser);
        UUID deactivatedUser = createUser("deactivated-" + UUID.randomUUID() + "@example.test", "DEACTIVATED");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update gym.users set status = 'ACTIVE' where id = ?", deactivatedUser))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void receptionistAcceptanceAtomicallyCreatesProfileScopeAssignmentAndAuthenticatableUser() {
        UUID actorId = createOrganizationAdministrator();
        String email = "accepted-" + UUID.randomUUID() + "@example.test";
        String rawToken = tokenGenerator.generate();
        StaffInvitationRecord invitation = createInvitation(
                actorId, email, RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                Set.of(ACTIVE_BRANCH_ID), rawToken);
        String password = "valid-staff-password-123";

        assertThatThrownBy(() -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)))
                .isInstanceOf(AuthenticationException.class);
        var inspection = acceptanceService.inspect(rawToken, "127.0.0.1");
        assertThat(inspection.maskedEmail()).isEqualTo("a***@example.test");
        assertThat(inspection.toString()).doesNotContain(email, rawToken);

        var accepted = acceptanceService.accept(new AcceptStaffInvitationCommand(
                rawToken, password, password, "Ada", "Lovelace"));

        assertThat(accepted.role()).isEqualTo(RoleCode.RECEPTIONIST);
        assertThat(accepted.scope()).isEqualTo(StaffScopeType.BRANCH);
        assertThat(accepted.branchIds()).containsExactly(ACTIVE_BRANCH_ID);
        assertThat(accepted.toString()).doesNotContain(email, rawToken, password);
        assertThat(invitations.findById(invitation.invitationId()))
                .get()
                .extracting(StaffInvitationRecord::status)
                .isEqualTo(StaffInvitationStatus.ACCEPTED);
        assertThat(invitations.findPendingByFingerprint(tokenProtector.fingerprint(
                rawToken, StaffTokenPurpose.INVITATION))).isEmpty();

        var persisted = jdbcTemplate.queryForMap("""
                select id, username, email, password_hash, first_name, last_name, status
                  from gym.users where id = ?
                """, accepted.userId());
        assertThat(persisted.get("email")).isEqualTo(email);
        assertThat(persisted.get("username").toString()).startsWith("staff-");
        assertThat(persisted.get("first_name")).isEqualTo("Ada");
        assertThat(persisted.get("last_name")).isEqualTo("Lovelace");
        assertThat(persisted.get("status")).isEqualTo("ACTIVE");
        assertThat(persisted.get("password_hash")).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, persisted.get("password_hash").toString())).isTrue();

        assertThat(jdbcTemplate.queryForObject("""
                select r.role_code from gym.user_roles ur
                  join gym.roles r on r.id = ur.role_id where ur.user_id = ?
                """, String.class, accepted.userId())).isEqualTo("RECEPTIONIST");
        assertThat(jdbcTemplate.queryForObject("""
                select scope_type from gym.staff_scopes where user_id = ?
                """, String.class, accepted.userId())).isEqualTo("BRANCH");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.staff_branch_assignments
                 where user_id = ? and branch_id = ? and status = 'ACTIVE'
                """, Integer.class, accepted.userId(), ACTIVE_BRANCH_ID)).isEqualTo(1);
        assertThat(staffProfileQuery.findByUserId(accepted.userId()))
                .get()
                .satisfies(profile -> {
                    assertThat(profile.firstName()).isEqualTo("Ada");
                    assertThat(profile.lastName()).isEqualTo("Lovelace");
                    assertThat(profile.roles()).containsExactly(RoleCode.RECEPTIONIST);
                });
        assertThat(authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)).isAuthenticated()).isTrue();

        assertThatThrownBy(() -> acceptanceService.accept(new AcceptStaffInvitationCommand(
                rawToken, password, password, "Ada", "Lovelace")))
                .isInstanceOf(StaffIdentityStateConflictException.class)
                .hasMessage("Invitation is not available.");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.users where lower(email) = lower(?)", Integer.class, email))
                .isEqualTo(1);
    }

    @Test
    void organizationAdminAcceptanceCreatesNoBranchAssignment() {
        UUID actorId = createOrganizationAdministrator();
        String email = "org-admin-" + UUID.randomUUID() + "@example.test";
        String rawToken = tokenGenerator.generate();
        createInvitation(actorId, email, RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of(), rawToken);
        String password = "another-valid-password-123";

        var accepted = acceptanceService.accept(new AcceptStaffInvitationCommand(
                rawToken, password, password, "Grace", "Hopper"));

        assertThat(accepted.role()).isEqualTo(RoleCode.ADMIN);
        assertThat(accepted.scope()).isEqualTo(StaffScopeType.ORGANIZATION);
        assertThat(accepted.branchIds()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
                select scope_type from gym.staff_scopes where user_id = ?
                """, String.class, accepted.userId())).isEqualTo("ORGANIZATION");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.staff_branch_assignments where user_id = ?
                """, Integer.class, accepted.userId())).isZero();
    }

    @Test
    void failedActivationNoticeIsPersistedAndCanBeRetriedWithoutStoringEmailOrToken() {
        UUID actorId = createOrganizationAdministrator();
        String email = "activation-retry-" + UUID.randomUUID() + "@example.test";
        String rawToken = tokenGenerator.generate();
        StaffInvitationRecord invitation = createInvitation(
                actorId, email, RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of(), rawToken);
        String password = "retry-valid-password-123";

        var accepted = acceptanceService.accept(new AcceptStaffInvitationCommand(
                rawToken, password, password, "Activation", "Retry"));
        var persisted = jdbcTemplate.queryForMap("""
                select status, attempt_count, last_failure_code
                  from gym.staff_account_activation_deliveries
                 where invitation_id = ?
                """, invitation.invitationId());
        List<String> sensitiveColumns = jdbcTemplate.queryForList("""
                select column_name
                  from information_schema.columns
                 where table_schema = 'gym'
                   and table_name = 'staff_account_activation_deliveries'
                   and column_name ~* '(email|recipient|body|token|password|secret)'
                """, String.class);

        assertThat(accepted.userId()).isNotNull();
        assertThat(persisted.get("status")).isEqualTo("FAILED");
        assertThat(persisted.get("attempt_count")).isEqualTo(1);
        assertThat(persisted.get("last_failure_code")).isEqualTo("DELIVERY_FAILED");
        assertThat(sensitiveColumns).isEmpty();

        Instant retryAt = Instant.now().plusSeconds(300).truncatedTo(ChronoUnit.MICROS);
        StaffAccountActivationDeliveryClaim claim = activationDeliveries
                .claim(invitation.invitationId(), retryAt, retryAt.plus(Duration.ofMinutes(2)))
                .orElseThrow();
        assertThat(claim.attemptNumber()).isEqualTo(2);
        assertThat(claim.email().recipient()).isEqualTo(email);
        assertThat(claim.toString()).doesNotContain(email, rawToken, password);

        activationDeliveries.complete(
                claim.deliveryId(),
                claim.attemptNumber(),
                io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus.FAILED,
                retryAt.plusSeconds(1),
                retryAt.plusSeconds(61));

        assertThat(jdbcTemplate.queryForObject("""
                select attempt_count from gym.staff_account_activation_deliveries
                 where invitation_id = ?
                """, Integer.class, invitation.invitationId())).isEqualTo(2);
    }

    @Test
    void expiredActivationDeliveryLeaseBecomesAmbiguousAndIsNotResent() {
        UUID actorId = createOrganizationAdministrator();
        String rawToken = tokenGenerator.generate();
        StaffInvitationRecord invitation = createInvitation(
                actorId,
                "activation-lease-" + UUID.randomUUID() + "@example.test",
                RoleCode.ADMIN,
                StaffScopeType.ORGANIZATION,
                Set.of(),
                rawToken);
        String password = "lease-valid-password-123";
        acceptanceService.accept(new AcceptStaffInvitationCommand(
                rawToken, password, password, "Lease", "Recovery"));

        Instant claimedAt = Instant.now().plusSeconds(300).truncatedTo(ChronoUnit.MICROS);
        StaffAccountActivationDeliveryClaim claim = activationDeliveries
                .claim(invitation.invitationId(), claimedAt, claimedAt.plus(Duration.ofMinutes(2)))
                .orElseThrow();
        Instant afterLease = claimedAt.plus(Duration.ofMinutes(3));

        assertThat(activationDeliveries.claim(
                invitation.invitationId(), afterLease, afterLease.plus(Duration.ofMinutes(2))))
                .isEmpty();
        var persisted = jdbcTemplate.queryForMap("""
                select status, attempt_count, last_failure_code
                  from gym.staff_account_activation_deliveries
                 where invitation_id = ?
                """, invitation.invitationId());
        assertThat(persisted.get("status")).isEqualTo("AMBIGUOUS");
        assertThat(persisted.get("attempt_count")).isEqualTo(2);
        assertThat(persisted.get("last_failure_code")).isEqualTo("OUTCOME_UNKNOWN");
    }

    @Test
    void acceptanceRevalidatesBranchesThatBecameInactiveAfterInvitation() {
        UUID actorId = createOrganizationAdministrator();
        UUID branchId = createAdditionalBranch();
        String email = "inactive-branch-" + UUID.randomUUID() + "@example.test";
        String rawToken = tokenGenerator.generate();
        StaffInvitationRecord invitation = createInvitation(
                actorId, email, RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                Set.of(branchId), rawToken);
        jdbcTemplate.update("update gym.gym_branches set status = 'INACTIVE' where id = ?", branchId);
        String password = "valid-staff-password-123";

        try {
            assertThatThrownBy(() -> acceptanceService.accept(new AcceptStaffInvitationCommand(
                    rawToken, password, password, "Branch", "Closed")))
                    .isInstanceOf(StaffIdentityStateConflictException.class)
                    .hasMessage("Invitation is not available.");
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from gym.users where lower(email) = lower(?)", Integer.class, email))
                    .isZero();
            assertThat(invitations.findById(invitation.invitationId()))
                    .get().extracting(StaffInvitationRecord::status)
                    .isEqualTo(StaffInvitationStatus.PENDING);
        } finally {
            jdbcTemplate.update("update gym.gym_branches set status = 'ACTIVE' where id = ?", branchId);
        }
    }

    @Test
    void acceptanceCannotCreateASecondAccountWhenEmailWasClaimedAfterInvitation() {
        UUID actorId = createOrganizationAdministrator();
        String email = "claimed-" + UUID.randomUUID() + "@example.test";
        String rawToken = tokenGenerator.generate();
        StaffInvitationRecord invitation = createInvitation(
                actorId, email, RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of(), rawToken);
        UUID existingUserId = createUser(email, "ACTIVE");
        String password = "valid-staff-password-123";

        assertThatThrownBy(() -> acceptanceService.accept(new AcceptStaffInvitationCommand(
                rawToken, password, password, "Claimed", "Account")))
                .isInstanceOf(StaffIdentityStateConflictException.class)
                .hasMessage("Invitation is not available.");
        assertThat(invitations.findById(invitation.invitationId()))
                .get().extracting(StaffInvitationRecord::status)
                .isEqualTo(StaffInvitationStatus.PENDING);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.users where id = ?", Integer.class, existingUserId))
                .isEqualTo(1);
    }

    @Test
    void concurrentAcceptanceHasOneWinnerAndDoesNotDuplicateProvisioning() throws Exception {
        UUID actorId = createOrganizationAdministrator();
        String email = "race-" + UUID.randomUUID() + "@example.test";
        String rawToken = tokenGenerator.generate();
        createInvitation(actorId, email, RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                Set.of(ACTIVE_BRANCH_ID), rawToken);
        String password = "race-valid-password-123";
        var command = new AcceptStaffInvitationCommand(rawToken, password, password, "Race", "Winner");
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var first = executor.submit(() -> attemptAcceptance(command, ready, start));
            var second = executor.submit(() -> attemptAcceptance(command, ready, start));
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> results = List.of(
                    first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(30, java.util.concurrent.TimeUnit.SECONDS));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from gym.users where lower(email) = lower(?)", Integer.class, email))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    select count(*) from gym.staff_scopes s
                      join gym.users u on u.id = s.user_id
                     where lower(u.email) = lower(?)
                    """, Integer.class, email)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                    select count(*) from gym.staff_branch_assignments a
                      join gym.users u on u.id = a.user_id
                     where lower(u.email) = lower(?) and a.status = 'ACTIVE'
                    """, Integer.class, email)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void resendRacingInvitationAcceptanceLeavesOnlyTheRotatedOrAcceptedState() throws Exception {
        UUID actorId = createOrganizationAdministrator();
        String email = "resend-race-" + UUID.randomUUID() + "@example.test";
        String rawToken = tokenGenerator.generate();
        Instant createdAt = now().minus(StaffInvitationPolicy.RESEND_COOLDOWN).minusSeconds(5);
        StaffInvitationRecord invitation = invitations.create(invitationDraft(
                actorId, email, RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                Set.of(ACTIVE_BRANCH_ID), tokenProtector.fingerprint(
                        rawToken, StaffTokenPurpose.INVITATION), createdAt));
        Instant resentAt = now();
        StaffTokenFingerprint replacementFingerprint = newFingerprint();
        String password = "resend-race-password-123";
        var command = new AcceptStaffInvitationCommand(
                rawToken, password, password, "Resend", "Race");

        List<Boolean> outcomes = runConcurrently(
                () -> acceptInvitationIfAvailable(command),
                () -> rotateInvitationIfPending(
                        invitation, replacementFingerprint, resentAt));

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        StaffInvitationRecord current = invitations.findById(invitation.invitationId()).orElseThrow();
        if (current.status() == StaffInvitationStatus.ACCEPTED) {
            assertThat(current.version()).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from gym.users where lower(email) = lower(?)",
                    Integer.class, email)).isEqualTo(1);
        } else {
            assertThat(current.status()).isEqualTo(StaffInvitationStatus.PENDING);
            assertThat(current.version()).isEqualTo(1);
            assertThat(current.tokenFingerprint()).isEqualTo(replacementFingerprint);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from gym.users where lower(email) = lower(?)",
                    Integer.class, email)).isZero();
            assertThat(invitations.findPendingByFingerprint(tokenProtector.fingerprint(
                    rawToken, StaffTokenPurpose.INVITATION))).isEmpty();
        }
    }

    @Test
    void revocationRacingInvitationAcceptanceHasExactlyOneFinalTransition() throws Exception {
        UUID actorId = createOrganizationAdministrator();
        String email = "revoke-race-" + UUID.randomUUID() + "@example.test";
        String rawToken = tokenGenerator.generate();
        StaffInvitationRecord invitation = createInvitation(
                actorId, email, RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of(), rawToken);
        String password = "revoke-race-password-123";
        var command = new AcceptStaffInvitationCommand(
                rawToken, password, password, "Revoke", "Race");

        List<Boolean> outcomes = runConcurrently(
                () -> acceptInvitationIfAvailable(command),
                () -> revokeInvitationIfPending(invitation));

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        StaffInvitationRecord current = invitations.findById(invitation.invitationId()).orElseThrow();
        assertThat(current.status()).isIn(StaffInvitationStatus.ACCEPTED, StaffInvitationStatus.REVOKED);
        if (current.status() == StaffInvitationStatus.ACCEPTED) {
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from gym.users where lower(email) = lower(?)",
                    Integer.class, email)).isEqualTo(1);
        } else {
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from gym.users where lower(email) = lower(?)",
                    Integer.class, email)).isZero();
        }
    }

    @Test
    void invitationAcceptanceRacingAnAccountCreationForTheSameEmailCreatesOneUser() throws Exception {
        UUID actorId = createOrganizationAdministrator();
        String email = "account-race-" + UUID.randomUUID() + "@example.test";
        String rawToken = tokenGenerator.generate();
        StaffInvitationRecord invitation = createInvitation(
                actorId, email, RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of(), rawToken);
        String password = "account-race-password-123";
        var command = new AcceptStaffInvitationCommand(
                rawToken, password, password, "Account", "Race");

        List<Boolean> outcomes = runConcurrently(
                () -> acceptInvitationIfAvailable(command),
                () -> createUserIfEmailAvailable(email));

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.users where lower(email) = lower(?)",
                Integer.class, email)).isEqualTo(1);
        StaffInvitationRecord current = invitations.findById(invitation.invitationId()).orElseThrow();
        if (outcomes.getFirst()) {
            assertThat(current.status()).isEqualTo(StaffInvitationStatus.ACCEPTED);
            assertThat(jdbcTemplate.queryForObject("""
                    select count(*) from gym.staff_scopes scope
                      join gym.users staff_user on staff_user.id = scope.user_id
                     where lower(staff_user.email) = lower(?)
                    """, Integer.class, email)).isEqualTo(1);
        } else {
            assertThat(current.status()).isEqualTo(StaffInvitationStatus.PENDING);
        }
    }

    @Test
    void userAndProfileFailureRollsBackInvitationAcceptance() {
        assertProvisioningFailureRollsBack(
                "users", "trg_test_reject_staff_user", "test_reject_staff_user",
                RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());
    }

    @Test
    void roleFailureRollsBackInvitationAcceptance() {
        assertProvisioningFailureRollsBack(
                "user_roles", "trg_test_reject_staff_role", "test_reject_staff_role",
                RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());
    }

    @Test
    void scopeFailureRollsBackInvitationAcceptance() {
        assertProvisioningFailureRollsBack(
                "staff_scopes", "trg_test_reject_staff_scope", "test_reject_staff_scope",
                RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());
    }

    @Test
    void assignmentFailureRollsBackTheAccountRoleScopeAndInvitationAcceptance() {
        assertProvisioningFailureRollsBack(
                "staff_branch_assignments", "trg_test_reject_staff_assignment",
                "test_reject_staff_assignment", RoleCode.RECEPTIONIST,
                StaffScopeType.BRANCH, Set.of(ACTIVE_BRANCH_ID));
    }

    @Test
    void activationOutboxFailureRollsBackAllProvisioningWrites() {
        assertProvisioningFailureRollsBack(
                "staff_account_activation_deliveries", "trg_test_reject_activation_outbox",
                "test_reject_activation_outbox", RoleCode.ADMIN,
                StaffScopeType.ORGANIZATION, Set.of());
    }

    @Test
    void invitationFinalizationFailureRollsBackAllProvisioningWrites() {
        assertProvisioningFailureRollsBack(
                "staff_invitations", "trg_test_reject_invitation_acceptance",
                "test_reject_invitation_acceptance", RoleCode.ADMIN,
                StaffScopeType.ORGANIZATION, Set.of());
    }

    private void assertProvisioningFailureRollsBack(
            String table,
            String trigger,
            String function,
            RoleCode role,
            StaffScopeType scope,
            Set<UUID> branchIds) {
        UUID actorId = createOrganizationAdministrator();
        String email = "rollback-" + UUID.randomUUID() + "@example.test";
        String rawToken = tokenGenerator.generate();
        StaffInvitationRecord invitation = createInvitation(
                actorId, email, role, scope, branchIds, rawToken);
        installFailureTrigger(table, trigger, function);
        String password = "valid-staff-password-123";

        try {
            assertThatThrownBy(() -> acceptanceService.accept(new AcceptStaffInvitationCommand(
                    rawToken, password, password, "Rollback", "Failure")))
                    .isInstanceOf(StaffIdentityDataAccessException.class)
                    .hasMessageNotContaining(email)
                    .hasMessageNotContaining(rawToken)
                    .hasMessageNotContaining(password)
                    .hasMessageNotContaining("Rollback");
        } finally {
            dropFailureTrigger(table, trigger, function);
        }

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.users where lower(email) = lower(?)", Integer.class, email))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.staff_account_activation_deliveries
                 where invitation_id = ?
                """, Integer.class, invitation.invitationId())).isZero();
        assertThat(invitations.findById(invitation.invitationId()))
                .get().extracting(StaffInvitationRecord::status)
                .isEqualTo(StaffInvitationStatus.PENDING);
    }

    private boolean attemptAcceptance(
            AcceptStaffInvitationCommand command,
            java.util.concurrent.CountDownLatch ready,
            java.util.concurrent.CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            acceptanceService.accept(command);
            return true;
        } catch (StaffIdentityStateConflictException conflict) {
            return false;
        }
    }

    private boolean createInvitationIfAvailable(StaffInvitationDraft draft) {
        try {
            invitations.create(draft);
            return true;
        } catch (StaffIdentityStateConflictException conflict) {
            return false;
        }
    }

    private boolean acceptInvitationIfAvailable(AcceptStaffInvitationCommand command) {
        try {
            acceptanceService.accept(command);
            return true;
        } catch (StaffIdentityStateConflictException conflict) {
            return false;
        } catch (StaffIdentityDataAccessException conflict) {
            return false;
        }
    }

    private boolean rotateInvitationIfPending(
            StaffInvitationRecord invitation,
            StaffTokenFingerprint replacementFingerprint,
            Instant resentAt) {
        try {
            invitations.rotatePendingToken(
                    invitation.invitationId(), replacementFingerprint, resentAt,
                    resentAt.plus(StaffInvitationPolicy.defaultLifetime(invitation.proposedRole())),
                    invitation.version());
            return true;
        } catch (StaffIdentityStateConflictException conflict) {
            return false;
        }
    }

    private boolean revokeInvitationIfPending(StaffInvitationRecord invitation) {
        try {
            invitations.transition(invitation.invitationId(), StaffInvitationStatus.REVOKED,
                    now(), null, invitation.version());
            return true;
        } catch (StaffIdentityStateConflictException conflict) {
            return false;
        }
    }

    private boolean createUserIfEmailAvailable(String email) {
        try {
            createUser(email, "ACTIVE");
            return true;
        } catch (DataAccessException conflict) {
            return false;
        }
    }

    private static <T> List<T> runConcurrently(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<T> firstResult = executor.submit(() -> runAfterBarrier(first, ready, start));
            Future<T> secondResult = executor.submit(() -> runAfterBarrier(second, ready, start));
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent invitation test did not become ready.");
            }
            start.countDown();
            return List.of(
                    firstResult.get(30, TimeUnit.SECONDS),
                    secondResult.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static <T> T runAfterBarrier(
            Callable<T> task, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent invitation test start timed out.");
        }
        return task.call();
    }

    private StaffInvitationRecord createInvitation(
            UUID actorId,
            String email,
            RoleCode role,
            StaffScopeType scope,
            Set<UUID> branchIds,
            String rawToken) {
        return invitations.create(invitationDraft(
                actorId,
                email,
                role,
                scope,
                branchIds,
                tokenProtector.fingerprint(rawToken, StaffTokenPurpose.INVITATION),
                now()));
    }

    private UUID createAdditionalBranch() {
        UUID id = UUID.randomUUID();
        String code = "TEST_" + id.toString().replace("-", "").substring(0, 20).toUpperCase();
        jdbcTemplate.update("""
                insert into gym.gym_branches (
                    id, organization_id, code, name, country_code, timezone, status, version)
                values (?, ?, ?, 'Acceptance test branch', 'SV', 'America/El_Salvador', 'ACTIVE', 0)
                """, id, ORGANIZATION_ID, code);
        return id;
    }

    private void installFailureTrigger(String table, String trigger, String function) {
        FailureTriggerTarget target = switch (table) {
            case "users" -> new FailureTriggerTarget(
                    "gym.users", "BEFORE INSERT",
                    "TG_OP = 'INSERT' AND NEW.first_name = 'Rollback'");
            case "user_roles" -> new FailureTriggerTarget(
                    "gym.user_roles", "BEFORE INSERT",
                    "TG_OP = 'INSERT' AND EXISTS (SELECT 1 FROM gym.users u "
                            + "WHERE u.id = NEW.user_id AND u.first_name = 'Rollback')");
            case "staff_scopes" -> new FailureTriggerTarget(
                    "gym.staff_scopes", "BEFORE INSERT",
                    "TG_OP = 'INSERT' AND EXISTS (SELECT 1 FROM gym.users u "
                            + "WHERE u.id = NEW.user_id AND u.first_name = 'Rollback')");
            case "staff_branch_assignments" -> new FailureTriggerTarget(
                    "gym.staff_branch_assignments", "BEFORE INSERT",
                    "TG_OP = 'INSERT' AND EXISTS (SELECT 1 FROM gym.users u "
                            + "WHERE u.id = NEW.user_id AND u.first_name = 'Rollback')");
            case "staff_invitations" -> new FailureTriggerTarget(
                    "gym.staff_invitations", "BEFORE UPDATE",
                    "TG_OP = 'UPDATE' AND NEW.status = 'ACCEPTED' AND EXISTS "
                            + "(SELECT 1 FROM gym.users u WHERE u.id = NEW.accepted_user_id "
                            + "AND u.first_name = 'Rollback')");
            case "staff_account_activation_deliveries" -> new FailureTriggerTarget(
                    "gym.staff_account_activation_deliveries", "BEFORE INSERT",
                    "TG_OP = 'INSERT' AND EXISTS (SELECT 1 FROM gym.staff_invitations i "
                            + "JOIN gym.users u ON u.id = i.accepted_user_id "
                            + "WHERE i.id = NEW.invitation_id AND u.first_name = 'Rollback')");
            default -> throw new IllegalArgumentException("Unsupported test trigger table.");
        };
        jdbcTemplate.execute("""
                create or replace function gym.%s()
                returns trigger language plpgsql as $$
                begin
                    if %s then
                        raise exception 'injected test failure' using errcode = '23514';
                    end if;
                    return NEW;
                end;
                $$
                """.formatted(function, target.condition()));
        jdbcTemplate.execute("create trigger " + trigger + " " + target.operation()
                + " on " + target.table() + " for each row execute function gym." + function + "()");
    }

    private void dropFailureTrigger(String table, String trigger, String function) {
        String targetTable = switch (table) {
            case "users" -> "gym.users";
            case "user_roles" -> "gym.user_roles";
            case "staff_scopes" -> "gym.staff_scopes";
            case "staff_branch_assignments" -> "gym.staff_branch_assignments";
            case "staff_invitations" -> "gym.staff_invitations";
            case "staff_account_activation_deliveries" -> "gym.staff_account_activation_deliveries";
            default -> throw new IllegalArgumentException("Unsupported test trigger table.");
        };
        jdbcTemplate.execute("drop trigger if exists " + trigger + " on " + targetTable);
        jdbcTemplate.execute("drop function if exists gym." + function + "()");
    }

    private record FailureTriggerTarget(String table, String operation, String condition) {
    }

    private UUID createOrganizationAdministrator() {
        UUID userId = createUser("admin-" + UUID.randomUUID() + "@example.test", "ACTIVE");
        UUID roleId = jdbcTemplate.queryForObject(
                "select id from gym.roles where role_code = 'ADMIN'", UUID.class);
        jdbcTemplate.update("insert into gym.user_roles (user_id, role_id) values (?, ?)", userId, roleId);
        jdbcTemplate.update("""
                insert into gym.staff_scopes (user_id, scope_type, granted_at, version)
                values (?, 'ORGANIZATION', CURRENT_TIMESTAMP, 0)
                """, userId);
        return userId;
    }

    private UUID createUser(String email, String status) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString();
        jdbcTemplate.update("""
                insert into gym.users (
                    id, username, email, password_hash, first_name, last_name, status, version)
                values (?, ?, ?, 'test-hash-not-a-credential', 'Test', 'Staff', ?, 0)
                """, id, "test-" + suffix, email, status);
        return id;
    }

    private StaffInvitationDraft invitationDraft(
            UUID actorId,
            String email,
            RoleCode role,
            StaffScopeType scope,
            Set<UUID> branchIds,
            StaffTokenFingerprint fingerprint,
            Instant createdAt) {
        return new StaffInvitationDraft(
                UUID.randomUUID(), ORGANIZATION_ID, email, role, scope, branchIds, fingerprint,
                createdAt, createdAt, createdAt.plus(StaffInvitationPolicy.defaultLifetime(role)), actorId);
    }

    private void insertRawInvitation(
            UUID actorId,
            RoleCode role,
            StaffScopeType scope,
            String status,
            Instant createdAt,
            Instant expiresAt,
            long version) {
        jdbcTemplate.update("""
                insert into gym.staff_invitations (
                    id, organization_id, email_normalized, proposed_role, proposed_scope,
                    token_fingerprint, token_scheme, status, created_at, last_sent_at,
                    expires_at, invited_by_user_id, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), ORGANIZATION_ID,
                "raw-" + UUID.randomUUID() + "@example.test", role.name(), scope.name(),
                newFingerprint().value(), StaffTokenPurpose.INVITATION.schemeVersion(), status,
                java.time.OffsetDateTime.ofInstant(createdAt, java.time.ZoneOffset.UTC),
                java.time.OffsetDateTime.ofInstant(createdAt, java.time.ZoneOffset.UTC),
                java.time.OffsetDateTime.ofInstant(expiresAt, java.time.ZoneOffset.UTC),
                actorId, version);
    }

    private StaffTokenFingerprint newFingerprint() {
        return tokenProtector.fingerprint(tokenGenerator.generate(), StaffTokenPurpose.INVITATION);
    }

    private static Instant now() {
        return Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MICROS);
    }
}
