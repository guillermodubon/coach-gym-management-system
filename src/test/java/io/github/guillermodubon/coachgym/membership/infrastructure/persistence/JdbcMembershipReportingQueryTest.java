package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.membership.MembershipReportingUnavailableException;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class JdbcMembershipReportingQueryTest {

    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void mapsDataAccessFailuresToASafeMembershipReportingError() {
        when(jdbcTemplate.queryForObject(
                eq(JdbcMembershipReportingQuery.MEMBERSHIP_SUMMARY_BRANCH_SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenThrow(new DataRetrievalFailureException("private database detail"));

        assertThatThrownBy(() -> new JdbcMembershipReportingQuery(jdbcTemplate).summarize(
                ReportingQueryScope.branches(java.util.List.of(java.util.UUID.randomUUID())),
                new ReportingQueryWindow(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 2),
                        ZoneId.of("UTC")),
                LocalDate.of(2026, 9, 1)))
                .isInstanceOf(MembershipReportingUnavailableException.class)
                .hasMessage("Membership reporting data could not be read.")
                .hasCauseInstanceOf(DataRetrievalFailureException.class)
                .hasMessageNotContaining("private database detail");
    }
}
