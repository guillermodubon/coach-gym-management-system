package io.github.guillermodubon.coachgym.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportingAccessPolicyTest {

    private static final UUID BRANCH_A = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_B = UUID.fromString(
            "20000000-0000-0000-0000-000000000002");
    private static final UUID BRANCH_C = UUID.fromString(
            "20000000-0000-0000-0000-000000000003");

    @Test
    void organizationAdministratorMayUseAllReportFamiliesAndSupportedScopes() {
        StaffAuthorizationContext actor = actor(
                RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                StaffAccountStatus.ACTIVE, Set.of(BRANCH_A, BRANCH_B));

        for (ReportingMetricGroup section : ReportingMetricGroup.values()) {
            assertThat(ReportingAccessPolicy.allows(
                    actor, section, BranchReportingSelection.organizationWide())).isTrue();
            assertThat(ReportingAccessPolicy.allows(
                    actor, section, BranchReportingSelection.singleBranch(BRANCH_A))).isTrue();
            assertThat(ReportingAccessPolicy.allows(
                    actor,
                    section,
                    BranchReportingSelection.authorizedBranches(List.of(BRANCH_A, BRANCH_B))))
                    .isTrue();
        }
    }

    @Test
    void branchAdministratorIsLimitedToAssignedBranchesAndCannotRequestOrganizationScope() {
        StaffAuthorizationContext actor = actor(
                RoleCode.ADMIN, StaffScopeType.BRANCH,
                StaffAccountStatus.ACTIVE, Set.of(BRANCH_A, BRANCH_B));

        for (ReportingMetricGroup section : ReportingMetricGroup.values()) {
            assertThat(ReportingAccessPolicy.allows(
                    actor, section, BranchReportingSelection.singleBranch(BRANCH_A))).isTrue();
            assertThat(ReportingAccessPolicy.allows(
                    actor,
                    section,
                    BranchReportingSelection.authorizedBranches(List.of(BRANCH_A, BRANCH_B))))
                    .isTrue();
            assertThat(ReportingAccessPolicy.allows(
                    actor,
                    section,
                    BranchReportingSelection.authorizedBranches(List.of(BRANCH_A, BRANCH_C))))
                    .isFalse();
            assertThat(ReportingAccessPolicy.allows(
                    actor, section, BranchReportingSelection.organizationWide())).isFalse();
        }
    }

    @Test
    void receptionistMayOnlySeeBaselineMembershipAndAccessAggregatesForOneAssignedBranch() {
        StaffAuthorizationContext actor = actor(
                RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                StaffAccountStatus.ACTIVE, Set.of(BRANCH_A, BRANCH_B));

        for (ReportingMetricGroup section : ReportingMetricGroup.values()) {
            boolean expected = section == ReportingMetricGroup.MEMBERSHIP_SUMMARY
                    || section == ReportingMetricGroup.ACCESS_SUMMARY;
            assertThat(ReportingAccessPolicy.allows(
                    actor, section, BranchReportingSelection.singleBranch(BRANCH_A)))
                    .as("section %s", section)
                    .isEqualTo(expected);
            assertThat(ReportingAccessPolicy.allows(
                    actor, section, BranchReportingSelection.activeBranch(BRANCH_A)))
                    .as("active branch section %s", section)
                    .isEqualTo(expected);
        }
        assertThat(ReportingAccessPolicy.allows(
                actor,
                ReportingMetricGroup.ACCESS_SUMMARY,
                BranchReportingSelection.authorizedBranches(List.of(BRANCH_A, BRANCH_B))))
                .isFalse();
        assertThat(ReportingAccessPolicy.allows(
                actor,
                ReportingMetricGroup.ACCESS_SUMMARY,
                BranchReportingSelection.singleBranch(BRANCH_C)))
                .isFalse();
        assertThat(ReportingAccessPolicy.allows(
                actor,
                ReportingMetricGroup.ACCESS_SUMMARY,
                BranchReportingSelection.organizationWide()))
                .isFalse();
    }

    @Test
    void deniesMissingAndInactiveActors() {
        StaffAuthorizationContext inactiveAdmin = actor(
                RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                StaffAccountStatus.SUSPENDED, Set.of());

        assertThat(ReportingAccessPolicy.allows(
                null, ReportingMetricGroup.ACCESS_SUMMARY,
                BranchReportingSelection.singleBranch(BRANCH_A)))
                .isFalse();
        assertThat(ReportingAccessPolicy.allows(
                inactiveAdmin,
                ReportingMetricGroup.FINANCIAL_SUMMARY,
                BranchReportingSelection.organizationWide()))
                .isFalse();
    }

    private static StaffAuthorizationContext actor(
            RoleCode role,
            StaffScopeType scope,
            StaffAccountStatus status,
            Set<UUID> branches) {
        return new StaffAuthorizationContext(
                UUID.randomUUID(), Set.of(role), status, scope, branches);
    }
}
