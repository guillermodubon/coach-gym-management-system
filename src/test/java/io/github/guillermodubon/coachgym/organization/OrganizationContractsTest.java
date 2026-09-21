package io.github.guillermodubon.coachgym.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationContractsTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Test
    void organizationDetailsNormalizeHumanTextAndCodes() {
        OrganizationDetails details = new OrganizationDetails(
                ORGANIZATION_ID,
                " coach_gym ",
                "  Coach   Gym, S.A. ",
                " Coach   Gym ",
                " SUPPORT@COACH-GYM.EXAMPLE ",
                " +503 7000-0000 ",
                "America/El_Salvador",
                "usd",
                OrganizationStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                0);

        assertThat(details.code()).isEqualTo("COACH_GYM");
        assertThat(details.legalName()).isEqualTo("Coach Gym, S.A.");
        assertThat(details.brandName()).isEqualTo("Coach Gym");
        assertThat(details.supportEmail()).isEqualTo("support@coach-gym.example");
        assertThat(details.defaultCurrency()).isEqualTo("USD");
        assertThat(OrganizationSummary.from(details).brandName()).isEqualTo("Coach Gym");
    }

    @Test
    void organizationRejectsInvalidCodeTimezoneCurrencyAndVersion() {
        assertThatThrownBy(() -> new OrganizationDetails(
                ORGANIZATION_ID, "COACH GYM", "Coach Gym", "Coach Gym", null, null,
                "America/El_Salvador", "USD", OrganizationStatus.ACTIVE,
                Instant.EPOCH, Instant.EPOCH, 0))
                .isInstanceOf(OrganizationValidationException.class);

        assertThatThrownBy(() -> new OrganizationDetails(
                ORGANIZATION_ID, "COACH_GYM", "Coach Gym", "Coach Gym", null, null,
                "Not/ATimezone", "USD", OrganizationStatus.ACTIVE,
                Instant.EPOCH, Instant.EPOCH, 0))
                .isInstanceOf(OrganizationValidationException.class);

        assertThatThrownBy(() -> new OrganizationDetails(
                ORGANIZATION_ID, "COACH_GYM", "Coach Gym", "Coach Gym", null, null,
                "America/El_Salvador", "ZZZ", OrganizationStatus.ACTIVE,
                Instant.EPOCH, Instant.EPOCH, 0))
                .isInstanceOf(OrganizationValidationException.class);

        assertThatThrownBy(() -> new UpdateOrganizationCommand(
                "Coach Gym", "Coach Gym", null, null,
                "America/El_Salvador", "USD", -1))
                .isInstanceOf(OrganizationValidationException.class);
    }

    @Test
    void branchCommandsNormalizeOptionalContactAndInheritTimezoneLater() {
        CreateGymBranchCommand command = new CreateGymBranchCommand(
                " principal ",
                " Coach   Gym Principal ",
                " Avenida Central 1 ",
                " ",
                " San Salvador ",
                null,
                " CP01 ",
                "sv",
                "+503 7000-0000",
                "BRANCH@COACH-GYM.EXAMPLE",
                null);

        assertThat(command.code()).isEqualTo("PRINCIPAL");
        assertThat(command.name()).isEqualTo("Coach Gym Principal");
        assertThat(command.addressLine2()).isNull();
        assertThat(command.countryCode()).isEqualTo("SV");
        assertThat(command.email()).isEqualTo("branch@coach-gym.example");
        assertThat(command.timezone()).isNull();
    }

    @Test
    void branchStatusCommandRequiresReasonAndNonNegativeVersion() {
        ChangeGymBranchStatusCommand command = new ChangeGymBranchStatusCommand(
                GymBranchStatus.INACTIVE,
                "Temporary closure",
                3);

        assertThat(command.requestedStatus()).isEqualTo(GymBranchStatus.INACTIVE);
        assertThat(command.reason()).isEqualTo("Temporary closure");

        assertThatThrownBy(() -> new ChangeGymBranchStatusCommand(
                GymBranchStatus.INACTIVE, " ", 3))
                .isInstanceOf(OrganizationValidationException.class);
        assertThatThrownBy(() -> new ChangeGymBranchStatusCommand(
                GymBranchStatus.INACTIVE, "Temporary closure", -1))
                .isInstanceOf(OrganizationValidationException.class);
    }

    @Test
    void publicCommandsDoNotAcceptTenantAssignmentOrServerOwnedFields() {
        Set<String> forbidden = Set.of(
                "tenantId", "staffAssignmentId", "organizationId", "initialBranch",
                "actorId", "occurredAt", "status", "version", "code");

        assertThat(recordComponentNames(UpdateOrganizationCommand.class))
                .doesNotContainAnyElementsOf(forbidden);
        assertThat(recordComponentNames(CreateGymBranchCommand.class))
                .doesNotContainAnyElementsOf(Set.of(
                        "tenantId", "organizationId", "initialBranch", "actorId", "occurredAt", "status"));
        assertThat(recordComponentNames(UpdateGymBranchCommand.class))
                .doesNotContainAnyElementsOf(Set.of(
                        "tenantId", "organizationId", "initialBranch", "actorId", "occurredAt", "status", "code"));
    }

    private static Set<String> recordComponentNames(Class<?> type) {
        return java.util.Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
