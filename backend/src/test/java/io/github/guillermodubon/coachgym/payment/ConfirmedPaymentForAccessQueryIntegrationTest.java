package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptStore;
import io.github.guillermodubon.coachgym.payment.application.PersistPaymentAttemptCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

/** PostgreSQL contract tests for the payment-owned access query. */
class ConfirmedPaymentForAccessQueryIntegrationTest
        extends AbstractPaymentCorrectionApiIntegrationTest {

    private static final Instant PAYMENT_ATTEMPT_TIME =
            Instant.parse("2026-09-10T16:00:00Z");

    @Autowired
    private ConfirmedPaymentForAccessQuery confirmedPaymentQuery;

    @Autowired
    private PaymentAttemptStore paymentAttemptStore;

    @Test
    void exactPaidPaymentSatisfiesTheRequirement() throws Exception {
        PaymentFixture fixture = createPaidPayment(
                loginAsAdmin(), "CASH", null);

        assertThat(confirmedPaymentQuery.hasConfirmedPaymentForPeriod(
                fixture.clientId(), fixture.membershipId(), fixture.periodId()))
                .isTrue();
    }

    @Test
    void noPaymentDoesNotSatisfyTheRequirement() throws Exception {
        MembershipFixture fixture = createMembershipFixture(loginAsAdmin());

        assertThat(confirmedPaymentQuery.hasConfirmedPaymentForPeriod(
                fixture.clientId(), fixture.membershipId(), fixture.periodId()))
                .isFalse();
    }

    @Test
    void paymentAttemptAloneDoesNotSatisfyTheRequirement() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        MembershipFixture fixture = createMembershipFixture(admin);
        UUID actorId = jdbcTemplate.queryForObject(
                "select id from gym.users where username = ?",
                UUID.class,
                ADMIN_USERNAME);

        paymentAttemptStore.create(new PersistPaymentAttemptCommand(
                UUID.randomUUID(),
                fixture.clientId(),
                fixture.membershipId(),
                fixture.periodId(),
                PaymentProvider.STRIPE,
                new BigDecimal("25.00"),
                "USD",
                actorId,
                PAYMENT_ATTEMPT_TIME));

        assertThat(confirmedPaymentQuery.hasConfirmedPaymentForPeriod(
                fixture.clientId(), fixture.membershipId(), fixture.periodId()))
                .isFalse();
    }

    @Test
    void voidedPaymentDoesNotSatisfyTheRequirement() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        PaymentFixture fixture = createPaidPayment(admin, "CASH", null);

        mockMvc.perform(voidPayment(
                        admin,
                        fixture.paymentId(),
                        "Duplicate payment",
                        fixture.version()))
                .andExpect(status().isOk());

        assertThat(confirmedPaymentQuery.hasConfirmedPaymentForPeriod(
                fixture.clientId(), fixture.membershipId(), fixture.periodId()))
                .isFalse();
    }

    @Test
    void refundedPaymentDoesNotSatisfyTheRequirement() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        PaymentFixture fixture = createPaidPayment(admin, "CASH", null);

        mockMvc.perform(refundPayment(
                        admin,
                        fixture.paymentId(),
                        "Approved refund",
                        null,
                        fixture.version()))
                .andExpect(status().isOk());

        assertThat(confirmedPaymentQuery.hasConfirmedPaymentForPeriod(
                fixture.clientId(), fixture.membershipId(), fixture.periodId()))
                .isFalse();
    }

    @Test
    void differentClientMembershipOrPeriodDoesNotSatisfyTheRequirement()
            throws Exception {
        PaymentFixture fixture = createPaidPayment(
                loginAsAdmin(), "CASH", null);

        assertThat(confirmedPaymentQuery.hasConfirmedPaymentForPeriod(
                UUID.randomUUID(), fixture.membershipId(), fixture.periodId()))
                .isFalse();
        assertThat(confirmedPaymentQuery.hasConfirmedPaymentForPeriod(
                fixture.clientId(), UUID.randomUUID(), fixture.periodId()))
                .isFalse();
        assertThat(confirmedPaymentQuery.hasConfirmedPaymentForPeriod(
                fixture.clientId(), fixture.membershipId(), UUID.randomUUID()))
                .isFalse();
    }

    @Test
    void multiplePaidRowsStillProduceAStableExistenceResult() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        PaymentFixture fixture = createPaidPayment(admin, "CASH", null);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/payments")
                                .with(org.springframework.security.test.web.servlet.request
                                        .SecurityMockMvcRequestPostProcessors.csrf())
                                .session(admin)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(PaymentRegistrationApiIntegrationTest.paymentBody(
                                        fixture.clientId(),
                                        fixture.membershipId(),
                                        fixture.periodId(),
                                        "25.00",
                                        "USD",
                                        "CASH",
                                        null,
                                        "2026-08-26T12:00:00Z")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isCreated());

        assertThat(countPaymentRows(fixture.membershipId())).isEqualTo(2);
        assertThat(confirmedPaymentQuery.hasConfirmedPaymentForPeriod(
                fixture.clientId(), fixture.membershipId(), fixture.periodId()))
                .isTrue();
    }

    @Test
    void existingMembershipPeriodIndexCoversTheExistenceLookup() {
        String indexDefinition = jdbcTemplate.queryForObject(
                """
                select indexdef
                from pg_indexes
                where schemaname = 'gym'
                  and tablename = 'payments'
                  and indexname = 'idx_payments_membership_period_id'
                """,
                String.class);

        assertThat(indexDefinition)
                .containsIgnoringCase("membership_period_id")
                .containsIgnoringCase("gym.payments");
    }

    private MembershipFixture createMembershipFixture(MockHttpSession session)
            throws Exception {
        UUID clientId = createClient(session, uniqueEmail());
        UUID planId = createPlan(session, uniqueName("Query-Plan"), "25.00", "USD");
        UUID membershipId = createMembership(
                session, clientId, planId, null, "2026-09-01");
        return new MembershipFixture(
                clientId, membershipId, getMembershipPeriodId(membershipId));
    }

    private record MembershipFixture(
            UUID clientId,
            UUID membershipId,
            UUID periodId) {
    }
}
