package io.github.guillermodubon.coachgym.organization.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.organization.GymBranchReportingUnavailableException;
import io.github.guillermodubon.coachgym.organization.GymBranchStatus;
import io.github.guillermodubon.coachgym.organization.GymBranchSummary;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class JdbcGymBranchReportingQueryTest {

    private static final UUID BRANCH_A = UUID.fromString(
            "40000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_B = UUID.fromString(
            "40000000-0000-0000-0000-000000000002");
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000010");

    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void activeBranchResolutionIsCanonicalParameterizedAndSelectsOnlySafeMetadata() {
        GymBranchSummary branch = branch(BRANCH_A, "NORTH", GymBranchStatus.ACTIVE);
        when(jdbcTemplate.query(
                eq(JdbcGymBranchPersistenceAdapter.REPORTING_BRANCHES_ACTIVE_SQL),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<GymBranchSummary>>any()))
                .thenReturn(List.of(branch));

        assertThat(new JdbcGymBranchPersistenceAdapter(jdbcTemplate)
                .findCanonicalBranches(List.of(BRANCH_A), false))
                .containsExactly(branch);

        String sql = JdbcGymBranchPersistenceAdapter.REPORTING_BRANCHES_ACTIVE_SQL
                .toLowerCase(java.util.Locale.ROOT);
        assertThat(sql)
                .contains("o.is_canonical = true", "o.status = 'active'", "b.status = 'active'",
                        "b.id in (:branchids)", "b.timezone", "b.is_initial_branch")
                .doesNotContain("b.email", "b.phone", "address_line", "support_email");
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(
                eq(JdbcGymBranchPersistenceAdapter.REPORTING_BRANCHES_ACTIVE_SQL),
                parameters.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<GymBranchSummary>>any());
        assertThat(parameters.getValue().getValue("branchIds")).isEqualTo(List.of(BRANCH_A));
    }

    @Test
    void inactiveMetadataIsAvailableOnlyThroughTheExplicitAlternateQuery() {
        GymBranchSummary inactive = branch(BRANCH_B, "SOUTH", GymBranchStatus.INACTIVE);
        when(jdbcTemplate.query(
                eq(JdbcGymBranchPersistenceAdapter.REPORTING_BRANCHES_WITH_INACTIVE_SQL),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<GymBranchSummary>>any()))
                .thenReturn(List.of(inactive));

        assertThat(new JdbcGymBranchPersistenceAdapter(jdbcTemplate)
                .findCanonicalBranches(List.of(BRANCH_B), true))
                .containsExactly(inactive);

        String sql = JdbcGymBranchPersistenceAdapter.REPORTING_BRANCHES_WITH_INACTIVE_SQL
                .toLowerCase(java.util.Locale.ROOT);
        assertThat(sql)
                .contains("o.is_canonical = true", "o.status = 'active'", "b.id in (:branchids)")
                .doesNotContain("b.status = 'active'");
    }

    @Test
    void rejectsEmptyOversizedAndNullBranchSelectionsBeforeQuerying() {
        JdbcGymBranchPersistenceAdapter adapter = new JdbcGymBranchPersistenceAdapter(jdbcTemplate);

        assertThatThrownBy(() -> adapter.findCanonicalBranches(List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.findCanonicalBranches(
                java.util.Collections.singletonList(null), false))
                .isInstanceOf(IllegalArgumentException.class);
        List<UUID> oversized = java.util.stream.IntStream.range(0, 101)
                .mapToObj(index -> new UUID(0, index + 1L))
                .toList();
        assertThatThrownBy(() -> adapter.findCanonicalBranches(oversized, false))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void translatesBranchMetadataReadFailuresWithoutExposingDatabaseMessage() {
        when(jdbcTemplate.query(
                eq(JdbcGymBranchPersistenceAdapter.REPORTING_BRANCHES_ACTIVE_SQL),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<GymBranchSummary>>any()))
                .thenThrow(new DataRetrievalFailureException("private branch database detail"));

        assertThatThrownBy(() -> new JdbcGymBranchPersistenceAdapter(jdbcTemplate)
                .findCanonicalBranches(List.of(BRANCH_A), false))
                .isInstanceOf(GymBranchReportingUnavailableException.class)
                .hasMessage("Reporting branch metadata could not be read.")
                .hasMessageNotContaining("private branch database detail");
    }

    private static GymBranchSummary branch(UUID id, String code, GymBranchStatus status) {
        return new GymBranchSummary(
                id, ORGANIZATION_ID, code, code + " Gym", "America/El_Salvador", status, false);
    }
}
