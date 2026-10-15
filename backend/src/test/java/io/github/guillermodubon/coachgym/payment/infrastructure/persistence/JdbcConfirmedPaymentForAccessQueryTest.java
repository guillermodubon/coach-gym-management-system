package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.payment.application.ConfirmedPaymentForAccessDataAccessException;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class JdbcConfirmedPaymentForAccessQueryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void returnsTrueWhenExactRelationshipHasPaidPayment() {
        UUID clientId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(
                eq(JdbcConfirmedPaymentForAccessQuery.SQL),
                any(MapSqlParameterSource.class),
                eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        boolean result = new JdbcConfirmedPaymentForAccessQuery(jdbcTemplate)
                .hasConfirmedPaymentForPeriod(clientId, membershipId, periodId);

        assertThat(result).isTrue();
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                eq(JdbcConfirmedPaymentForAccessQuery.SQL),
                parameters.capture(),
                eq(Boolean.class));
        assertThat(parameters.getValue().getValue("clientId"))
                .isEqualTo(clientId);
        assertThat(parameters.getValue().getValue("membershipId"))
                .isEqualTo(membershipId);
        assertThat(parameters.getValue().getValue("membershipPeriodId"))
                .isEqualTo(periodId);
    }

    @Test
    void returnsFalseWhenNoExactPaidPaymentExists() {
        when(jdbcTemplate.queryForObject(
                eq(JdbcConfirmedPaymentForAccessQuery.SQL),
                any(MapSqlParameterSource.class),
                eq(Boolean.class)))
                .thenReturn(Boolean.FALSE);

        boolean result = new JdbcConfirmedPaymentForAccessQuery(jdbcTemplate)
                .hasConfirmedPaymentForPeriod(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).isFalse();
    }

    @Test
    void translatesDatabaseFailureWithoutExposingDatabaseDetails() {
        DataRetrievalFailureException databaseFailure =
                new DataRetrievalFailureException("secret database detail");
        when(jdbcTemplate.queryForObject(
                eq(JdbcConfirmedPaymentForAccessQuery.SQL),
                any(MapSqlParameterSource.class),
                eq(Boolean.class)))
                .thenThrow(databaseFailure);

        assertThatThrownBy(() ->
                new JdbcConfirmedPaymentForAccessQuery(jdbcTemplate)
                        .hasConfirmedPaymentForPeriod(
                                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ConfirmedPaymentForAccessDataAccessException.class)
                .hasMessage("Confirmed payment for access could not be read.")
                .hasCause(databaseFailure)
                .hasMessageNotContaining("secret database detail");
    }

    @Test
    void rejectsMissingRelationshipIdentifiersBeforeDatabaseAccess() {
        JdbcConfirmedPaymentForAccessQuery query =
                new JdbcConfirmedPaymentForAccessQuery(jdbcTemplate);

        assertThatThrownBy(() -> query.hasConfirmedPaymentForPeriod(
                null, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Client id is required.");
        assertThatThrownBy(() -> query.hasConfirmedPaymentForPeriod(
                UUID.randomUUID(), null, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Membership id is required.");
        assertThatThrownBy(() -> query.hasConfirmedPaymentForPeriod(
                UUID.randomUUID(), UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Membership period id is required.");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void usesOnlyExactPaidPaymentExistencePredicate() {
        String sql = JdbcConfirmedPaymentForAccessQuery.SQL
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("select exists")
                .contains("from gym.payments p")
                .contains("p.client_id = :clientid")
                .contains("p.membership_id = :membershipid")
                .contains("p.membership_period_id = :membershipperiodid")
                .contains("p.status = 'paid'")
                .doesNotContain("payment_attempt")
                .doesNotContain("stripe")
                .doesNotContain("receipt")
                .doesNotContain("external_reference")
                .doesNotContain("select *");
    }
}
