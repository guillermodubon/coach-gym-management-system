package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.membership.MembershipPeriodBranchCoverageDetails;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class JdbcMembershipPeriodBranchCoverageAdapterTest {

    private static final UUID PERIOD_ID = UUID.randomUUID();
    private static final UUID FIRST_BRANCH_ID = UUID.randomUUID();
    private static final UUID SECOND_BRANCH_ID = UUID.randomUUID();
    private static final Instant CAPTURED_AT = Instant.parse("2026-09-23T12:00:00Z");

    @Mock
    private JdbcTemplate jdbcTemplate;

    private JdbcMembershipPeriodBranchCoverageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JdbcMembershipPeriodBranchCoverageAdapter(jdbcTemplate);
    }

    @Test
    void entitlementQueryIsAnExactIndexedExistenceCheckAndAbsenceIsFalse() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(Object[].class)))
                .thenReturn(false);

        assertThat(adapter.coversBranch(PERIOD_ID, FIRST_BRANCH_ID)).isFalse();

        verify(jdbcTemplate).queryForObject(
                org.mockito.ArgumentMatchers.contains("SELECT EXISTS"),
                eq(Boolean.class),
                eq(PERIOD_ID),
                eq(FIRST_BRANCH_ID));
    }

    @Test
    void entitlementQueryPropagatesDatabaseFailuresInsteadOfTreatingThemAsAbsence() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(Object[].class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> adapter.coversBranch(PERIOD_ID, FIRST_BRANCH_ID))
                .isSameAs(failure);
    }

    @Test
    void capturePersistsHeaderAndEveryExactBranchInOnePersistenceCallSequence() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.batchUpdate(
                anyString(), org.mockito.ArgumentMatchers.<List<Object[]>>any()))
                .thenReturn(new int[] {1, 1});
        MembershipPeriodBranchCoverageDetails snapshot =
                new MembershipPeriodBranchCoverageDetails(
                        PERIOD_ID,
                        MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                        Set.of(FIRST_BRANCH_ID, SECOND_BRANCH_ID),
                        CAPTURED_AT,
                        4);

        adapter.capture(snapshot);

        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("membership_period_coverage_snapshots"),
                eq(PERIOD_ID),
                eq("SELECTED_BRANCHES"),
                eq(OffsetDateTime.ofInstant(CAPTURED_AT, ZoneOffset.UTC)),
                eq(4L));
        verify(jdbcTemplate).batchUpdate(
                org.mockito.ArgumentMatchers.contains("membership_period_branch_coverage"),
                org.mockito.ArgumentMatchers.<List<Object[]>>any());
    }
}
