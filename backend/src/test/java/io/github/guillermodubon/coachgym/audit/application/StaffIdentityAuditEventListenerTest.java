package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.user.StaffIdentityLifecycleChanged;
import io.github.guillermodubon.coachgym.user.StaffInitialAdministratorProvisioned;
import io.github.guillermodubon.coachgym.user.StaffInvitationAccepted;
import io.github.guillermodubon.coachgym.user.StaffInvitationCreated;
import io.github.guillermodubon.coachgym.user.StaffInvitationResent;
import io.github.guillermodubon.coachgym.user.StaffInvitationRevoked;
import io.github.guillermodubon.coachgym.user.StaffIdentityStatus;
import io.github.guillermodubon.coachgym.user.StaffPasswordReset;
import io.github.guillermodubon.coachgym.user.StaffRoleScopeChanged;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffIdentityAuditEventListenerTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    @Test
    void forwardsEveryIdentityLifecycleFactToTheAuditPort() {
        AuditEntryStore store = mock(AuditEntryStore.class);
        StaffIdentityAuditEventListener listener = new StaffIdentityAuditEventListener(store);
        UUID actor = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID invitation = UUID.randomUUID();
        UUID organization = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        StaffInvitationCreated created = new StaffInvitationCreated(
                invitation, organization, actor, "i***@example.test",
                RoleCode.RECEPTIONIST, StaffScopeType.BRANCH, Set.of(branch), NOW);
        StaffInvitationResent resent = new StaffInvitationResent(
                invitation, organization, actor, 1, NOW);
        StaffInvitationRevoked revoked = new StaffInvitationRevoked(
                invitation, organization, actor, 2, NOW);
        StaffInvitationAccepted accepted = new StaffInvitationAccepted(
                invitation, organization, user, actor, RoleCode.RECEPTIONIST,
                StaffScopeType.BRANCH, Set.of(branch), NOW);
        StaffIdentityLifecycleChanged lifecycle = new StaffIdentityLifecycleChanged(
                user, StaffIdentityStatus.ACTIVE, StaffIdentityStatus.SUSPENDED,
                actor, NOW, true);
        StaffRoleScopeChanged authority = new StaffRoleScopeChanged(
                user, Set.of(RoleCode.RECEPTIONIST), Set.of(RoleCode.ADMIN),
                StaffScopeType.BRANCH, StaffScopeType.BRANCH, actor, NOW, true);
        StaffPasswordReset reset = new StaffPasswordReset(user, NOW);
        StaffInitialAdministratorProvisioned bootstrap =
                new StaffInitialAdministratorProvisioned(user, NOW);

        listener.record(created);
        listener.record(resent);
        listener.record(revoked);
        listener.record(accepted);
        listener.record(lifecycle);
        listener.record(authority);
        listener.record(reset);
        listener.record(bootstrap);

        verify(store).recordStaffInvitationCreated(created);
        verify(store).recordStaffInvitationResent(resent);
        verify(store).recordStaffInvitationRevoked(revoked);
        verify(store).recordStaffInvitationAccepted(accepted);
        verify(store).recordStaffIdentityLifecycleChanged(lifecycle);
        verify(store).recordStaffRoleScopeChanged(authority);
        verify(store).recordStaffPasswordReset(reset);
        verify(store).recordInitialAdministratorProvisioned(bootstrap);
    }
}
