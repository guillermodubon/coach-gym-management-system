package io.github.guillermodubon.coachgym.organization.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.organization.ChangeGymBranchStatusCommand;
import io.github.guillermodubon.coachgym.organization.CreateGymBranchCommand;
import io.github.guillermodubon.coachgym.organization.GymBranchDetails;
import io.github.guillermodubon.coachgym.organization.GymBranchStateConflictException;
import io.github.guillermodubon.coachgym.organization.GymBranchStatus;
import io.github.guillermodubon.coachgym.organization.GymBranchSummary;
import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import io.github.guillermodubon.coachgym.organization.UpdateGymBranchCommand;
import io.github.guillermodubon.coachgym.organization.UpdateOrganizationCommand;
import io.github.guillermodubon.coachgym.organization.application.GymBranchCodeConflictException;
import io.github.guillermodubon.coachgym.organization.application.GymBranchPage;
import io.github.guillermodubon.coachgym.organization.application.GymBranchQuery;
import io.github.guillermodubon.coachgym.organization.application.GymBranchSearchQuery;
import io.github.guillermodubon.coachgym.organization.application.GymBranchSortDirection;
import io.github.guillermodubon.coachgym.organization.application.GymBranchSortField;
import io.github.guillermodubon.coachgym.organization.application.GymBranchStore;
import io.github.guillermodubon.coachgym.organization.application.GymBranchVersionConflictException;
import io.github.guillermodubon.coachgym.organization.application.OrganizationQuery;
import io.github.guillermodubon.coachgym.organization.application.OrganizationStore;
import io.github.guillermodubon.coachgym.organization.application.OrganizationVersionConflictException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrganizationPersistenceIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000001");
    private static final UUID INITIAL_BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");
    private static final Instant FIRST_TIMESTAMP = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant SECOND_TIMESTAMP = Instant.parse("2026-01-02T10:00:00Z");

    @Autowired
    private OrganizationQuery organizationQuery;

    @Autowired
    private OrganizationStore organizationStore;

    @Autowired
    private GymBranchQuery branchQuery;

    @Autowired
    private GymBranchStore branchStore;

    @BeforeEach
    void resetOrganizationFixtures() {
        jdbcTemplate.execute("truncate table gym.gym_branches");
        jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, country_code,
                     timezone, status, is_initial_branch, version)
                values (?, ?, 'PRINCIPAL', 'Coach Gym Principal', 'SV',
                        'America/El_Salvador', 'ACTIVE', true, 0)
                """, INITIAL_BRANCH_ID, ORGANIZATION_ID);
        jdbcTemplate.update("""
                update gym.organizations
                set legal_name = 'Coach Gym',
                    brand_name = 'Coach Gym',
                    support_email = null,
                    support_phone = null,
                    default_timezone = 'America/El_Salvador',
                    default_currency = 'USD',
                    status = 'ACTIVE',
                    version = 0
                where id = ?
                """, ORGANIZATION_ID);
    }

    @Test
    void readsAndUpdatesCanonicalOrganizationWithImmutableCodeAndVersioning() {
        OrganizationDetails original = organizationQuery.findCanonical().orElseThrow();
        assertThat(original.id()).isEqualTo(ORGANIZATION_ID);
        assertThat(original.code()).isEqualTo("COACH_GYM");

        OrganizationDetails updated = organizationStore.update(
                new UpdateOrganizationCommand(
                        "Coach Gym Legal",
                        "Coach Gym Brand",
                        "support@example.test",
                        "+50370000000",
                        "America/Guatemala",
                        "USD",
                        original.version()),
                FIRST_TIMESTAMP);

        assertThat(updated.code()).isEqualTo("COACH_GYM");
        assertThat(updated.legalName()).isEqualTo("Coach Gym Legal");
        assertThat(updated.brandName()).isEqualTo("Coach Gym Brand");
        assertThat(updated.version()).isEqualTo(original.version() + 1);
        assertThatThrownBy(() -> organizationStore.update(
                new UpdateOrganizationCommand(
                        "Stale", "Stale", null, null,
                        "America/El_Salvador", "USD", original.version()),
                SECOND_TIMESTAMP))
                .isInstanceOf(OrganizationVersionConflictException.class);
    }

    @Test
    void createsFindsUpdatesAndProtectsTheInitialBranchMarker() {
        assertThat(branchQuery.findActiveById(INITIAL_BRANCH_ID))
                .get()
                .extracting(GymBranchSummary::code, GymBranchSummary::status)
                .containsExactly("PRINCIPAL", GymBranchStatus.ACTIVE);

        assertThatThrownBy(() -> branchStore.changeStatus(
                INITIAL_BRANCH_ID,
                new ChangeGymBranchStatusCommand(GymBranchStatus.INACTIVE, "No replacement", 0),
                SECOND_TIMESTAMP))
                .isInstanceOf(GymBranchStateConflictException.class);

        GymBranchDetails created = branchStore.create(
                command("NORTH", "North Gym", "North City"), FIRST_TIMESTAMP);

        assertThat(branchQuery.findById(created.id())).contains(created);
        assertThat(branchQuery.findByCode(" north ")).contains(created);
        assertThat(created.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(created.initialBranch()).isFalse();
        assertThat(created.version()).isZero();

        GymBranchDetails updated = branchStore.update(
                created.id(),
                updateCommand("North Gym Updated", "New City", created.version()),
                SECOND_TIMESTAMP);
        assertThat(updated.code()).isEqualTo("NORTH");
        assertThat(updated.name()).isEqualTo("North Gym Updated");
        assertThat(updated.version()).isEqualTo(1);

        assertThatThrownBy(() -> branchStore.update(
                created.id(), updateCommand("Stale", "New City", created.version()), SECOND_TIMESTAMP))
                .isInstanceOf(GymBranchVersionConflictException.class);
    }

    @Test
    void translatesDuplicateCodesAndSupportsStatusSearchEscapedSearchAndPagination() {
        GymBranchDetails north = branchStore.create(
                command("NORTH", "North  Gym", "North City"), FIRST_TIMESTAMP);
        branchStore.create(command("SOUTH", "South Gym", "South City"), FIRST_TIMESTAMP);
        branchStore.create(command("SOUTHWEST", "Southwest Gym", "South City"), FIRST_TIMESTAMP);

        assertThatThrownBy(() -> branchStore.create(
                command(" north ", "Duplicate", "Other"), SECOND_TIMESTAMP))
                .isInstanceOf(GymBranchCodeConflictException.class);

        GymBranchDetails inactive = branchStore.changeStatus(
                north.id(),
                new ChangeGymBranchStatusCommand(GymBranchStatus.INACTIVE, "Renovation", 0),
                SECOND_TIMESTAMP);
        assertThat(inactive.status()).isEqualTo(GymBranchStatus.INACTIVE);
        assertThat(branchQuery.findActiveById(north.id())).isEmpty();

        GymBranchPage filtered = branchQuery.findAll(new GymBranchSearchQuery(
                GymBranchStatus.INACTIVE,
                "  north   gym ",
                0,
                10,
                GymBranchSortField.NAME,
                GymBranchSortDirection.ASC));
        assertThat(filtered.items()).extracting(GymBranchDetails::code)
                .containsExactly("NORTH");

        GymBranchPage page = branchQuery.findAll(new GymBranchSearchQuery(
                null, null, 0, 2, GymBranchSortField.CODE, GymBranchSortDirection.ASC));
        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items()).extracting(GymBranchDetails::code)
                .containsExactly("NORTH", "PRINCIPAL");

        GymBranchPage secondPage = branchQuery.findAll(new GymBranchSearchQuery(
                null, null, 1, 2, GymBranchSortField.CODE, GymBranchSortDirection.ASC));
        assertThat(secondPage.items()).extracting(GymBranchDetails::code)
                .containsExactly("SOUTH", "SOUTHWEST");

        GymBranchPage emptyPage = branchQuery.findAll(new GymBranchSearchQuery(
                null, null, 2, 2, GymBranchSortField.CODE, GymBranchSortDirection.ASC));
        assertThat(emptyPage.items()).isEmpty();
        assertThat(emptyPage.totalElements()).isEqualTo(4);
    }

    private static CreateGymBranchCommand command(String code, String name, String city) {
        return new CreateGymBranchCommand(
                code,
                name,
                "Main Street 1",
                null,
                city,
                null,
                null,
                "SV",
                "+50370000000",
                "branch@example.test",
                "America/El_Salvador");
    }

    private static UpdateGymBranchCommand updateCommand(
            String name, String city, long version) {
        return new UpdateGymBranchCommand(
                name,
                "Main Street 2",
                null,
                city,
                null,
                null,
                "SV",
                "+50370000001",
                "updated@example.test",
                "America/El_Salvador",
                version);
    }
}
