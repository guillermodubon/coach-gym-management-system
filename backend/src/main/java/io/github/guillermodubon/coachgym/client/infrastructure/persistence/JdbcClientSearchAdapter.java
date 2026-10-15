package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import io.github.guillermodubon.coachgym.client.ClientPage;
import io.github.guillermodubon.coachgym.client.ClientSearchQuery;
import io.github.guillermodubon.coachgym.client.ClientSortDirection;
import io.github.guillermodubon.coachgym.client.ClientSortField;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.client.ClientSummary;
import io.github.guillermodubon.coachgym.client.application.ClientProfileDataAccessException;
import io.github.guillermodubon.coachgym.client.application.ClientSearchStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcClientSearchAdapter implements ClientSearchStore {

    static final String FROM_AND_FILTERS = """
            from gym.clients c
            left join lateral (
                select m.status, mp.effective_ends_on
                from gym.memberships m
                left join lateral (
                    select effective_ends_on
                    from gym.membership_periods
                    where membership_id = m.id
                    order by period_number desc
                    limit 1
                ) mp on true
                where m.client_id = c.id
                order by
                    case m.status
                        when 'ACTIVE' then 0
                        when 'FROZEN' then 1
                        when 'EXPIRED' then 2
                        else 3
                    end,
                    m.updated_at desc,
                    m.id
                limit 1
            ) current_membership on true
                       where (cast(:search as varchar) is null or
                              lower(c.client_code) like cast(:searchPattern as varchar) or
                              lower(c.first_name) like cast(:searchPattern as varchar) or
                              lower(c.last_name) like cast(:searchPattern as varchar) or
                              lower(concat_ws(' ', c.first_name, c.last_name))
                                  like cast(:searchPattern as varchar) or
                              lower(c.phone) like cast(:searchPattern as varchar) or
                              lower(coalesce(c.email, ''))
                                  like cast(:searchPattern as varchar))
                         and (cast(:status as varchar) is null
                              or c.status = cast(:status as varchar))
                         and (cast(:membershipStatus as varchar) is null
                              or exists (
                                  select 1
                                  from gym.memberships fm
                                  where fm.client_id = c.id
                                    and fm.status =
                                        cast(:membershipStatus as varchar)
                              ))
                         and (cast(:branchId as uuid) is null
                              or c.home_branch_id = cast(:branchId as uuid))
            """;

    static final String SELECT = """
            select c.id, c.client_code, c.first_name, c.last_name,
                   c.phone, c.email, c.status,
                   current_membership.status as membership_status,
                   current_membership.effective_ends_on as membership_expires_on,
                   exists(select 1 from gym.client_photos cp where cp.client_id = c.id)
                       as photo_available,
                   c.updated_at, c.version, c.home_branch_id
            """;

    static final String COUNT = "select count(*) " + FROM_AND_FILTERS;

    private static final Map<ClientSortField, String> SORT_COLUMNS = Map.of(
            ClientSortField.CLIENT_CODE, "c.client_code",
            ClientSortField.FIRST_NAME, "c.first_name",
            ClientSortField.LAST_NAME, "c.last_name",
            ClientSortField.CREATED_AT, "c.created_at",
            ClientSortField.UPDATED_AT, "c.updated_at");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcClientSearchAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public ClientPage findAll(ClientSearchQuery query) {
        return findAll(query, query.branchId());
    }

    @Override
    public ClientPage findAll(ClientSearchQuery query, java.util.UUID branchId) {
        Objects.requireNonNull(query, "Client search query is required.");
        MapSqlParameterSource parameters = parameters(query, branchId);
        String dataSql = SELECT + FROM_AND_FILTERS + orderBy(query)
                + " limit :limit offset :offset";
        try {
            Long total = jdbcTemplate.queryForObject(COUNT, parameters, Long.class);
            List<ClientSummary> items = jdbcTemplate.query(
                    dataSql, parameters, JdbcClientSearchAdapter::mapSummary);
            long safeTotal = total == null ? 0L : total;
            int totalPages = safeTotal == 0L
                    ? 0
                    : (int) ((safeTotal + query.size() - 1) / query.size());
            return new ClientPage(
                    items, query.page(), query.size(), safeTotal, totalPages);
        } catch (DataAccessException exception) {
            throw new ClientProfileDataAccessException(
                    "Client catalog could not be read.", exception);
        }
    }

    static String orderBy(ClientSearchQuery query) {
        String column = SORT_COLUMNS.get(query.sort());
        if (column == null) {
            throw new IllegalArgumentException("Unsupported client sort field.");
        }
        String direction = query.direction() == ClientSortDirection.DESC
                ? "desc" : "asc";
        if (query.sort() == ClientSortField.LAST_NAME) {
            return " order by c.last_name " + direction
                    + ", c.first_name " + direction + ", c.id asc";
        }
        return " order by " + column + " " + direction + ", c.id asc";
    }

    private static MapSqlParameterSource parameters(
            ClientSearchQuery query,
            java.util.UUID branchId) {
        String searchPattern = query.search() == null
                ? null
                : "%" + query.search().toLowerCase(Locale.ROOT) + "%";
        return new MapSqlParameterSource()
                .addValue("search", query.search())
                .addValue("searchPattern", searchPattern)
                .addValue("status", query.status() == null ? null : query.status().name())
                .addValue("membershipStatus", query.membershipStatus())
                .addValue("branchId", branchId)
                .addValue("limit", query.size())
                .addValue("offset", (long) query.page() * query.size());
    }

    private static ClientSummary mapSummary(ResultSet rs, int row) throws SQLException {
        OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);
        return new ClientSummary(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("client_code"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("phone"),
                rs.getString("email"),
                ClientStatus.valueOf(rs.getString("status")),
                rs.getString("membership_status"),
                rs.getObject("membership_expires_on", java.time.LocalDate.class),
                rs.getBoolean("photo_available"),
                updatedAt.toInstant(),
                rs.getLong("version"),
                rs.getObject("home_branch_id", java.util.UUID.class));
    }
}
