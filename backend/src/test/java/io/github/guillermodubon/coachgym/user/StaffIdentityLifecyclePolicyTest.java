package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffIdentityLifecyclePolicyTest {

    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Test
    void accountLifecycleAllowsOnlyAcceptanceSuspensionReactivationAndDeactivation() {
        assertThat(StaffIdentityStatus.values()).containsExactly(
                StaffIdentityStatus.INVITED,
                StaffIdentityStatus.ACTIVE,
                StaffIdentityStatus.SUSPENDED,
                StaffIdentityStatus.DEACTIVATED);

        assertThat(StaffIdentityLifecyclePolicy.allowsTransition(
                StaffIdentityStatus.INVITED, StaffIdentityStatus.ACTIVE)).isTrue();
        assertThat(StaffIdentityLifecyclePolicy.allowsTransition(
                StaffIdentityStatus.ACTIVE, StaffIdentityStatus.SUSPENDED)).isTrue();
        assertThat(StaffIdentityLifecyclePolicy.allowsTransition(
                StaffIdentityStatus.ACTIVE, StaffIdentityStatus.DEACTIVATED)).isTrue();
        assertThat(StaffIdentityLifecyclePolicy.allowsTransition(
                StaffIdentityStatus.SUSPENDED, StaffIdentityStatus.ACTIVE)).isTrue();
        assertThat(StaffIdentityLifecyclePolicy.allowsTransition(
                StaffIdentityStatus.SUSPENDED, StaffIdentityStatus.DEACTIVATED)).isTrue();
        assertThat(StaffIdentityLifecyclePolicy.allowsTransition(
                StaffIdentityStatus.DEACTIVATED, StaffIdentityStatus.ACTIVE)).isFalse();
        assertThat(StaffIdentityLifecyclePolicy.allowsTransition(
                StaffIdentityStatus.ACTIVE, StaffIdentityStatus.INVITED)).isFalse();
        assertThatThrownBy(() -> StaffIdentityLifecyclePolicy.requireTransition(
                StaffIdentityStatus.DEACTIVATED, StaffIdentityStatus.ACTIVE))
                .isInstanceOf(StaffIdentityStateConflictException.class);
    }

    @Test
    void administrativeIdentityChangesCannotTargetTheActor() {
        StaffIdentityLifecyclePolicy.requireDifferentActorAndTarget(ACTOR_ID, TARGET_ID);
        assertThatThrownBy(() -> StaffIdentityLifecyclePolicy.requireDifferentActorAndTarget(
                ACTOR_ID, ACTOR_ID))
                .isInstanceOf(StaffIdentityAuthorizationException.class);
        assertThatThrownBy(() -> StaffIdentityLifecyclePolicy.requireDifferentActorAndTarget(
                null, TARGET_ID))
                .isInstanceOf(StaffIdentityValidationException.class);
    }

    @Test
    void privilegedChangesAndOrganizationAdministratorSuspensionRequireReauthentication() {
        assertThat(StaffIdentityLifecyclePolicy.requiresReauthentication(
                StaffIdentityStatus.ACTIVE, false, true)).isTrue();
        assertThat(StaffIdentityLifecyclePolicy.requiresReauthentication(
                StaffIdentityStatus.SUSPENDED, true, false)).isTrue();
        assertThat(StaffIdentityLifecyclePolicy.requiresReauthentication(
                StaffIdentityStatus.DEACTIVATED, true, false)).isTrue();
        assertThat(StaffIdentityLifecyclePolicy.requiresReauthentication(
                StaffIdentityStatus.SUSPENDED, false, false)).isFalse();
    }

    @Test
    void lastActiveOrganizationAdministratorCannotLoseAuthority() {
        LastOrganizationAdministratorPolicy.requireAdministratorRemains(true, false, 2);
        LastOrganizationAdministratorPolicy.requireAdministratorRemains(true, true, 1);
        LastOrganizationAdministratorPolicy.requireAdministratorRemains(false, false, 0);

        assertThatThrownBy(() -> LastOrganizationAdministratorPolicy.requireAdministratorRemains(
                true, false, 1))
                .isInstanceOf(StaffIdentityStateConflictException.class);
        assertThatThrownBy(() -> LastOrganizationAdministratorPolicy.requireAdministratorRemains(
                true, false, -1))
                .isInstanceOf(StaffIdentityValidationException.class);
    }
}
