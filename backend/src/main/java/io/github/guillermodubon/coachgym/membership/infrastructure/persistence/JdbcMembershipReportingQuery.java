package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import io.github.guillermodubon.coachgym.membership.MembershipReportingQuery;
import io.github.guillermodubon.coachgym.membership.MembershipReportingSummary;
import io.github.guillermodubon.coachgym.membership.MembershipReportingSummary.CoverageDistribution;
import io.github.guillermodubon.coachgym.membership.MembershipReportingSummary.PlanDistribution;
import io.github.guillermodubon.coachgym.membership.MembershipReportingUnavailableException;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL reader for current membership and immutable period aggregates. */
@Repository
@Transactional(readOnly = true)
class JdbcMembershipReportingQuery implements MembershipReportingQuery {

    private static final String MEMBERSHIP_BRANCH_FILTER =
            "and m.registered_at_branch_id in (:branchIds)";
    private static final String PERIOD_BRANCH_FILTER =
            "and p.registered_at_branch_id in (:branchIds)";

    static final String MEMBERSHIP_SUMMARY_ORGANIZATION_SQL = membershipSummarySql("");
    static final String MEMBERSHIP_SUMMARY_BRANCH_SQL = membershipSummarySql(MEMBERSHIP_BRANCH_FILTER);
    static final String PERIOD_SUMMARY_ORGANIZATION_SQL = periodSummarySql("");
    static final String PERIOD_SUMMARY_BRANCH_SQL = periodSummarySql(PERIOD_BRANCH_FILTER);
    static final String PLAN_DISTRIBUTION_ORGANIZATION_SQL = planDistributionSql("");
    static final String PLAN_DISTRIBUTION_BRANCH_SQL =
            planDistributionSql(MEMBERSHIP_BRANCH_FILTER);
    static final String COVERAGE_DISTRIBUTION_ORGANIZATION_SQL = coverageDistributionSql("");
    static final String COVERAGE_DISTRIBUTION_BRANCH_SQL =
            coverageDistributionSql(MEMBERSHIP_BRANCH_FILTER);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcMembershipReportingQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate, "Named parameter JDBC template is required.");
    }

    @Override
    public MembershipReportingSummary summarize(
            ReportingQueryScope scope,
            ReportingQueryWindow window,
            LocalDate asOfDate) {
        Objects.requireNonNull(scope, "Membership reporting scope is required.");
        Objects.requireNonNull(window, "Membership reporting window is required.");
        Objects.requireNonNull(asOfDate, "Membership reporting as-of date is required.");

        try {
            MapSqlParameterSource parameters = parameters(scope, window)
                    .addValue("asOfDate", asOfDate)
                    .addValue(
                            "expiringFrom",
                            asOfDate.isAfter(window.fromInclusive())
                                    ? asOfDate : window.fromInclusive())
                    .addValue("expiringUntil", window.toExclusive());

            MembershipCounts membershipCounts = jdbcTemplate.queryForObject(
                    scope.organizationWide()
                            ? MEMBERSHIP_SUMMARY_ORGANIZATION_SQL
                            : MEMBERSHIP_SUMMARY_BRANCH_SQL,
                    parameters,
                    (resultSet, rowNumber) -> new MembershipCounts(
                            resultSet.getLong("active_memberships"),
                            resultSet.getLong("frozen_memberships"),
                            resultSet.getLong("expired_memberships"),
                            resultSet.getLong("cancelled_memberships"),
                            resultSet.getLong("new_memberships")));

            PeriodCounts periodCounts = jdbcTemplate.queryForObject(
                    scope.organizationWide()
                            ? PERIOD_SUMMARY_ORGANIZATION_SQL
                            : PERIOD_SUMMARY_BRANCH_SQL,
                    parameters,
                    (resultSet, rowNumber) -> new PeriodCounts(
                            resultSet.getLong("active_periods"),
                            resultSet.getLong("new_periods"),
                            resultSet.getLong("expiring_periods")));

            List<PlanDistribution> plans = jdbcTemplate.query(
                    scope.organizationWide()
                            ? PLAN_DISTRIBUTION_ORGANIZATION_SQL
                            : PLAN_DISTRIBUTION_BRANCH_SQL,
                    parameters,
                    (resultSet, rowNumber) -> new PlanDistribution(
                            resultSet.getString("plan_code"),
                            resultSet.getString("plan_name"),
                            resultSet.getLong("membership_count")));

            List<CoverageDistribution> coverage = jdbcTemplate.query(
                    scope.organizationWide()
                            ? COVERAGE_DISTRIBUTION_ORGANIZATION_SQL
                            : COVERAGE_DISTRIBUTION_BRANCH_SQL,
                    parameters,
                    (resultSet, rowNumber) -> new CoverageDistribution(
                            MembershipPlanBranchCoverageScope.valueOf(
                                    resultSet.getString("coverage_scope")),
                            resultSet.getLong("membership_count")));

            if (membershipCounts == null || periodCounts == null) {
                throw new MembershipReportingUnavailableException(null);
            }
            return new MembershipReportingSummary(
                    membershipCounts.activeMemberships(),
                    membershipCounts.frozenMemberships(),
                    membershipCounts.expiredMemberships(),
                    membershipCounts.cancelledMemberships(),
                    membershipCounts.newMemberships(),
                    periodCounts.activePeriods(),
                    periodCounts.newPeriods(),
                    periodCounts.expiringPeriods(),
                    plans,
                    coverage);
        } catch (DataAccessException exception) {
            throw new MembershipReportingUnavailableException(exception);
        }
    }

    private static String membershipSummarySql(String branchFilter) {
        return """
                select count(*) filter (where m.status = 'ACTIVE') as active_memberships,
                       count(*) filter (where m.status = 'FROZEN') as frozen_memberships,
                       count(*) filter (where m.status = 'EXPIRED') as expired_memberships,
                       count(*) filter (where m.status = 'CANCELLED') as cancelled_memberships,
                       count(*) filter (
                           where m.created_at >= :fromInclusive
                             and m.created_at < :toExclusive
                       ) as new_memberships
                from gym.memberships m
                where true
                  %s
                """.formatted(branchFilter);
    }

    private static String periodSummarySql(String branchFilter) {
        return """
                select
                    (
                        select count(*)
                        from gym.membership_periods p
                        join gym.memberships m on m.id = p.membership_id
                        where m.status = 'ACTIVE'
                          and p.starts_on <= :asOfDate
                          and p.effective_ends_on >= :asOfDate
                          and not exists (
                              select 1 from gym.membership_periods newer
                              where newer.membership_id = p.membership_id
                                and newer.period_number > p.period_number
                          )
                          %s
                    ) as active_periods,
                    (
                        select count(*)
                        from gym.membership_periods p
                        where p.created_at >= :fromInclusive
                          and p.created_at < :toExclusive
                          %s
                    ) as new_periods,
                    (
                        select count(*)
                        from gym.membership_periods p
                        join gym.memberships m on m.id = p.membership_id
                        where m.status = 'ACTIVE'
                          and p.starts_on <= :asOfDate
                          and p.effective_ends_on >= :asOfDate
                          and p.effective_ends_on >= :expiringFrom
                          and p.effective_ends_on < :expiringUntil
                          and not exists (
                              select 1 from gym.membership_periods newer
                              where newer.membership_id = p.membership_id
                                and newer.period_number > p.period_number
                          )
                          %s
                    ) as expiring_periods
                """.formatted(branchFilter, branchFilter, branchFilter);
    }

    private static String planDistributionSql(String branchFilter) {
        return """
                with latest_membership_period as (
                    select distinct on (m.id)
                           m.id as membership_id,
                           p.plan_code_snapshot as plan_code,
                           p.plan_name_snapshot as plan_name
                    from gym.memberships m
                    join gym.membership_periods p on p.membership_id = m.id
                    where true
                      %s
                    order by m.id, p.period_number desc
                )
                select latest.plan_code, latest.plan_name,
                       count(*) as membership_count
                from latest_membership_period latest
                group by latest.plan_code, latest.plan_name
                order by latest.plan_code, latest.plan_name
                """.formatted(branchFilter);
    }

    private static String coverageDistributionSql(String branchFilter) {
        return """
                with latest_membership_period as (
                    select distinct on (m.id)
                           m.id as membership_id,
                           p.id as membership_period_id
                    from gym.memberships m
                    join gym.membership_periods p on p.membership_id = m.id
                    where true
                      %s
                    order by m.id, p.period_number desc
                )
                select snapshot.coverage_scope_snapshot as coverage_scope,
                       count(*) as membership_count
                from latest_membership_period latest
                join gym.membership_period_coverage_snapshots snapshot
                  on snapshot.membership_period_id = latest.membership_period_id
                group by snapshot.coverage_scope_snapshot
                order by snapshot.coverage_scope_snapshot
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

    private static OffsetDateTime utc(java.time.Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record MembershipCounts(
            long activeMemberships,
            long frozenMemberships,
            long expiredMemberships,
            long cancelledMemberships,
            long newMemberships) {
    }

    private record PeriodCounts(long activePeriods, long newPeriods, long expiringPeriods) {
    }
}
