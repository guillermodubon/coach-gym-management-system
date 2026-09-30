package io.github.guillermodubon.coachgym.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditBranchVisibilityPolicyTest {

    private static final UUID BRANCH_A = UUID.fromString(
            "30000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_B = UUID.fromString(
            "30000000-0000-0000-0000-000000000002");
    private static final UUID BRANCH_C = UUID.fromString(
            "30000000-0000-0000-0000-000000000003");

    @Test
    void organizationAdministratorMayQueryAndExportOrganizationOrSelectedBranches() {
        StaffAuthorizationContext actor = actor(
                RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of(BRANCH_A, BRANCH_B));

        assertThat(AuditBranchVisibilityPolicy.allowsQuery(actor, true, Set.of())).isTrue();
        assertThat(AuditBranchVisibilityPolicy.allowsQuery(
                actor, false, Set.of(BRANCH_A, BRANCH_B))).isTrue();
        assertThat(AuditBranchVisibilityPolicy.allowsCsvExport(actor, true, Set.of())).isTrue();
        assertThat(AuditBranchVisibilityPolicy.allowsCsvExport(
                actor, false, Set.of(BRANCH_A))).isTrue();
    }

    @Test
    void branchAdministratorMayExportOnlyExplicitAssignedBranches() {
        StaffAuthorizationContext actor = actor(
                RoleCode.ADMIN, StaffScopeType.BRANCH, Set.of(BRANCH_A, BRANCH_B));

        assertThat(AuditBranchVisibilityPolicy.allowsQuery(actor, false, Set.of(BRANCH_A)))
                .isTrue();
        assertThat(AuditBranchVisibilityPolicy.allowsQuery(
                actor, false, Set.of(BRANCH_A, BRANCH_B))).isTrue();
        assertThat(AuditBranchVisibilityPolicy.allowsQuery(actor, true, Set.of())).isFalse();
        assertThat(AuditBranchVisibilityPolicy.allowsQuery(actor, false, Set.of())).isFalse();
        assertThat(AuditBranchVisibilityPolicy.allowsQuery(
                actor, false, Set.of(BRANCH_A, BRANCH_C))).isFalse();
        assertThat(AuditBranchVisibilityPolicy.allowsCsvExport(actor, true, Set.of())).isFalse();
        assertThat(AuditBranchVisibilityPolicy.allowsCsvExport(
                actor, false, Set.of(BRANCH_A, BRANCH_B))).isTrue();
        assertThat(AuditBranchVisibilityPolicy.allowsCsvExport(
                actor, false, Set.of(BRANCH_C))).isFalse();
    }

    @Test
    void globalOrUnattributedEntriesAndCsvRemainUnavailableToReceptionists() {
        StaffAuthorizationContext actor = actor(
                RoleCode.RECEPTIONIST, StaffScopeType.BRANCH, Set.of(BRANCH_A));

        assertThat(AuditBranchVisibilityPolicy.allowsQuery(actor, true, Set.of())).isFalse();
        assertThat(AuditBranchVisibilityPolicy.allowsQuery(actor, false, Set.of(BRANCH_A)))
                .isFalse();
        assertThat(AuditBranchVisibilityPolicy.allowsCsvExport(actor, true, Set.of())).isFalse();
        assertThat(AuditBranchVisibilityPolicy.allowsCsvExport(
                actor, false, Set.of(BRANCH_A))).isFalse();
        assertThat(AuditBranchVisibilityPolicy.allowsQuery(null, true, Set.of())).isFalse();
        assertThat(AuditBranchVisibilityPolicy.allowsQuery(
                actor, false, java.util.Collections.singleton(null))).isFalse();
    }

    private static StaffAuthorizationContext actor(
            RoleCode role,
            StaffScopeType scope,
            Set<UUID> branches) {
        return new StaffAuthorizationContext(
                UUID.randomUUID(), Set.of(role), StaffAccountStatus.ACTIVE, scope, branches);
    }
}
