package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffInvitationPolicyTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-24T12:00:00Z");
    private static final UUID BRANCH_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_B = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void invitationLifecycleOnlyAllowsPendingToOneTerminalState() {
        assertThat(StaffInvitationStatus.values()).containsExactly(
                StaffInvitationStatus.PENDING,
                StaffInvitationStatus.ACCEPTED,
                StaffInvitationStatus.EXPIRED,
                StaffInvitationStatus.REVOKED);

        assertThat(StaffInvitationLifecyclePolicy.allowsTransition(
                StaffInvitationStatus.PENDING, StaffInvitationStatus.ACCEPTED)).isTrue();
        assertThat(StaffInvitationLifecyclePolicy.allowsTransition(
                StaffInvitationStatus.PENDING, StaffInvitationStatus.EXPIRED)).isTrue();
        assertThat(StaffInvitationLifecyclePolicy.allowsTransition(
                StaffInvitationStatus.PENDING, StaffInvitationStatus.REVOKED)).isTrue();

        for (StaffInvitationStatus terminal : List.of(
                StaffInvitationStatus.ACCEPTED,
                StaffInvitationStatus.EXPIRED,
                StaffInvitationStatus.REVOKED)) {
            assertThat(StaffInvitationStatus.values())
                    .as("terminal invitation state %s cannot transition", terminal)
                    .allSatisfy(next -> assertThat(
                            StaffInvitationLifecyclePolicy.allowsTransition(terminal, next)).isFalse());
        }
        assertThatThrownBy(() -> StaffInvitationLifecyclePolicy.requireTransition(
                StaffInvitationStatus.ACCEPTED, StaffInvitationStatus.PENDING))
                .isInstanceOf(StaffIdentityStateConflictException.class);
    }

    @Test
    void onlyOrganizationAdministratorsMayInviteTheApprovedRoleScopeMatrix() {
        StaffAuthorizationContext organizationAdmin = context(
                Set.of(RoleCode.ADMIN), StaffScopeType.ORGANIZATION, Set.of());
        StaffAuthorizationContext branchAdmin = context(
                Set.of(RoleCode.ADMIN), StaffScopeType.BRANCH, Set.of(BRANCH_A));
        StaffAuthorizationContext receptionist = context(
                Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH, Set.of(BRANCH_A));

        StaffInvitationPolicy.requireCanInvite(
                organizationAdmin, RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                Set.of(), Set.of(BRANCH_A, BRANCH_B), true);
        StaffInvitationPolicy.requireCanInvite(
                organizationAdmin, RoleCode.ADMIN, StaffScopeType.BRANCH,
                Set.of(BRANCH_A), Set.of(BRANCH_A), true);
        StaffInvitationPolicy.requireCanInvite(
                organizationAdmin, RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                Set.of(BRANCH_A, BRANCH_B), Set.of(BRANCH_A, BRANCH_B), false);

        assertThatThrownBy(() -> StaffInvitationPolicy.requireCanInvite(
                branchAdmin, RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                Set.of(BRANCH_A), Set.of(BRANCH_A), false))
                .isInstanceOf(StaffIdentityAuthorizationException.class);
        assertThatThrownBy(() -> StaffInvitationPolicy.requireCanInvite(
                receptionist, RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                Set.of(BRANCH_A), Set.of(BRANCH_A), false))
                .isInstanceOf(StaffIdentityAuthorizationException.class);
        assertThatThrownBy(() -> StaffInvitationPolicy.requireCanInvite(
                organizationAdmin, RoleCode.RECEPTIONIST, StaffScopeType.ORGANIZATION,
                Set.of(), Set.of(), false))
                .isInstanceOf(StaffScopeValidationException.class);
    }

    @Test
    void approvedRoleScopeCombinationsAndBranchAssignmentRequirementsAreEnforced() {
        assertThat(StaffScopeAuthorizationPolicy.isCompatible(
                Set.of(RoleCode.ADMIN), StaffScopeType.ORGANIZATION)).isTrue();
        assertThat(StaffScopeAuthorizationPolicy.isCompatible(
                Set.of(RoleCode.ADMIN), StaffScopeType.BRANCH)).isTrue();
        assertThat(StaffScopeAuthorizationPolicy.isCompatible(
                Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH)).isTrue();
        assertThat(StaffScopeAuthorizationPolicy.isCompatible(
                Set.of(RoleCode.RECEPTIONIST), StaffScopeType.ORGANIZATION)).isFalse();

        assertThatThrownBy(() -> StaffInvitationPolicy.requireValidProposal(
                RoleCode.RECEPTIONIST, StaffScopeType.BRANCH, Set.of()))
                .isInstanceOf(StaffIdentityValidationException.class);
        assertThatThrownBy(() -> StaffInvitationPolicy.requireValidProposal(
                RoleCode.ADMIN, StaffScopeType.BRANCH, Set.of()))
                .isInstanceOf(StaffIdentityValidationException.class);
        assertThatThrownBy(() -> StaffInvitationPolicy.requireValidProposal(
                RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of(BRANCH_A)))
                .isInstanceOf(StaffIdentityValidationException.class);
        assertThatThrownBy(() -> StaffInvitationPolicy.requireAllBranchesActive(
                Set.of(BRANCH_A, BRANCH_B), Set.of(BRANCH_A)))
                .isInstanceOf(StaffIdentityStateConflictException.class);
    }

    @Test
    void administratorInvitationsRequireCurrentPasswordReauthentication() {
        assertThat(StaffInvitationPolicy.requiresReauthentication(RoleCode.ADMIN)).isTrue();
        assertThat(StaffInvitationPolicy.requiresReauthentication(RoleCode.RECEPTIONIST)).isFalse();

        assertThatThrownBy(() -> StaffInvitationPolicy.requireCanInvite(
                context(Set.of(RoleCode.ADMIN), StaffScopeType.ORGANIZATION, Set.of()),
                RoleCode.ADMIN,
                StaffScopeType.ORGANIZATION,
                Set.of(),
                Set.of(BRANCH_A),
                false))
                .isInstanceOf(StaffIdentityAuthorizationException.class)
                .hasMessageContaining("reauthentication");
    }

    @Test
    void branchSetsAreDeduplicatedSortedAndImmutable() {
        Set<UUID> branches = StaffInvitationPolicy.normalizeBranchIds(
                List.of(BRANCH_B, BRANCH_A, BRANCH_B));

        assertThat(branches).containsExactly(BRANCH_A, BRANCH_B);
        assertThat(branches).isUnmodifiable();
        assertThatThrownBy(() -> StaffInvitationPolicy.normalizeBranchIds(
                Arrays.asList(BRANCH_A, null)))
                .isInstanceOf(StaffIdentityValidationException.class);
        assertThatThrownBy(() -> StaffInvitationPolicy.normalizeBranchIds(
                java.util.stream.IntStream.range(0, StaffInvitationPolicy.MAX_INVITATION_BRANCHES + 1)
                        .mapToObj(ignored -> UUID.randomUUID())
                        .toList()))
                .isInstanceOf(StaffIdentityValidationException.class);
    }

    @Test
    void invitationExpirationAndResendAreBoundedByServerPolicy() {
        assertThat(StaffInvitationPolicy.defaultLifetime(RoleCode.ADMIN))
                .isEqualTo(Duration.ofHours(24));
        assertThat(StaffInvitationPolicy.defaultLifetime(RoleCode.RECEPTIONIST))
                .isEqualTo(Duration.ofHours(48));
        assertThat(StaffInvitationPolicy.requireExpiration(
                RoleCode.ADMIN, CREATED_AT, CREATED_AT.plus(Duration.ofHours(24))))
                .isEqualTo(CREATED_AT.plus(Duration.ofHours(24)));

        assertThatThrownBy(() -> StaffInvitationPolicy.requireExpiration(
                RoleCode.ADMIN, CREATED_AT, CREATED_AT))
                .isInstanceOf(StaffIdentityValidationException.class);
        assertThatThrownBy(() -> StaffInvitationPolicy.requireExpiration(
                RoleCode.RECEPTIONIST, CREATED_AT, CREATED_AT.plus(Duration.ofHours(48)).plusSeconds(1)))
                .isInstanceOf(StaffIdentityValidationException.class);
        assertThatThrownBy(() -> StaffInvitationPolicy.requireAcceptable(
                StaffInvitationStatus.PENDING, CREATED_AT.plus(Duration.ofHours(24)),
                CREATED_AT.plus(Duration.ofHours(24))))
                .isInstanceOf(StaffIdentityStateConflictException.class);

        StaffInvitationPolicy.requireResendAllowed(
                StaffInvitationStatus.PENDING, CREATED_AT,
                CREATED_AT.plus(StaffInvitationPolicy.RESEND_COOLDOWN));
        assertThatThrownBy(() -> StaffInvitationPolicy.requireResendAllowed(
                StaffInvitationStatus.PENDING, CREATED_AT,
                CREATED_AT.plus(StaffInvitationPolicy.RESEND_COOLDOWN).minusNanos(1)))
                .isInstanceOf(StaffIdentityStateConflictException.class);
        assertThatThrownBy(() -> StaffInvitationPolicy.requireResendAllowed(
                StaffInvitationStatus.REVOKED, CREATED_AT,
                CREATED_AT.plus(StaffInvitationPolicy.RESEND_COOLDOWN)))
                .isInstanceOf(StaffIdentityStateConflictException.class);
    }

    private static StaffAuthorizationContext context(
            Set<RoleCode> roles,
            StaffScopeType scope,
            Set<UUID> branches) {
        return new StaffAuthorizationContext(
                ACTOR_ID, roles, StaffAccountStatus.ACTIVE, scope, branches);
    }
}
