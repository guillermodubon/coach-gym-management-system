package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.domain.PaymentValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentSearchQueryTest {

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    // ------------------------------------------------------------------
    // Valid construction
    // ------------------------------------------------------------------

    @Test
    void buildDefaultQuery() {
        PaymentSearchQuery query = PaymentSearchQuery.from(
                null, null, null, null, null, null, null,
                0, 25, "PAID_AT", "DESC");

        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(25);
        assertThat(query.sortField()).isEqualTo(PaymentSortField.PAID_AT);
        assertThat(query.direction()).isEqualTo(PaymentSortDirection.DESC);
        assertThat(query.clientId()).isNull();
        assertThat(query.status()).isNull();
        assertThat(query.paymentMethod()).isNull();
    }

    @Test
    void defaultsAppliedWhenSortAndDirectionAreNull() {
        PaymentSearchQuery query = PaymentSearchQuery.from(
                null, null, null, null, null, null, null,
                0, 25, null, null);

        assertThat(query.sortField()).isEqualTo(PaymentSortField.PAID_AT);
        assertThat(query.direction()).isEqualTo(PaymentSortDirection.DESC);
    }

    @Test
    void parsesAllFilters() {
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant until = Instant.parse("2026-08-31T23:59:59Z");

        PaymentSearchQuery query = PaymentSearchQuery.from(
                CLIENT_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PAID",
                "CASH",
                from,
                until,
                0, 10, "AMOUNT", "ASC");

        assertThat(query.clientId()).isEqualTo(CLIENT_ID);
        assertThat(query.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(query.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(query.paidFrom()).isEqualTo(from);
        assertThat(query.paidUntil()).isEqualTo(until);
        assertThat(query.sortField()).isEqualTo(PaymentSortField.AMOUNT);
        assertThat(query.direction()).isEqualTo(PaymentSortDirection.ASC);
    }

    @Test
    void parsesStatusCaseInsensitive() {
        PaymentSearchQuery query = PaymentSearchQuery.from(
                null, null, null, "paid", null, null, null,
                0, 10, null, null);

        assertThat(query.status()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void acceptsMaxPageSize() {
        PaymentSearchQuery query = PaymentSearchQuery.from(
                null, null, null, null, null, null, null,
                0, 100, null, null);

        assertThat(query.size()).isEqualTo(100);
    }

    @Test
    void acceptsPaidFromEqualToPaidUntil() {
        Instant ts = Instant.parse("2026-08-25T18:00:00Z");

        PaymentSearchQuery query = PaymentSearchQuery.from(
                null, null, null, null, null, ts, ts,
                0, 25, null, null);

        assertThat(query.paidFrom()).isEqualTo(ts);
        assertThat(query.paidUntil()).isEqualTo(ts);
    }

    // ------------------------------------------------------------------
    // Rejections
    // ------------------------------------------------------------------

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> PaymentSearchQuery.from(
                null, null, null, null, null, null, null,
                -1, 25, null, null))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void rejectsSizeZero() {
        assertThatThrownBy(() -> PaymentSearchQuery.from(
                null, null, null, null, null, null, null,
                0, 0, null, null))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("size");
    }

    @Test
    void rejectsSizeOver100() {
        assertThatThrownBy(() -> PaymentSearchQuery.from(
                null, null, null, null, null, null, null,
                0, 101, null, null))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("size");
    }

    @Test
    void rejectsPaidFromAfterPaidUntil() {
        Instant from = Instant.parse("2026-08-31T00:00:00Z");
        Instant until = Instant.parse("2026-08-01T00:00:00Z");

        assertThatThrownBy(() -> PaymentSearchQuery.from(
                null, null, null, null, null, from, until,
                0, 25, null, null))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("paidFrom");
    }

    @Test
    void rejectsUnknownSortField() {
        assertThatThrownBy(() -> PaymentSearchQuery.from(
                null, null, null, null, null, null, null,
                0, 25, "INVALID_FIELD", null))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("sort field");
    }

    @Test
    void rejectsUnknownSortDirection() {
        assertThatThrownBy(() -> PaymentSearchQuery.from(
                null, null, null, null, null, null, null,
                0, 25, null, "SIDEWAYS"))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("direction");
    }

    @Test
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> PaymentSearchQuery.from(
                null, null, null, "PENDING", null, null, null,
                0, 25, null, null))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("status");
    }

    @Test
    void rejectsUnknownPaymentMethod() {
        assertThatThrownBy(() -> PaymentSearchQuery.from(
                null, null, null, null, "CRYPTO", null, null,
                0, 25, null, null))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("method");
    }
}
