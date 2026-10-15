package io.github.guillermodubon.coachgym.organization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.user.RoleCode;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OrganizationBoundaryContractTest {

    @Test
    void publicContractsRemainFrameworkFreeAndRolesRemainUnchanged() {
        assertThat(Arrays.stream(OrganizationDetails.class.getRecordComponents())
                .map(RecordComponent::getType)
                .map(Class::getName)
                .noneMatch(name -> name.startsWith("org.springframework")
                        || name.startsWith("jakarta.persistence")))
                .isTrue();
        assertThat(Set.of(RoleCode.values())).containsExactlyInAnyOrder(
                RoleCode.ADMIN, RoleCode.RECEPTIONIST);
        assertThat(Set.of(RoleCode.values()).stream().map(Enum::name))
                .doesNotContain("MAINTENANCE");
    }

    @Test
    void summariesExposeOnlyStableAssignmentSafeFields() {
        Set<String> organizationFields = Arrays.stream(
                        OrganizationSummary.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        Set<String> branchFields = Arrays.stream(
                        GymBranchSummary.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(organizationFields).containsExactlyInAnyOrder(
                "id", "code", "brandName", "defaultTimezone", "defaultCurrency", "status");
        assertThat(branchFields).containsExactlyInAnyOrder(
                "id", "organizationId", "code", "name", "timezone", "status", "initialBranch");
    }
}
