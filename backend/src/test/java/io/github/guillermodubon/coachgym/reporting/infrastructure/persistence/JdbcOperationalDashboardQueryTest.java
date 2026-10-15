package io.github.guillermodubon.coachgym.reporting.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.reporting.DashboardNotificationDetails;
import io.github.guillermodubon.coachgym.reporting.EquipmentDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.IncidentDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.MaintenanceDashboardDetails;
import io.github.guillermodubon.coachgym.reporting.application.DashboardDataAccessException;
import io.github.guillermodubon.coachgym.reporting.application.DashboardSettings;
import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import io.github.guillermodubon.coachgym.organization.OrganizationIdentityQuery;
import io.github.guillermodubon.coachgym.organization.OrganizationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
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
class JdbcOperationalDashboardQueryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private OrganizationIdentityQuery organizationQuery;

    @Test
    void equipmentReaderReturnsAggregate() {
        EquipmentDashboardDetails expected =
                new EquipmentDashboardDetails(10, 2, 1);
        when(jdbcTemplate.queryForObject(
                eq(JdbcEquipmentDashboardQuery.SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class))).thenReturn(expected);

        assertThat(new JdbcEquipmentDashboardQuery(jdbcTemplate).summarize())
                .isSameAs(expected);
    }

    @Test
    void incidentReaderReturnsAggregate() {
        IncidentDashboardDetails expected =
                new IncidentDashboardDetails(3, 2, 1);
        when(jdbcTemplate.queryForObject(
                eq(JdbcIncidentDashboardQuery.SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class))).thenReturn(expected);

        assertThat(new JdbcIncidentDashboardQuery(jdbcTemplate).summarize())
                .isSameAs(expected);
    }

    @Test
    void maintenanceReaderUsesOperationalDate() {
        MaintenanceDashboardDetails expected =
                new MaintenanceDashboardDetails(4, 1, 2);
        when(jdbcTemplate.queryForObject(
                eq(JdbcMaintenanceDashboardQuery.SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class))).thenReturn(expected);

        LocalDate date = LocalDate.of(2026, 9, 5);
        assertThat(new JdbcMaintenanceDashboardQuery(jdbcTemplate)
                .summarize(date)).isSameAs(expected);
    }

    @Test
    void notificationReaderScopesCountToRecipient() {
        UUID recipient = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(
                eq(JdbcDashboardNotificationQuery.SQL),
                any(MapSqlParameterSource.class),
                eq(Long.class))).thenReturn(5L);

        DashboardNotificationDetails result =
                new JdbcDashboardNotificationQuery(jdbcTemplate)
                        .summarize(recipient);
        assertThat(result.unread()).isEqualTo(5);
    }

    @Test
    void settingsReaderReturnsAuthoritativeValues() {
        DashboardSettings expected = new DashboardSettings(7, "USD");
        when(jdbcTemplate.queryForObject(
                eq(JdbcDashboardSettingsQuery.SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class))).thenReturn(7);
        when(organizationQuery.findCanonical()).thenReturn(Optional.of(organization()));

        assertThat(new JdbcDashboardSettingsQuery(jdbcTemplate, organizationQuery).load())
                .isEqualTo(expected);
    }

    @Test
    void readersTranslateJdbcFailures() {
        when(jdbcTemplate.queryForObject(
                eq(JdbcEquipmentDashboardQuery.SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenThrow(new DataRetrievalFailureException("database detail"));

        assertThatThrownBy(
                () -> new JdbcEquipmentDashboardQuery(jdbcTemplate).summarize())
                .isInstanceOf(DashboardDataAccessException.class)
                .hasMessage("Equipment dashboard metrics could not be read.");
    }

    private static OrganizationDetails organization() {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        return new OrganizationDetails(
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                "COACH_GYM", "Coach Gym Legal", "Coach Gym", "support@example.test",
                "+50370000000", "America/El_Salvador", "USD", OrganizationStatus.ACTIVE,
                timestamp, timestamp, 0);
    }
}
