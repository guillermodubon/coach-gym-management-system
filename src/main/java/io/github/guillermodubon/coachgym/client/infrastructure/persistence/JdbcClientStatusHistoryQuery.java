package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.client.ClientStatusHistoryDetails;
import io.github.guillermodubon.coachgym.client.application.ClientProfileDataAccessException;
import io.github.guillermodubon.coachgym.client.application.ClientStatusHistoryQuery;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcClientStatusHistoryQuery implements ClientStatusHistoryQuery {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcClientStatusHistoryQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ClientStatusHistoryDetails> findByClientId(UUID clientId) {
        try {
            return List.copyOf(jdbc.query("""
                    select id, client_id, previous_status, new_status,
                           reason, occurred_at, changed_by_user_id
                    from gym.client_status_history
                    where client_id = :clientId
                    order by occurred_at desc, id asc
                    """,
                    new MapSqlParameterSource("clientId", clientId),
                    (rs, row) -> new ClientStatusHistoryDetails(
                            rs.getObject("id", UUID.class),
                            rs.getObject("client_id", UUID.class),
                            ClientStatus.valueOf(rs.getString("previous_status")),
                            ClientStatus.valueOf(rs.getString("new_status")),
                            rs.getString("reason"),
                            rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                            rs.getObject("changed_by_user_id", UUID.class))));
        } catch (DataAccessException exception) {
            throw new ClientProfileDataAccessException(
                    "Client status history could not be read.", exception);
        }
    }
}
