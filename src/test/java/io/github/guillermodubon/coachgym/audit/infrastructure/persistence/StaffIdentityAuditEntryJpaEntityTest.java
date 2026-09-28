package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.user.StaffIdentityLifecycleChanged;
import io.github.guillermodubon.coachgym.user.StaffIdentityStatus;
import io.github.guillermodubon.coachgym.user.StaffInvitationCreated;
import io.github.guillermodubon.coachgym.user.StaffInitialAdministratorProvisioned;
import io.github.guillermodubon.coachgym.user.StaffPasswordReset;
import io.github.guillermodubon.coachgym.user.StaffRoleScopeChanged;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffIdentityAuditEntryJpaEntityTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    @Test
    void invitationAuditStoresOnlyMaskedRecipientAndAllowlistedMetadata() {
        UUID invitationId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();

        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(new StaffInvitationCreated(
                invitationId, organizationId, actorId, "i***@example.test",
                RoleCode.RECEPTIONIST, StaffScopeType.BRANCH, Set.of(branchId), NOW));

        assertThat(entry.actionCode()).isEqualTo("STAFF_INVITATION_CREATED");
        assertThat(entry.resourceType()).isEqualTo("STAFF_INVITATION");
        assertThat(entry.resourceId()).isEqualTo(invitationId);
        assertThat(entry.metadata())
                .containsEntry("maskedRecipient", "i***@example.test")
                .containsEntry("proposedRole", "RECEPTIONIST")
                .containsEntry("proposedScope", "BRANCH")
                .containsEntry("newStatus", "PENDING")
                .containsKey("branchIds")
                .doesNotContainKey("email")
                .doesNotContainKey("token")
                .doesNotContainKey("password");
        assertThat(entry.metadata().toString()).doesNotContain("invitee@example.test");
    }

    @Test
    void accountPrivilegeRecoveryAndBootstrapEventsUseSeparateSafeActionFamilies() {
        UUID actor = UUID.randomUUID();
        UUID user = UUID.randomUUID();

        AuditEntryJpaEntity account = AuditEntryJpaEntity.from(
                new StaffIdentityLifecycleChanged(user, StaffIdentityStatus.ACTIVE,
                        StaffIdentityStatus.SUSPENDED, actor, NOW, true));
        AuditEntryJpaEntity roleScope = AuditEntryJpaEntity.from(
                new StaffRoleScopeChanged(user, Set.of(RoleCode.RECEPTIONIST),
                        Set.of(RoleCode.ADMIN), StaffScopeType.BRANCH,
                        StaffScopeType.BRANCH, actor, NOW, true));
        AuditEntryJpaEntity reset = AuditEntryJpaEntity.from(new StaffPasswordReset(user, NOW));
        AuditEntryJpaEntity bootstrap = AuditEntryJpaEntity.from(
                new StaffInitialAdministratorProvisioned(user, NOW));

        assertThat(account.actionCode()).isEqualTo("STAFF_ACCOUNT_SUSPENDED");
        assertThat(account.metadata()).containsEntry("reasonPresent", true);
        assertThat(roleScope.actionCode()).isEqualTo("STAFF_ROLE_SCOPE_CHANGED");
        assertThat(roleScope.metadata())
                .containsEntry("previousRoles", java.util.List.of("RECEPTIONIST"))
                .containsEntry("newRoles", java.util.List.of("ADMIN"));
        assertThat(reset.actionCode()).isEqualTo("STAFF_PASSWORD_RECOVERY_COMPLETED");
        assertThat(reset.metadata()).containsEntry("userIdPresent", true);
        assertThat(bootstrap.actionCode()).isEqualTo("INITIAL_ADMIN_BOOTSTRAPPED");
        assertThat(bootstrap.actorIdentifierSnapshot()).isEqualTo("system");
    }
}
