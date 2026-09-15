package io.github.guillermodubon.coachgym.configuration.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicy;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyDetails;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyValidationException;
import io.github.guillermodubon.coachgym.configuration.application.AccessPaymentPolicyDataAccessException;
import io.github.guillermodubon.coachgym.configuration.application.AccessPaymentPolicyVersionConflictException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class JdbcAccessPaymentPolicyAdapterTest {

    private static final UUID ACTOR_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final Instant UPDATED_AT = Instant.parse(
            "2026-09-14T12:00:00Z");

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void readsTheAuthoritativeSingletonPolicy() {
        AccessPaymentPolicyDetails expected = new AccessPaymentPolicyDetails(
                false, 0L, UPDATED_AT, null);
        when(jdbcTemplate.queryForObject(
                eq(JdbcAccessPaymentPolicyAdapter.FIND_CURRENT_SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class))).thenReturn(expected);

        assertThat(new JdbcAccessPaymentPolicyAdapter(jdbcTemplate).findCurrent())
                .isSameAs(expected);
    }

    @Test
    void translatesReadFailuresWithoutExposingDatabaseDetails() {
        when(jdbcTemplate.queryForObject(
                eq(JdbcAccessPaymentPolicyAdapter.FIND_CURRENT_SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenThrow(new DataRetrievalFailureException("secret SQL detail"));

        assertThatThrownBy(() ->
                new JdbcAccessPaymentPolicyAdapter(jdbcTemplate).findCurrent())
                .isInstanceOf(AccessPaymentPolicyDataAccessException.class)
                .hasMessage("Access payment policy could not be read.");
    }

    @Test
    void updatesOnlyTheExpectedVersionAndReturnsThePersistedProjection() {
        AccessPaymentPolicyDetails expected = new AccessPaymentPolicyDetails(
                true, 1L, UPDATED_AT, ACTOR_ID);
        when(jdbcTemplate.query(
                eq(JdbcAccessPaymentPolicyAdapter.UPDATE_SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class))).thenReturn(List.of(expected));

        AccessPaymentPolicyDetails result =
                new JdbcAccessPaymentPolicyAdapter(jdbcTemplate).update(
                        AccessPaymentPolicy.enabled(), 0L, ACTOR_ID, UPDATED_AT);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void distinguishesAStaleVersionFromAnUnexpectedMissingSingleton() {
        when(jdbcTemplate.query(
                eq(JdbcAccessPaymentPolicyAdapter.UPDATE_SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(
                eq(JdbcAccessPaymentPolicyAdapter.EXISTS_SQL),
                any(MapSqlParameterSource.class),
                eq(Boolean.class))).thenReturn(true);

        assertThatThrownBy(() -> new JdbcAccessPaymentPolicyAdapter(jdbcTemplate)
                .update(AccessPaymentPolicy.enabled(), 0L, ACTOR_ID, UPDATED_AT))
                .isInstanceOf(AccessPaymentPolicyVersionConflictException.class)
                .hasMessage("Access payment policy was changed by another operation.");

        when(jdbcTemplate.queryForObject(
                eq(JdbcAccessPaymentPolicyAdapter.EXISTS_SQL),
                any(MapSqlParameterSource.class),
                eq(Boolean.class))).thenReturn(false);

        assertThatThrownBy(() -> new JdbcAccessPaymentPolicyAdapter(jdbcTemplate)
                .update(AccessPaymentPolicy.enabled(), 0L, ACTOR_ID, UPDATED_AT))
                .isInstanceOf(AccessPaymentPolicyDataAccessException.class)
                .hasMessage("Access payment policy could not be updated.");
    }

    @Test
    void validatesServerOwnedUpdateInputsBeforeAccessingTheDatabase() {
        JdbcAccessPaymentPolicyAdapter adapter =
                new JdbcAccessPaymentPolicyAdapter(jdbcTemplate);

        assertThatThrownBy(() -> adapter.update(null, 0L, ACTOR_ID, UPDATED_AT))
                .isInstanceOf(AccessPaymentPolicyValidationException.class)
                .hasMessage("Access payment policy must be provided.");
        assertThatThrownBy(() -> adapter.update(
                AccessPaymentPolicy.enabled(), -1L, ACTOR_ID, UPDATED_AT))
                .isInstanceOf(AccessPaymentPolicyValidationException.class)
                .hasMessage("Expected access payment policy version must not be negative.");
        assertThatThrownBy(() -> adapter.update(
                AccessPaymentPolicy.enabled(), 0L, null, UPDATED_AT))
                .isInstanceOf(AccessPaymentPolicyValidationException.class)
                .hasMessage("Policy update actor must be provided.");
        assertThatThrownBy(() -> adapter.update(
                AccessPaymentPolicy.enabled(), 0L, ACTOR_ID, null))
                .isInstanceOf(AccessPaymentPolicyValidationException.class)
                .hasMessage("Policy update timestamp must be provided.");

        verifyNoInteractions(jdbcTemplate);
    }
}
