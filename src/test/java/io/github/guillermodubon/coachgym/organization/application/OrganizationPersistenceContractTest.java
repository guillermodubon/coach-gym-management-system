package io.github.guillermodubon.coachgym.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.organization.GymBranchDetails;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationPersistenceContractTest {

    @Test
    void queriesAreBoundedNormalizedAndUseAllowlistedDefaults() {
        GymBranchSearchQuery query = new GymBranchSearchQuery(
                null,
                "  North   Gym  ",
                2,
                10,
                GymBranchSortField.NAME,
                GymBranchSortDirection.DESC);

        assertThat(query.search()).isEqualTo("North Gym");
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(10);

        GymBranchSearchQuery defaults = GymBranchSearchQuery.defaults();
        assertThat(defaults.size()).isEqualTo(GymBranchSearchQuery.DEFAULT_SIZE);
        assertThat(defaults.sortField()).isEqualTo(GymBranchSortField.CODE);
        assertThat(defaults.direction()).isEqualTo(GymBranchSortDirection.ASC);
    }

    @Test
    void queriesRejectUnboundedOrNegativePages() {
        assertThatThrownBy(() -> new GymBranchSearchQuery(
                null, null, -1, 10, GymBranchSortField.CODE, GymBranchSortDirection.ASC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GymBranchSearchQuery(
                null, null, 0, GymBranchSearchQuery.MAX_SIZE + 1,
                GymBranchSortField.CODE, GymBranchSortDirection.ASC))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageIsImmutableAndDoesNotExposeADeletePort() throws Exception {
        GymBranchDetails detail = new GymBranchDetails(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "NORTH",
                "North",
                null,
                null,
                null,
                null,
                null,
                "SV",
                null,
                null,
                "America/El_Salvador",
                io.github.guillermodubon.coachgym.organization.GymBranchStatus.ACTIVE,
                false,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                0);
        GymBranchPage page = new GymBranchPage(List.of(detail), 0, 10, 1, 1);

        assertThat(page.items()).containsExactly(detail);
        assertThatThrownBy(() -> page.items().add(detail))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(GymBranchStore.class.getMethods())
                .noneMatch(method -> method.getName().equals("delete"));
        assertThat(OrganizationStore.class.getMethods())
                .noneMatch(method -> method.getName().equals("delete"));
    }
}
