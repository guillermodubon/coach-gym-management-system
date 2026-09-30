package io.github.guillermodubon.coachgym.access.infrastructure.persistence;

import io.github.guillermodubon.coachgym.access.AccessBranchReportingSummary;
import io.github.guillermodubon.coachgym.access.AccessDailyTrendPoint;
import io.github.guillermodubon.coachgym.access.AccessOperationalDaySummary;
import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessReportingQuery;
import io.github.guillermodubon.coachgym.access.AccessReportingSummary;
import io.github.guillermodubon.coachgym.access.AccessReportingUnavailableException;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL reader for immutable physical access-attempt reporting. */
@Repository
@Transactional(readOnly = true)
class JdbcAccessReportingQuery implements AccessReportingQuery {

    private static final String BRANCH_FILTER = "and ar.branch_id in (:branchIds)";

    static final String OPERATIONAL_DAY_ORGANIZATION_SQL = operationalDaySql("");
    static final String OPERATIONAL_DAY_BRANCH_SQL = operationalDaySql(BRANCH_FILTER);
    static final String SUMMARY_ORGANIZATION_SQL = summarySql("");
    static final String SUMMARY_BRANCH_SQL = summarySql(BRANCH_FILTER);
    static final String BRANCHES_ORGANIZATION_SQL = branchSummarySql("");
    static final String BRANCHES_FILTERED_SQL = branchSummarySql(BRANCH_FILTER);
    static final String DAILY_TREND_ORGANIZATION_SQL = dailyTrendSql("");
    static final String DAILY_TREND_BRANCH_SQL = dailyTrendSql(BRANCH_FILTER);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcAccessReportingQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate, "Named parameter JDBC template is required.");
    }

    @Override
    public AccessOperationalDaySummary summarizeOperationalDay(
            ReportingQueryScope scope,
            LocalDate day,
            ZoneId timezone) {
        Objects.requireNonNull(day, "Access operational day is required.");
        Objects.requireNonNull(timezone, "Access reporting timezone is required.");
        ReportingQueryWindow window = new ReportingQueryWindow(day, day.plusDays(1), timezone);
        requireQuery(scope, window);
        try {
            return jdbcTemplate.queryForObject(
                    scope.organizationWide()
                            ? OPERATIONAL_DAY_ORGANIZATION_SQL : OPERATIONAL_DAY_BRANCH_SQL,
                    parameters(scope, window),
                    (resultSet, rowNumber) -> new AccessOperationalDaySummary(
                            day,
                            timezone,
                            resultSet.getLong("total_attempts"),
                            resultSet.getLong("allowed_attempts"),
                            resultSet.getLong("denied_attempts")));
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new AccessReportingUnavailableException(exception);
        }
    }

    @Override
    public AccessReportingSummary summarize(
            ReportingQueryScope scope,
            ReportingQueryWindow window) {
        requireQuery(scope, window);
        try {
            List<SummaryRow> rows = jdbcTemplate.query(
                    scope.organizationWide() ? SUMMARY_ORGANIZATION_SQL : SUMMARY_BRANCH_SQL,
                    parameters(scope, window),
                    (resultSet, rowNumber) -> new SummaryRow(
                            resultSet.getLong("total_attempts"),
                            resultSet.getLong("allowed_attempts"),
                            resultSet.getLong("denied_attempts"),
                            resultSet.getBigDecimal("allowed_rate"),
                            resultSet.getLong("manual_attempts"),
                            resultSet.getLong("qr_attempts"),
                            resultSet.getLong("unknown_source_attempts"),
                            resultSet.getString("reason_code"),
                            resultSet.getLong("reason_count")));
            if (rows.isEmpty()) {
                throw new AccessReportingUnavailableException(null);
            }
            SummaryRow total = rows.getFirst();
            EnumMap<AccessReasonCode, Long> reasons = new EnumMap<>(AccessReasonCode.class);
            rows.stream().filter(row -> row.reasonCode() != null).forEach(row ->
                    reasons.put(AccessReasonCode.valueOf(row.reasonCode()), row.reasonCount()));
            return new AccessReportingSummary(
                    total.totalAttempts(), total.allowedAttempts(), total.deniedAttempts(),
                    rate(total.allowedRate()), total.manualAttempts(), total.qrAttempts(),
                    total.unknownSourceAttempts(), reasons);
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new AccessReportingUnavailableException(exception);
        }
    }

    @Override
    public List<AccessBranchReportingSummary> summarizeByBranch(
            ReportingQueryScope scope,
            ReportingQueryWindow window) {
        requireQuery(scope, window);
        try {
            return jdbcTemplate.query(
                    scope.organizationWide()
                            ? BRANCHES_ORGANIZATION_SQL : BRANCHES_FILTERED_SQL,
                    parameters(scope, window),
                    (resultSet, rowNumber) -> new AccessBranchReportingSummary(
                            resultSet.getObject("branch_id", UUID.class),
                            resultSet.getLong("total_attempts"),
                            resultSet.getLong("allowed_attempts"),
                            resultSet.getLong("denied_attempts"),
                            rate(resultSet.getBigDecimal("allowed_rate")),
                            resultSet.getLong("manual_attempts"),
                            resultSet.getLong("qr_attempts"),
                            resultSet.getLong("unknown_source_attempts")));
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new AccessReportingUnavailableException(exception);
        }
    }

    @Override
    public List<AccessDailyTrendPoint> dailyTrend(
            ReportingQueryScope scope,
            ReportingQueryWindow window) {
        requireQuery(scope, window);
        MapSqlParameterSource parameters = parameters(scope, window)
                .addValue("fromDate", window.fromInclusive())
                .addValue("toDate", window.toExclusive())
                .addValue("timezone", window.timezone().getId());
        try {
            return jdbcTemplate.query(
                    scope.organizationWide()
                            ? DAILY_TREND_ORGANIZATION_SQL : DAILY_TREND_BRANCH_SQL,
                    parameters,
                    (resultSet, rowNumber) -> new AccessDailyTrendPoint(
                            resultSet.getDate("bucket_day").toLocalDate(),
                            resultSet.getLong("total_attempts"),
                            resultSet.getLong("allowed_attempts"),
                            resultSet.getLong("denied_attempts"),
                            resultSet.getLong("manual_attempts"),
                            resultSet.getLong("qr_attempts"),
                            resultSet.getLong("unknown_source_attempts")));
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new AccessReportingUnavailableException(exception);
        }
    }

    private static String summarySql(String branchFilter) {
        return """
                with scoped_attempts as (
                    select ar.decision, ar.identification_source, ar.reason_code
                    from gym.access_records ar
                    where ar.occurred_at >= :fromInclusive
                      and ar.occurred_at < :toExclusive
                      %s
                ), totals as (
                    select count(*) as total_attempts,
                           count(*) filter (where decision = 'ALLOWED') as allowed_attempts,
                           count(*) filter (where decision = 'DENIED') as denied_attempts,
                           coalesce(
                               count(*) filter (where decision = 'ALLOWED')::numeric
                                   / nullif(count(*), 0), 0::numeric) as allowed_rate,
                           count(*) filter (where identification_source in ('CLIENT_CODE', 'MEMBERSHIP_CODE'))
                               as manual_attempts,
                           count(*) filter (where identification_source = 'QR_CREDENTIAL') as qr_attempts,
                           count(*) filter (where identification_source = 'UNKNOWN') as unknown_source_attempts
                    from scoped_attempts
                ), reasons as (
                    select reason_code, count(*) as reason_count
                    from scoped_attempts
                    where decision = 'DENIED'
                    group by reason_code
                )
                select totals.*, reasons.reason_code, coalesce(reasons.reason_count, 0) as reason_count
                from totals
                left join reasons on true
                order by reasons.reason_code
                """.formatted(branchFilter);
    }

    private static String operationalDaySql(String branchFilter) {
        return """
                select count(*) as total_attempts,
                       count(*) filter (where ar.decision = 'ALLOWED') as allowed_attempts,
                       count(*) filter (where ar.decision = 'DENIED') as denied_attempts
                from gym.access_records ar
                where ar.occurred_at >= :fromInclusive
                  and ar.occurred_at < :toExclusive
                  %s
                """.formatted(branchFilter);
    }

    private static String branchSummarySql(String branchFilter) {
        return """
                select ar.branch_id,
                       count(*) as total_attempts,
                       count(*) filter (where ar.decision = 'ALLOWED') as allowed_attempts,
                       count(*) filter (where ar.decision = 'DENIED') as denied_attempts,
                       coalesce(
                           count(*) filter (where ar.decision = 'ALLOWED')::numeric
                               / nullif(count(*), 0), 0::numeric) as allowed_rate,
                       count(*) filter (where ar.identification_source in ('CLIENT_CODE', 'MEMBERSHIP_CODE'))
                           as manual_attempts,
                       count(*) filter (where ar.identification_source = 'QR_CREDENTIAL') as qr_attempts,
                       count(*) filter (where ar.identification_source = 'UNKNOWN') as unknown_source_attempts
                from gym.access_records ar
                where ar.occurred_at >= :fromInclusive
                  and ar.occurred_at < :toExclusive
                  %s
                group by ar.branch_id
                order by ar.branch_id
                """.formatted(branchFilter);
    }

    private static String dailyTrendSql(String branchFilter) {
        return """
                with days as (
                    select series_day::date as bucket_day
                    from generate_series(
                        cast(:fromDate as date),
                        cast(:toDate as date) - 1,
                        interval '1 day') as series_day
                ), daily as (
                    select (ar.occurred_at at time zone :timezone)::date as bucket_day,
                           count(*) as total_attempts,
                           count(*) filter (where ar.decision = 'ALLOWED') as allowed_attempts,
                           count(*) filter (where ar.decision = 'DENIED') as denied_attempts,
                           count(*) filter (where ar.identification_source in ('CLIENT_CODE', 'MEMBERSHIP_CODE'))
                               as manual_attempts,
                           count(*) filter (where ar.identification_source = 'QR_CREDENTIAL') as qr_attempts,
                           count(*) filter (where ar.identification_source = 'UNKNOWN') as unknown_source_attempts
                    from gym.access_records ar
                    where ar.occurred_at >= :fromInclusive
                      and ar.occurred_at < :toExclusive
                      %s
                    group by bucket_day
                )
                select days.bucket_day,
                       coalesce(daily.total_attempts, 0) as total_attempts,
                       coalesce(daily.allowed_attempts, 0) as allowed_attempts,
                       coalesce(daily.denied_attempts, 0) as denied_attempts,
                       coalesce(daily.manual_attempts, 0) as manual_attempts,
                       coalesce(daily.qr_attempts, 0) as qr_attempts,
                       coalesce(daily.unknown_source_attempts, 0) as unknown_source_attempts
                from days
                left join daily using (bucket_day)
                order by days.bucket_day
                """.formatted(branchFilter);
    }

    private static MapSqlParameterSource parameters(
            ReportingQueryScope scope,
            ReportingQueryWindow window) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("fromInclusive", utc(window.fromInclusiveInstant()))
                .addValue("toExclusive", utc(window.toExclusiveInstant()));
        if (!scope.organizationWide()) {
            parameters.addValue("branchIds", scope.branchIds());
        }
        return parameters;
    }

    private static BigDecimal rate(BigDecimal rate) {
        return rate == null ? BigDecimal.ZERO : rate.setScale(4, RoundingMode.HALF_UP);
    }

    private static OffsetDateTime utc(java.time.Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void requireQuery(ReportingQueryScope scope, ReportingQueryWindow window) {
        Objects.requireNonNull(scope, "Access reporting scope is required.");
        Objects.requireNonNull(window, "Access reporting window is required.");
    }

    private record SummaryRow(
            long totalAttempts,
            long allowedAttempts,
            long deniedAttempts,
            BigDecimal allowedRate,
            long manualAttempts,
            long qrAttempts,
            long unknownSourceAttempts,
            String reasonCode,
            long reasonCount) {
    }
}
