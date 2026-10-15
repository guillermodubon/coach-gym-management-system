package io.github.guillermodubon.coachgym.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DashboardExceptionContractTest {

    @Test
    void dataAccessExceptionRetainsSafeMessageAndCause() {
        RuntimeException cause = new RuntimeException("database detail");
        DashboardDataAccessException exception =
                new DashboardDataAccessException(
                        "Dashboard data could not be read.", cause);
        assertThat(exception.getMessage())
                .isEqualTo("Dashboard data could not be read.");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void currencyConflictDefensivelyCopiesCurrencies() {
        DashboardCurrencyConflictException exception =
                new DashboardCurrencyConflictException(Set.of("USD", "EUR"));
        assertThat(exception.currencies())
                .containsExactlyInAnyOrder("USD", "EUR");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> exception.currencies().add("GBP"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
