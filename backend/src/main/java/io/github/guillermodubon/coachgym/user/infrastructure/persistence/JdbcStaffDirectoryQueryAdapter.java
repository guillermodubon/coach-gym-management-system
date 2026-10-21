package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.application.StaffDirectoryDetails;
import io.github.guillermodubon.coachgym.user.application.StaffDirectoryEntry;
import io.github.guillermodubon.coachgym.user.application.StaffDirectoryPage;
import io.github.guillermodubon.coachgym.user.application.StaffDirectoryQuery;
import io.github.guillermodubon.coachgym.user.application.StaffDirectorySearchQuery;
import io.github.guillermodubon.coachgym.user.application.StaffDirectorySortDirection;
import io.github.guillermodubon.coachgym.user.application.StaffDirectorySortField;
import io.github.guillermodubon.coachgym.user.application.StaffDirectoryUnavailableException;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit-column JDBC read adapter for administrative staff directory views. */
@Component
class JdbcStaffDirectoryQueryAdapter implements StaffDirectoryQuery {

    private static final String DIRECTORY_COLUMNS = """
            u.id, u.username, u.first_name, u.last_name, u.status,
            array_agg(r.role_code order by r.role_code) as roles,
            s.scope_type, u.security_version, s.version as scope_version
            """;

    private static final String DIRECTORY_FROM = """
            from gym.users u
            join gym.staff_scopes s on s.user_id = u.id
            join gym.user_roles ur on ur.user_id = u.id
            join gym.roles r on r.id = ur.role_id
            """;

    private static final String DIRECTORY_GROUP_BY = """
            group by u.id, s.scope_type, s.version
            """;

