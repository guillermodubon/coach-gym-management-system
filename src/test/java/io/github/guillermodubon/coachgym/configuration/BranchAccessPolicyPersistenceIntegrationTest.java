package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.configuration.application.BranchAccessPolicyApplicationService;
import io.github.guillermodubon.coachgym.configuration.application.BranchAccessPolicyNotFoundException;
import io.github.guillermodubon.coachgym.configuration.application.BranchAccessPolicyStore;
import io.github.guillermodubon.coachgym.configuration.application.BranchAccessPolicyVersionConflictException;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

class BranchAccessPolicyPersistenceIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    private static final UUID INITIAL_BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");

    @Autowired private BranchAccessPolicyQuery policyQuery;
    @Autowired private BranchAccessPolicyStore policyStore;
    @Autowired private BranchAccessPolicyApplicationService policyService;

    private UUID organizationId;
    private UUID inactiveBranchId;

    @BeforeEach
    void resetBranchPolicyFixtures() {
        organizationId = jdbcTemplate.queryForObject("""
                select id from gym.organizations
                where is_canonical and status = 'ACTIVE'
                """, UUID.class);
        inactiveBranchId = UUID.randomUUID();
        String inactiveBranchCode = "INACTIVE_POLICY_"
                + inactiveBranchId.toString().substring(0, 8).replace("-", "").toUpperCase();
        jdbcTemplate.update("""
                update gym.gym_settings
                set require_confirmed_payment_for_access = false,
                    updated_by_user_id = null,
                    version = 0
                where id = 1
                """);
        jdbcTemplate.update("""
                update gym.branch_access_policy_overrides
                set policy_mode = 'INHERIT', updated_by_user_id = null, version = 0
                where branch_id = ?
                """, INITIAL_BRANCH_ID);
        jdbcTemplate.update("""
                delete from gym.audit_entries
                where action_code = 'BRANCH_ACCESS_PAYMENT_POLICY_CHANGED'
                """);
        jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, version)
                values (?, ?, ?, 'Inactive Policy Branch',
                        'America/El_Salvador', 'INACTIVE', 0)
                """, inactiveBranchId, organizationId, inactiveBranchCode);
    }

    @Test
    void resolvesOrganizationDefaultAndBranchOverrideThenClearsBackToInheritance() {
        assertThat(policyQuery.findForBranch(organizationId, INITIAL_BRANCH_ID))
                .satisfies(policy -> {
                    assertThat(policy.organizationDefaultRequiresConfirmedPayment())
                            .isFalse();
                    assertThat(policy.branchMode())
                            .isEqualTo(BranchAccessPaymentPolicyMode.INHERIT);
                    assertThat(policy.requireConfirmedPaymentForAccess()).isFalse();
                    assertThat(policy.version()).isZero();
                });

        jdbcTemplate.update("""
                update gym.gym_settings
                set require_confirmed_payment_for_access = true
                where id = 1
                """);
        assertThat(policyQuery.findForBranch(organizationId, INITIAL_BRANCH_ID)
                .requireConfirmedPaymentForAccess()).isTrue();

        EffectiveBranchAccessPolicy required = policyStore.update(
                organizationId,
                INITIAL_BRANCH_ID,
                BranchAccessPaymentPolicyMode.REQUIRED,
                0,
                adminId,
                java.time.Instant.parse("2026-09-23T12:00:00Z"));
        assertThat(required.branchMode()).isEqualTo(BranchAccessPaymentPolicyMode.REQUIRED);
        assertThat(required.version()).isEqualTo(1);
        assertThat(required.requireConfirmedPaymentForAccess()).isTrue();

        EffectiveBranchAccessPolicy notRequired = policyStore.update(
                organizationId,
                INITIAL_BRANCH_ID,
                BranchAccessPaymentPolicyMode.NOT_REQUIRED,
                1,
                adminId,
                java.time.Instant.parse("2026-09-23T12:01:00Z"));
        assertThat(notRequired.version()).isEqualTo(2);
        assertThat(notRequired.requireConfirmedPaymentForAccess()).isFalse();

        EffectiveBranchAccessPolicy inherited = policyStore.update(
                organizationId,
                INITIAL_BRANCH_ID,
                BranchAccessPaymentPolicyMode.INHERIT,
                2,
                adminId,
                java.time.Instant.parse("2026-09-23T12:02:00Z"));
        assertThat(inherited.branchMode()).isEqualTo(BranchAccessPaymentPolicyMode.INHERIT);
        assertThat(inherited.version()).isEqualTo(3);
        assertThat(inherited.requireConfirmedPaymentForAccess()).isTrue();
    }

    @Test
    void missingOverrideInheritsAtVersionZeroAndFirstWriteCreatesVersionOne() {
        jdbcTemplate.update("""
                delete from gym.branch_access_policy_overrides where branch_id = ?
                """, INITIAL_BRANCH_ID);

        EffectiveBranchAccessPolicy inherited = policyQuery.findForBranch(
                organizationId, INITIAL_BRANCH_ID);
        assertThat(inherited.branchMode()).isEqualTo(BranchAccessPaymentPolicyMode.INHERIT);
        assertThat(inherited.version()).isZero();

        EffectiveBranchAccessPolicy created = policyStore.update(
                organizationId,
                INITIAL_BRANCH_ID,
                BranchAccessPaymentPolicyMode.REQUIRED,
                0,
                adminId,
                java.time.Instant.parse("2026-09-23T12:00:00Z"));
        assertThat(created.branchMode()).isEqualTo(BranchAccessPaymentPolicyMode.REQUIRED);
        assertThat(created.version()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.branch_access_policy_overrides
                where branch_id = ?
                """, Integer.class, INITIAL_BRANCH_ID)).isEqualTo(1);
    }

    @Test
    void staleUpdatesAndInactiveBranchesAreNotTreatedAsOrdinaryDenials() {
        policyStore.update(
                organizationId,
                INITIAL_BRANCH_ID,
                BranchAccessPaymentPolicyMode.REQUIRED,
                0,
                adminId,
                java.time.Instant.parse("2026-09-23T12:00:00Z"));

        assertThatThrownBy(() -> policyStore.update(
                organizationId,
                INITIAL_BRANCH_ID,
                BranchAccessPaymentPolicyMode.NOT_REQUIRED,
                0,
                adminId,
                java.time.Instant.parse("2026-09-23T12:01:00Z")))
                .isInstanceOf(BranchAccessPolicyVersionConflictException.class);
        assertThatThrownBy(() -> policyQuery.findForBranch(
                organizationId, inactiveBranchId))
                .isInstanceOf(BranchAccessPolicyNotFoundException.class);
        assertThatThrownBy(() -> policyStore.update(
                organizationId,
                inactiveBranchId,
                BranchAccessPaymentPolicyMode.REQUIRED,
                0,
                adminId,
                java.time.Instant.parse("2026-09-23T12:01:00Z")))
                .isInstanceOf(BranchAccessPolicyNotFoundException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void organizationAdministratorChangeIsPersistedAsSafeAuditMetadata() {
        EffectiveBranchAccessPolicy updated = policyService.updateOverride(
                new UpdateBranchAccessPolicyCommand(
                        INITIAL_BRANCH_ID,
                        BranchAccessPaymentPolicyMode.REQUIRED,
                        0),
                new AccessPaymentPolicyActor(adminId, ADMIN_USERNAME));

        assertThat(updated.version()).isEqualTo(1);
        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                select action_code, resource_type, resource_id, actor_user_id,
                       metadata ->> 'previousMode' as previous_mode,
                       metadata ->> 'newMode' as new_mode,
                       metadata ->> 'version' as policy_version,
                       metadata::text as metadata
                from gym.audit_entries
                where action_code = 'BRANCH_ACCESS_PAYMENT_POLICY_CHANGED'
                """);
        assertThat(audit)
                .containsEntry("action_code", "BRANCH_ACCESS_PAYMENT_POLICY_CHANGED")
                .containsEntry("resource_type", "GYM_BRANCH")
                .containsEntry("resource_id", INITIAL_BRANCH_ID)
                .containsEntry("actor_user_id", adminId)
                .containsEntry("previous_mode", "INHERIT")
                .containsEntry("new_mode", "REQUIRED")
                .containsEntry("policy_version", "1");
        assertThat(audit.get("metadata").toString())
                .doesNotContain("payment details", "stripe", "token", "card", "secret");
    }
}
