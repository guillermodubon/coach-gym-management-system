package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates the mandatory organization scope for the administrator provisioned
 * after Flyway migrations have already run.
 */
@Component
class InitialAdministratorScopeProvisioner {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    InitialAdministratorScopeProvisioner(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    void provision(UUID userId, Instant grantedAt) {
        jdbcTemplate.update("""
                insert into gym.staff_scopes (
                    user_id, scope_type, granted_at, granted_by_user_id, version)
                values (:userId, 'ORGANIZATION', :grantedAt, null, 0)
                on conflict (user_id) do nothing
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("grantedAt", grantedAt.atOffset(ZoneOffset.UTC)));
    }
}