    private static final Map<StaffDirectorySortField, String> SORT_COLUMNS = Map.of(
            StaffDirectorySortField.LAST_NAME, "lower(u.last_name)",
            StaffDirectorySortField.USERNAME, "lower(u.username)",
            StaffDirectorySortField.CREATED_AT, "u.created_at");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcStaffDirectoryQueryAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public StaffDirectoryPage findPage(StaffDirectorySearchQuery query) {
        Objects.requireNonNull(query, "Directory query is required.");
        QueryFilters filters = filters(query);
        try {
            Long count = jdbcTemplate.queryForObject(
                    "select count(distinct u.id)" + DIRECTORY_FROM + filters.whereClause(),
                    filters.parameters(),
                    Long.class);
            long totalElements = count == null ? 0 : count;
            long offset = Math.multiplyExact((long) query.page(), query.size());
            String orderBy = orderBy(query.sort(), query.direction());
            List<StaffDirectoryEntry> items = jdbcTemplate.query(
                    "select " + DIRECTORY_COLUMNS + DIRECTORY_FROM
                            + filters.whereClause() + DIRECTORY_GROUP_BY
                            + " order by " + orderBy + ", u.id asc"
                            + " limit :pageSize offset :pageOffset",
                    filters.parameters()
                            .addValue("pageSize", query.size())
                            .addValue("pageOffset", offset),
                    JdbcStaffDirectoryQueryAdapter::mapEntry);
            return new StaffDirectoryPage(
                    items, query.page(), query.size(), totalElements,
                    totalPages(totalElements, query.size()));
        } catch (DataAccessException | ArithmeticException exception) {
            throw new StaffDirectoryUnavailableException(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffDirectoryDetails> findById(UUID userId) {
        Objects.requireNonNull(userId, "userId is required.");
        try {
            List<StaffDirectoryDetails> details = jdbcTemplate.query("""
                    select u.id, u.username, u.first_name, u.last_name, u.status,
                           array_agg(r.role_code order by r.role_code) as roles,
                           s.scope_type, u.security_version, s.version as scope_version,
                           u.email, u.created_at, u.updated_at
                    """ + DIRECTORY_FROM + " where u.id = :userId " + DIRECTORY_GROUP_BY + """
                    , u.username, u.first_name, u.last_name, u.status,
                      u.security_version, u.email, u.created_at, u.updated_at
                    """,
                    new MapSqlParameterSource("userId", userId),
                    JdbcStaffDirectoryQueryAdapter::mapDetails);
            return details.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new StaffDirectoryUnavailableException(exception);
        }
    }

    private static QueryFilters filters(StaffDirectorySearchQuery query) {
        StringBuilder where = new StringBuilder(" where 1 = 1 ");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (query.search() != null) {
            where.append("""
                     and (
                         position(:search in lower(u.first_name)) > 0
                         or position(:search in lower(u.last_name)) > 0
                         or position(:search in lower(concat_ws(' ', u.first_name, u.last_name))) > 0
                         or position(:search in lower(u.username)) > 0
                         or position(:search in lower(u.email)) > 0
                     )
                    """);
            parameters.addValue("search", query.search());
        }
        if (query.status() != null) {
            where.append(" and u.status = :status ");
            parameters.addValue("status", query.status().name());
        }
        if (query.role() != null) {
            where.append("""
                     and exists (
                         select 1
                           from gym.user_roles filter_user_role
                           join gym.roles filter_role on filter_role.id = filter_user_role.role_id
                          where filter_user_role.user_id = u.id
                            and filter_role.role_code = :role
                     )
                    """);
            parameters.addValue("role", query.role().name());
        }
        if (query.scopeType() != null) {
            where.append(" and s.scope_type = :scopeType ");
            parameters.addValue("scopeType", query.scopeType().name());
        }
        if (query.branchId() != null) {
            where.append("""
                     and exists (
                         select 1
                           from gym.staff_branch_assignments branch_assignment
                          where branch_assignment.user_id = u.id
                            and branch_assignment.branch_id = :branchId
                            and branch_assignment.status = 'ACTIVE'
                     )
                    """);
            parameters.addValue("branchId", query.branchId());
        }
        return new QueryFilters(where.toString(), parameters);
    }

    private static String orderBy(
            StaffDirectorySortField sort,
            StaffDirectorySortDirection direction) {
        String column = SORT_COLUMNS.get(Objects.requireNonNull(sort));
        if (column == null) {
            throw new IllegalArgumentException("Unsupported directory sort field.");
        }
        return column + " " + Objects.requireNonNull(direction).name();
    }

    private static int totalPages(long totalElements, int size) {
        long pages = totalElements / size + (totalElements % size == 0 ? 0 : 1);
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }

    private static StaffDirectoryEntry mapEntry(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new StaffDirectoryEntry(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("username"),
                resultSet.getString("first_name"),
                resultSet.getString("last_name"),
                StaffAccountStatus.valueOf(resultSet.getString("status")),
                roles(resultSet),
                StaffScopeType.valueOf(resultSet.getString("scope_type")),
                resultSet.getLong("security_version"),
                resultSet.getLong("scope_version"));
    }

    private static StaffDirectoryDetails mapDetails(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new StaffDirectoryDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("username"),
                resultSet.getString("first_name"),
                resultSet.getString("last_name"),
                StaffAccountStatus.valueOf(resultSet.getString("status")),
                roles(resultSet),
                StaffScopeType.valueOf(resultSet.getString("scope_type")),
                resultSet.getLong("security_version"),
                resultSet.getLong("scope_version"),
                resultSet.getString("email"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static List<RoleCode> roles(ResultSet resultSet) throws SQLException {
        Array array = resultSet.getArray("roles");
        if (array == null) {
            return List.of();
        }
        try {
            Object[] roleCodes = (Object[]) array.getArray();
            return java.util.Arrays.stream(roleCodes)
                    .map(Object::toString)
                    .map(RoleCode::valueOf)
                    .sorted()
                    .toList();
        } finally {
            array.free();
        }
    }

    private record QueryFilters(String whereClause, MapSqlParameterSource parameters) {}
}
