package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodCoverageSummary;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodSource;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.application.MembershipPeriodCoverageSummaryUnavailableException;
import io.github.guillermodubon.coachgym.membership.application.MembershipPeriodPage;
import io.github.guillermodubon.coachgym.membership.application.MembershipPeriodQueryUnavailableException;
import io.github.guillermodubon.coachgym.membership.application.MembershipPeriodReadEntry;
import io.github.guillermodubon.coachgym.membership.application.MembershipPeriodReadQuery;
import io.github.guillermodubon.coachgym.membership.application.MembershipPeriodSearchQuery;
import io.github.guillermodubon.coachgym.membership.application.MembershipPage;
import io.github.guillermodubon.coachgym.membership.application.MembershipQueryUnavailableException;
import io.github.guillermodubon.coachgym.membership.application.MembershipReadEntry;
import io.github.guillermodubon.coachgym.membership.application.MembershipReadQuery;
import io.github.guillermodubon.coachgym.membership.application.MembershipSearchQuery;
import io.github.guillermodubon.coachgym.membership.application.MembershipSortDirection;
import io.github.guillermodubon.coachgym.membership.application.MembershipSortField;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPricingSnapshot;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPromotionSnapshot;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import io.github.guillermodubon.coachgym.promotion.DiscountType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Batched JDBC read models for memberships and immutable commercial-period snapshots. */
@Repository
class JdbcMembershipReadQueryAdapter implements MembershipReadQuery, MembershipPeriodReadQuery {

    private static final Map<MembershipSortField, String> SORT_COLUMNS = Map.of(
            MembershipSortField.CREATED_AT, "m.created_at",
            MembershipSortField.MEMBERSHIP_CODE, "lower(m.membership_code)");

    private static final String FROM_MEMBERSHIPS = """
            from gym.memberships m
            """;

    private static final String WHERE_AND_FILTERS = """
            where m.registered_at_branch_id = :branchId
            """;

    private static final String MEMBERSHIP_COLUMNS = """
            m.id as membership_id,
            m.membership_code,
            m.client_id,
            m.status as membership_status,
            m.created_at as membership_created_at,
            m.updated_at as membership_updated_at,
            m.version as membership_version,
            m.registered_at_branch_id as membership_branch_id,
            period.id as period_id,
            period.period_number,
            period.period_source,
            period.membership_plan_id,
            period.plan_code_snapshot,
            period.plan_name_snapshot,
            period.duration_value_snapshot,
            period.duration_unit_snapshot,
            period.list_price,
            period.currency,
            period.promotion_id,
            period.promotion_code_snapshot,
            period.promotion_name_snapshot,
            period.promotion_type_snapshot,
            period.promotion_value_snapshot,
            period.promotion_currency_snapshot,
            period.discount_amount,
            period.final_price,
            period.starts_on,
            period.base_ends_on,
            period.effective_ends_on,
            period.created_at as period_created_at,
            period.version as period_version,
            period.registered_at_branch_id as period_branch_id,
            coverage.coverage_scope_snapshot,
            coverage.covered_branch_count
            """;

    private static final String PERIOD_AND_COVERAGE = """
            left join lateral (
                select p.*
                from gym.membership_periods p
                where p.membership_id = m.id
                order by p.period_number desc, p.id asc
                limit 1
            ) period on true
            left join lateral (
                select snapshot.coverage_scope_snapshot,
                       count(covered.branch_id)::integer as covered_branch_count
                from gym.membership_period_coverage_snapshots snapshot
                left join gym.membership_period_branch_coverage covered
                  on covered.membership_period_id = snapshot.membership_period_id
                where snapshot.membership_period_id = period.id
                group by snapshot.coverage_scope_snapshot
            ) coverage on true
            """;

