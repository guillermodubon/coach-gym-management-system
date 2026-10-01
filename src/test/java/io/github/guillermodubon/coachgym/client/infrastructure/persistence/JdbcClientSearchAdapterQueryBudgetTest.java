package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.client.ClientSearchQuery;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.client.ClientSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class JdbcClientSearchAdapterQueryBudgetTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void pageProjectionUsesOnlyOneCountAndOneDataQueryRegardlessOfReturnedRows() {
        ClientSummary summary = new ClientSummary(
                UUID.randomUUID(),
                "CLI-000001",
                "Alex",
                "Example",
                "5550000000",
                null,
                ClientStatus.ACTIVE,
                null,
                null,
                false,
                Instant.parse("2026-09-30T12:00:00Z"),
                0);
        when(jdbcTemplate.queryForObject(
                eq(JdbcClientSearchAdapter.COUNT),
                any(SqlParameterSource.class),
                eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(
                anyString(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<ClientSummary>>any()))
                .thenReturn(List.of(summary));

        var page = new JdbcClientSearchAdapter(jdbcTemplate)
                .findAll(ClientSearchQuery.defaults());

        assertThat(page.items()).containsExactly(summary);
        verify(jdbcTemplate).queryForObject(
                eq(JdbcClientSearchAdapter.COUNT),
                any(SqlParameterSource.class),
                eq(Long.class));
        verify(jdbcTemplate).query(
                anyString(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<ClientSummary>>any());
        verifyNoMoreInteractions(jdbcTemplate);
    }
}
