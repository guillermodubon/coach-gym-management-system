package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentReceiptSnapshotTest {

    private static final UUID PAYMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000901");
    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000902");
    private static final Instant PAID_AT = Instant.parse("2026-09-11T10:00:00Z");
    private static final Instant GENERATED_AT = Instant.parse("2026-09-11T10:05:00Z");

    @Test
    void normalizesServerSnapshotValuesAndPreservesFinancialPrecision() {
        PaymentReceiptSnapshot snapshot = new PaymentReceiptSnapshot(
                "  REC-0001  ", PAYMENT_ID, " PAY-0001 ", PaymentStatus.PAID,
                " CLI-0001 ", " Ana Martinez ", " MEM-0001 ", " Premium ",
                " Intro ", 1, LocalDate.of(2026, 9, 11), LocalDate.of(2026, 10, 10),
                new BigDecimal("30"), new BigDecimal("5.00"), new BigDecimal("25.0"),
                " usd ", PaymentMethod.CARD, PAID_AT, GENERATED_AT, ACTOR_ID,
                " Ana Admin ", true);

        assertThat(snapshot.receiptNumber()).isEqualTo("REC-0001");
        assertThat(snapshot.clientDisplayName()).isEqualTo("Ana Martinez");
        assertThat(snapshot.promotionName()).isEqualTo("Intro");
        assertThat(snapshot.currency()).isEqualTo("USD");
        assertThat(snapshot.listPrice()).isEqualByComparingTo("30.00");
        assertThat(snapshot.amount()).isEqualByComparingTo("25.00");
    }

    @Test
    void rejectsInconsistentFinancialSnapshot() {
        assertThatThrownBy(() -> new PaymentReceiptSnapshot(
                "REC-0001", PAYMENT_ID, "PAY-0001", PaymentStatus.PAID,
                "CLI-0001", "Ana Martinez", "MEM-0001", "Premium", null, 1,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 10, 10),
                new BigDecimal("30.00"), new BigDecimal("5.00"), new BigDecimal("24.00"),
                "USD", PaymentMethod.CASH, PAID_AT, GENERATED_AT, ACTOR_ID, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("list price minus the discount");
    }

    @Test
    void rejectsInvalidDatesCurrencyAndSubCentValues() {
        assertThatThrownBy(() -> new PaymentReceiptSnapshot(
                "REC-0001", PAYMENT_ID, "PAY-0001", PaymentStatus.PAID,
                "CLI-0001", "Ana Martinez", "MEM-0001", "Premium", null, 1,
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 9, 11),
                new BigDecimal("30.00"), BigDecimal.ZERO, new BigDecimal("30.00"),
                "USD", PaymentMethod.CASH, PAID_AT, GENERATED_AT, ACTOR_ID, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end must not precede");

        assertThatThrownBy(() -> new PaymentReceiptSnapshot(
                "REC-0001", PAYMENT_ID, "PAY-0001", PaymentStatus.PAID,
                "CLI-0001", "Ana Martinez", "MEM-0001", "Premium", null, 1,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 10, 10),
                new BigDecimal("30.001"), BigDecimal.ZERO, new BigDecimal("30.001"),
                "USD", PaymentMethod.CASH, PAID_AT, GENERATED_AT, ACTOR_ID, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two decimal places");

        assertThatThrownBy(() -> new PaymentReceiptSnapshot(
                "REC-0001", PAYMENT_ID, "PAY-0001", PaymentStatus.PAID,
                "CLI-0001", "Ana Martinez", "MEM-0001", "Premium", null, 1,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 10, 10),
                new BigDecimal("30.00"), BigDecimal.ZERO, new BigDecimal("30.00"),
                "US", PaymentMethod.CASH, PAID_AT, GENERATED_AT, ACTOR_ID, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("three-letter");
    }
}
