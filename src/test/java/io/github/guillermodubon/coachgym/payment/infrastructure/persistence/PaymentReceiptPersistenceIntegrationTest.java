package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptOrganization;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptSourceSnapshot;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptDuplicateException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentReceiptPersistenceIntegrationTest extends AbstractIncidentApiIntegrationTest {

    @Autowired
    private JdbcPaymentReceiptAdapter adapter;

    @Autowired
    private JdbcPaymentReceiptSnapshotQuery snapshotQuery;

    @Test
    void insertsReadsAndFindsCanonicalReceiptByPayment() {
        UUID paymentId = insertPaidMembershipPayment();
        PaymentReceiptDetails details = details(paymentId, UUID.randomUUID(), "REC-PERSIST-001");

        PaymentReceiptDetails saved = adapter.save(details);

        assertThat(adapter.findById(saved.id())).contains(saved);
        assertThat(adapter.findByPaymentId(paymentId)).contains(saved);
        assertThat(saved.amount()).isEqualByComparingTo("25.00");
        assertThat(saved.currency()).isEqualTo("USD");
        assertThat(saved.snapshot().paymentCode()).isEqualTo(saved.paymentCode());
    }

    @Test
    void translatesConcurrentCanonicalDuplicateToSafeApplicationException() {
        UUID paymentId = insertPaidMembershipPayment();
        adapter.save(details(paymentId, UUID.randomUUID(), "REC-PERSIST-002"));

        assertThatThrownBy(() -> adapter.save(
                details(paymentId, UUID.randomUUID(), "REC-PERSIST-003")))
                .isInstanceOf(PaymentReceiptDuplicateException.class)
                .hasMessage("A canonical receipt already exists for this payment.");
    }

    @Test
    void loadsAuthoritativePaymentMembershipSnapshotAndOrganization() {
        UUID paymentId = insertPaidMembershipPayment();

        PaymentReceiptSourceSnapshot source = snapshotQuery.findByPaymentId(paymentId).orElseThrow();
        PaymentReceiptOrganization organization = snapshotQuery.findCurrent();

        assertThat(source.paymentId()).isEqualTo(paymentId);
        assertThat(source.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(source.clientDisplayName()).isEqualTo("Receipt Client");
        assertThat(source.membershipCode()).startsWith("MEM-");
        assertThat(source.planName()).isEqualTo("Receipt Premium");
        assertThat(source.amount()).isEqualByComparingTo("25.00");
        assertThat(source.currency()).isEqualTo("USD");
        assertThat(organization.displayName()).isEqualTo("Coach Gym");
        assertThat(organization.timeZone()).isEqualTo("America/El_Salvador");
    }

    @Test
    void readsReceiptOrganizationFromCanonicalOrganizationSource() {
        String previousBrandName = jdbcTemplate.queryForObject(
                "select brand_name from gym.organizations where is_canonical = true",
                String.class);
        try {
            jdbcTemplate.update(
                    "update gym.organizations set brand_name = ? where is_canonical = true",
                    "Canonical Receipt Brand");

            assertThat(snapshotQuery.findCurrent().displayName())
                    .isEqualTo("Canonical Receipt Brand");
        } finally {
            jdbcTemplate.update(
                    "update gym.organizations set brand_name = ? where is_canonical = true",
                    previousBrandName);
        }
    }

    private PaymentReceiptDetails details(UUID paymentId, UUID receiptId, String receiptNumber) {
        PaymentReceiptDocument document = PaymentReceiptDocument.fromPdfBytes(
                "%PDF-1.7\nreceipt".getBytes(StandardCharsets.US_ASCII));
        return new PaymentReceiptDetails(
                receiptId, receiptNumber, paymentId, paymentCode(paymentId), PaymentStatus.PAID,
                clientCode(paymentId), "Receipt Client", membershipCode(paymentId),
                "Receipt Premium", null, 1, LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 10, 10), new BigDecimal("25.00"),
                BigDecimal.ZERO, new BigDecimal("25.00"), "USD",
                PaymentMethod.CASH, paidAt(paymentId), paidAt(paymentId).plusSeconds(30),
                adminId, "Incident Staff", false, document.contentType(),
                document.sizeBytes(), document.checksumSha256(), "pdfbox-1", 0);
    }

    private UUID insertPaidMembershipPayment() {
        UUID clientId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.clients
                    (id, first_name, last_name, phone, status,
                     created_by_user_id, updated_by_user_id)
                values (?, 'Receipt', 'Client', '+50370004004', 'ACTIVE', ?, ?)
                """, clientId, adminId, adminId);

        UUID planId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.membership_plans
                    (id, name, duration_value, duration_unit, list_price, currency,
                     is_active, created_by_user_id, updated_by_user_id)
                values (?, 'Receipt Premium', 1, 'MONTH', 25.00, 'USD', true, ?, ?)
                """, planId, adminId, adminId);

        UUID membershipId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.memberships
                    (id, client_id, status, created_by_user_id, updated_by_user_id)
                values (?, ?, 'ACTIVE', ?, ?)
                """, membershipId, clientId, adminId, adminId);

        UUID periodId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.membership_periods
                    (id, membership_id, period_number, period_source,
                     membership_plan_id, plan_code_snapshot, plan_name_snapshot,
                     duration_value_snapshot, duration_unit_snapshot, list_price,
                     currency, discount_amount, final_price, starts_on, base_ends_on,
                     effective_ends_on, created_by_user_id, updated_by_user_id)
                values (?, ?, 1, 'INITIAL', ?, 'PLAN-RECEIPT', 'Receipt Premium',
                        1, 'MONTH', 25.00, 'USD', 0.00, 25.00,
                        '2026-09-11', '2026-10-10', '2026-10-10', ?, ?)
                """, periodId, membershipId, planId, adminId, adminId);

        UUID paymentId = UUID.randomUUID();
        OffsetDateTime paidAt = OffsetDateTime.of(
                2026, 9, 11, 14, 0, 0, 0, ZoneOffset.UTC);
        jdbcTemplate.update("""
                insert into gym.payments
                    (id, client_id, membership_id, membership_period_id,
                     amount, currency, payment_method, status, paid_at,
                     registered_by_user_id)
                values (?, ?, ?, ?, 25.00, 'USD', 'CASH', 'PAID', ?, ?)
                """, paymentId, clientId, membershipId, periodId, paidAt, adminId);
        return paymentId;
    }

    private String paymentCode(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "select payment_code from gym.payments where id = ?", String.class, paymentId);
    }

    private String clientCode(UUID paymentId) {
        return jdbcTemplate.queryForObject("""
                select c.client_code from gym.clients c
                join gym.payments p on p.client_id = c.id where p.id = ?
                """, String.class, paymentId);
    }

    private String membershipCode(UUID paymentId) {
        return jdbcTemplate.queryForObject("""
                select m.membership_code from gym.memberships m
                join gym.payments p on p.membership_id = m.id where p.id = ?
                """, String.class, paymentId);
    }

    private Instant paidAt(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "select paid_at from gym.payments where id = ?",
                (rs, row) -> rs.getObject("paid_at", OffsetDateTime.class).toInstant(),
                paymentId);
    }
}