    private static final String PERIOD_HISTORY_COLUMNS = """
            p.id as period_id,
            p.period_number,
            p.period_source,
            p.membership_plan_id,
            p.plan_code_snapshot,
            p.plan_name_snapshot,
            p.duration_value_snapshot,
            p.duration_unit_snapshot,
            p.list_price,
            p.currency,
            p.promotion_id,
            p.promotion_code_snapshot,
            p.promotion_name_snapshot,
            p.promotion_type_snapshot,
            p.promotion_value_snapshot,
            p.promotion_currency_snapshot,
            p.discount_amount,
            p.final_price,
            p.starts_on,
            p.base_ends_on,
            p.effective_ends_on,
            p.created_at as period_created_at,
            p.version as period_version,
            p.registered_at_branch_id as period_branch_id,
            coverage.coverage_scope_snapshot,
            coverage.covered_branch_count
            """;

    private static final String PERIOD_HISTORY_FROM = """
            from gym.membership_periods p
            join gym.memberships m on m.id = p.membership_id
            left join lateral (
                select snapshot.coverage_scope_snapshot,
                       count(covered.branch_id)::integer as covered_branch_count
                from gym.membership_period_coverage_snapshots snapshot
                left join gym.membership_period_branch_coverage covered
                  on covered.membership_period_id = snapshot.membership_period_id
                where snapshot.membership_period_id = p.id
                group by snapshot.coverage_scope_snapshot
            ) coverage on true
            """;

    private static final String PERIOD_HISTORY_COUNT_FROM = """
            from gym.membership_periods p
            join gym.memberships m on m.id = p.membership_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcMembershipReadQueryAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public MembershipPage findPage(
            MembershipSearchQuery query,
            UUID authorizedBranchId) {
        Objects.requireNonNull(query, "Membership query is required.");
        Objects.requireNonNull(authorizedBranchId, "Authorized branch is required.");
        QueryFilters filters = filters(query, authorizedBranchId);
        try {
            Long count = jdbcTemplate.queryForObject(
                    "select count(*) " + FROM_MEMBERSHIPS + WHERE_AND_FILTERS + filters.whereClause(),
                    filters.parameters(),
                    Long.class);
            long totalElements = count == null ? 0 : count;
            List<MembershipReadEntry> items = jdbcTemplate.query(
                    "select " + MEMBERSHIP_COLUMNS + FROM_MEMBERSHIPS + PERIOD_AND_COVERAGE
                            + WHERE_AND_FILTERS + filters.whereClause()
                            + " order by " + orderBy(query.sort(), query.direction())
                            + ", m.id asc limit :pageSize offset :pageOffset",
                    filters.parameters()
                            .addValue("pageSize", query.size())
                            .addValue("pageOffset", query.offset()),
                    JdbcMembershipReadQueryAdapter::mapEntry);
            return new MembershipPage(
                    items,
                    query.page(),
                    query.size(),
                    totalElements,
                    totalPages(totalElements, query.size()));
        } catch (DataAccessException exception) {
            throw new MembershipQueryUnavailableException(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MembershipPeriodPage findPage(
            UUID membershipId,
            UUID authorizedBranchId,
            MembershipPeriodSearchQuery query) {
        Objects.requireNonNull(membershipId, "Membership identifier is required.");
        Objects.requireNonNull(authorizedBranchId, "Authorized branch is required.");
        Objects.requireNonNull(query, "Membership period query is required.");
        MapSqlParameterSource parameters = periodParameters(membershipId, authorizedBranchId);
        try {
            Long count = jdbcTemplate.queryForObject(
                    "select count(*) " + PERIOD_HISTORY_COUNT_FROM + PERIOD_HISTORY_WHERE,
                    parameters,
                    Long.class);
            long totalElements = count == null ? 0 : count;
            List<MembershipPeriodReadEntry> items = jdbcTemplate.query(
                    periodPageSql(query.direction()),
                    parameters.addValue("pageSize", query.size())
                            .addValue("pageOffset", query.offset()),
                    JdbcMembershipReadQueryAdapter::mapPeriodEntry);
            return new MembershipPeriodPage(
                    items,
                    query.page(),
                    query.size(),
                    totalElements,
                    totalPages(totalElements, query.size()));
        } catch (DataAccessException exception) {
            throw new MembershipPeriodQueryUnavailableException(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MembershipPeriodReadEntry> findById(
            UUID membershipId,
            UUID periodId,
            UUID authorizedBranchId) {
        Objects.requireNonNull(membershipId, "Membership identifier is required.");
        Objects.requireNonNull(periodId, "Membership period identifier is required.");
        Objects.requireNonNull(authorizedBranchId, "Authorized branch is required.");
        MapSqlParameterSource parameters = periodParameters(membershipId, authorizedBranchId)
                .addValue("periodId", periodId);
        try {
            List<MembershipPeriodReadEntry> entries = jdbcTemplate.query(
                    "select " + PERIOD_HISTORY_COLUMNS + PERIOD_HISTORY_FROM + PERIOD_HISTORY_WHERE
                            + " and p.id = :periodId limit 1",
                    parameters,
                    JdbcMembershipReadQueryAdapter::mapPeriodEntry);
            return entries.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new MembershipPeriodQueryUnavailableException(exception);
        }
    }

    private static final String PERIOD_HISTORY_WHERE = """
            where m.id = :membershipId
              and m.registered_at_branch_id = :branchId
              and p.membership_id = :membershipId
              and p.registered_at_branch_id = :branchId
            """;

    private static String periodPageSql(MembershipSortDirection direction) {
        String orderDirection = Objects.requireNonNull(direction).name();
        return """
                with selected_periods as (
                    select p.*
                    from gym.membership_periods p
                    join gym.memberships m on m.id = p.membership_id
                %s
                    order by p.period_number %s, p.id asc
                    limit :pageSize offset :pageOffset
                ), coverage_summary as (
                    select snapshot.membership_period_id,
                           snapshot.coverage_scope_snapshot,
                           count(covered.branch_id)::integer as covered_branch_count
                    from gym.membership_period_coverage_snapshots snapshot
                    join selected_periods selected
                      on selected.id = snapshot.membership_period_id
                    left join gym.membership_period_branch_coverage covered
                      on covered.membership_period_id = snapshot.membership_period_id
                    group by snapshot.membership_period_id, snapshot.coverage_scope_snapshot
                )
                select %s
                from selected_periods p
                left join coverage_summary coverage on coverage.membership_period_id = p.id
                order by p.period_number %s, p.id asc
                """.formatted(
                PERIOD_HISTORY_WHERE,
                orderDirection,
                PERIOD_HISTORY_COLUMNS,
                orderDirection);
    }

    private static MapSqlParameterSource periodParameters(UUID membershipId, UUID branchId) {
        return new MapSqlParameterSource()
                .addValue("membershipId", membershipId)
                .addValue("branchId", branchId);
    }

    private static QueryFilters filters(
            MembershipSearchQuery query,
            UUID authorizedBranchId) {
        StringBuilder where = new StringBuilder();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("branchId", authorizedBranchId);
        if (query.search() != null) {
            where.append(" and position(lower(:search) in lower(m.membership_code)) > 0 ");
            parameters.addValue("search", query.search());
        }
        if (query.clientId() != null) {
            where.append(" and m.client_id = :clientId ");
            parameters.addValue("clientId", query.clientId());
        }
        if (query.status() != null) {
            where.append(" and m.status = :status ");
            parameters.addValue("status", query.status().name());
        }
        return new QueryFilters(where.toString(), parameters);
    }

    private static String orderBy(
            MembershipSortField sort,
            MembershipSortDirection direction) {
        String column = SORT_COLUMNS.get(Objects.requireNonNull(sort));
        if (column == null) {
            throw new IllegalArgumentException("Unsupported membership sort field.");
        }
        return column + " " + Objects.requireNonNull(direction).name();
    }

    private static MembershipReadEntry mapEntry(ResultSet resultSet, int rowNumber)
            throws SQLException {
        UUID periodId = resultSet.getObject("period_id", UUID.class);
        if (periodId == null) {
            throw new MembershipQueryUnavailableException(
                    new IllegalStateException("Membership has no persisted period."));
        }
        String coverageScope = resultSet.getString("coverage_scope_snapshot");
        if (coverageScope == null) {
            throw new MembershipPeriodCoverageSummaryUnavailableException();
        }
        try {
            MembershipPeriodCoverageSummary coverage = new MembershipPeriodCoverageSummary(
                    MembershipPlanBranchCoverageScope.valueOf(coverageScope),
                    Math.toIntExact(resultSet.getLong("covered_branch_count")));
            MembershipDetails membership = new MembershipDetails(
                    resultSet.getObject("membership_id", UUID.class),
                    resultSet.getString("membership_code"),
                    resultSet.getObject("client_id", UUID.class),
                    MembershipStatus.valueOf(resultSet.getString("membership_status")),
                    mapPeriod(resultSet, periodId),
                    instant(resultSet, "membership_created_at"),
                    instant(resultSet, "membership_updated_at"),
                    resultSet.getLong("membership_version"),
                    resultSet.getObject("membership_branch_id", UUID.class));
            return new MembershipReadEntry(membership, coverage);
        } catch (MembershipPeriodCoverageSummaryUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MembershipQueryUnavailableException(exception);
        }
    }

    private static MembershipPeriodReadEntry mapPeriodEntry(ResultSet resultSet, int rowNumber)
            throws SQLException {
        UUID periodId = resultSet.getObject("period_id", UUID.class);
        String coverageScope = resultSet.getString("coverage_scope_snapshot");
        if (periodId == null) {
            throw new MembershipPeriodQueryUnavailableException(
                    new IllegalStateException("Persisted membership period identifier is missing."));
        }
        if (coverageScope == null) {
            throw new MembershipPeriodCoverageSummaryUnavailableException();
        }
        try {
            MembershipPeriodCoverageSummary coverage = new MembershipPeriodCoverageSummary(
                    MembershipPlanBranchCoverageScope.valueOf(coverageScope),
                    Math.toIntExact(resultSet.getLong("covered_branch_count")));
            return new MembershipPeriodReadEntry(mapPeriod(resultSet, periodId), coverage);
        } catch (MembershipPeriodCoverageSummaryUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MembershipPeriodQueryUnavailableException(exception);
        }
    }

    private static MembershipPeriodDetails mapPeriod(ResultSet resultSet, UUID periodId)
            throws SQLException {
        UUID promotionId = resultSet.getObject("promotion_id", UUID.class);
        MembershipPromotionSnapshot promotion = promotionId == null
                ? null
                : new MembershipPromotionSnapshot(
                        promotionId,
                        resultSet.getString("promotion_code_snapshot"),
                        resultSet.getString("promotion_name_snapshot"),
                        DiscountType.valueOf(resultSet.getString("promotion_type_snapshot")),
                        resultSet.getBigDecimal("promotion_value_snapshot"),
                        trimToNull(resultSet.getString("promotion_currency_snapshot")));
        MembershipPricingSnapshot pricing = new MembershipPricingSnapshot(
                resultSet.getObject("membership_plan_id", UUID.class),
                resultSet.getString("plan_code_snapshot"),
                resultSet.getString("plan_name_snapshot"),
                resultSet.getInt("duration_value_snapshot"),
                DurationUnit.valueOf(resultSet.getString("duration_unit_snapshot")),
                resultSet.getBigDecimal("list_price"),
                resultSet.getString("currency").trim(),
                promotion,
                resultSet.getBigDecimal("discount_amount"),
                resultSet.getBigDecimal("final_price"));
        return new MembershipPeriodDetails(
                periodId,
                resultSet.getShort("period_number"),
                MembershipPeriodSource.valueOf(resultSet.getString("period_source")),
                pricing,
                resultSet.getObject("starts_on", java.time.LocalDate.class),
                resultSet.getObject("base_ends_on", java.time.LocalDate.class),
                resultSet.getObject("effective_ends_on", java.time.LocalDate.class),
                instant(resultSet, "period_created_at"),
                resultSet.getLong("period_version"),
                resultSet.getObject("period_branch_id", UUID.class));
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int totalPages(long totalElements, int size) {
        long pages = totalElements / size + (totalElements % size == 0 ? 0 : 1);
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }

    private record QueryFilters(String whereClause, MapSqlParameterSource parameters) {}
}
