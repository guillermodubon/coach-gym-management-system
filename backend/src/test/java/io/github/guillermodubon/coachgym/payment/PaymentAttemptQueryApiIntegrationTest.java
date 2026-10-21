package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.guillermodubon.coachgym.payment.application.FailPaymentAttemptCommand;
import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptStore;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventApplicationService;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventProcessingResult;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventType;
import io.github.guillermodubon.coachgym.payment.application.PersistPaymentAttemptCommand;
import io.github.guillermodubon.coachgym.payment.application.ProcessPaymentAttemptCommand;
import io.github.guillermodubon.coachgym.payment.application.VerifiedPaymentProviderEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

@TestPropertySource(properties = "coach-gym.payment.stripe.enabled=false")
class PaymentAttemptQueryApiIntegrationTest extends AbstractPaymentApiIntegrationTest {

    private static final UUID PRINCIPAL_BRANCH = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000001");

    @Autowired
    private PaymentAttemptStore paymentAttemptStore;

    @Autowired
    private PaymentProviderEventApplicationService paymentProviderEventApplicationService;

    @Test
    void listsAllStatusesInStablePagesAndScopesRowsAndTotalsToActiveBranch() throws Exception {
        Fixture fixture = fixture();
        UUID actorId = userId(ADMIN_USERNAME);
        Instant base = Instant.parse("2026-10-19T12:00:00Z");
        UUID created = persistAttempt(fixture, actorId, PRINCIPAL_BRANCH, base);
        UUID expired = createProcessingAttempt(
                fixture, actorId, base.plusSeconds(1), "checkout-expired-private");
        UUID cancelled = createProcessingAttempt(
                fixture, actorId, base.plusSeconds(2), "checkout-cancelled-private");
        UUID succeeded = createProcessingAttempt(
                fixture, actorId, base.plusSeconds(3), "checkout-succeeded-private");
        UUID processing = createProcessingAttempt(
                fixture, actorId, base.plusSeconds(4), "checkout-processing-private");
        UUID failed = persistAttempt(fixture, actorId, PRINCIPAL_BRANCH, base.plusSeconds(5));
        paymentAttemptStore.markFailed(new FailPaymentAttemptCommand(
                failed, 0, PaymentAttemptFailureCode.PROVIDER_UNAVAILABLE,
                actorId, base.plusSeconds(7)));

        MockHttpSession receptionist = loginAsReceptionist();
        String membershipId = fixture.membershipId().toString();
        String periodId = fixture.periodId().toString();
        mockMvc.perform(get("/api/v1/payment-attempts")
                        .session(receptionist)
                        .param("membershipId", membershipId)
                        .param("membershipPeriodId", periodId)
                        .param("clientId", fixture.clientId().toString())
                        .param("status", "PROCESSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));

        assertThat(processProviderEvent(
                succeeded, "evt-query-success-" + UUID.randomUUID(),
                "checkout-succeeded-private", PaymentProviderEventType.CHECKOUT_COMPLETED,
                new BigDecimal("25.00"), "USD", base.plusSeconds(6)))
                .isEqualTo(PaymentProviderEventProcessingResult.PROCESSED);
        assertThat(processProviderEvent(
                cancelled, "evt-query-cancel-" + UUID.randomUUID(),
                "checkout-cancelled-private", PaymentProviderEventType.CHECKOUT_CANCELLED,
                null, null, base.plusSeconds(6)))
                .isEqualTo(PaymentProviderEventProcessingResult.PROCESSED);
        assertThat(processProviderEvent(
                expired, "evt-query-expire-" + UUID.randomUUID(),
                "checkout-expired-private", PaymentProviderEventType.CHECKOUT_EXPIRED,
                null, null, base.plusSeconds(6)))
                .isEqualTo(PaymentProviderEventProcessingResult.PROCESSED);

        UUID historicalBranch = createInactiveBranch();
        persistAttempt(fixture, actorId, historicalBranch, base.plusSeconds(5));

        int historyCountBefore = jdbcTemplate.queryForObject(
                "select count(*) from gym.payment_attempt_status_history", Integer.class);

        MvcResult firstPage = mockMvc.perform(get("/api/v1/payment-attempts")
                        .session(receptionist)
                        .param("membershipId", membershipId)
                        .param("membershipPeriodId", periodId)
                        .param("page", "0")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.items[1].status").value("PROCESSING"))
                .andExpect(jsonPath("$.items[2].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].checkoutReference").doesNotExist())
                .andExpect(jsonPath("$.items[0].checkoutUrl").doesNotExist())
                .andReturn();
        List<String> firstIds = JsonPath.read(
                firstPage.getResponse().getContentAsString(), "$.items[*].id");
        assertThat(firstIds).containsExactly(
                failed.toString(), processing.toString(), succeeded.toString());

        mockMvc.perform(get("/api/v1/payment-attempts")
                        .session(receptionist)
                        .param("membershipId", membershipId)
                        .param("membershipPeriodId", periodId)
                        .param("page", "1")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.items[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.items[1].status").value("EXPIRED"))
                .andExpect(jsonPath("$.items[2].id").value(created.toString()))
                .andExpect(jsonPath("$.items[2].status").value("CREATED"));

        mockMvc.perform(get("/api/v1/payment-attempts")
                        .session(receptionist)
                        .param("membershipId", membershipId)
                        .param("membershipPeriodId", periodId)
                        .param("clientId", fixture.clientId().toString())
                        .param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(failed.toString()));

        mockMvc.perform(get("/api/v1/payment-attempts")
                        .session(receptionist)
                        .param("membershipId", UUID.randomUUID().toString())
                        .param("membershipPeriodId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.payment_attempt_status_history", Integer.class))
                .isEqualTo(historyCountBefore);
    }

    @Test
    void requiresBothMembershipFiltersAndRejectsMalformedAndUnboundedParameters()
            throws Exception {
        Fixture fixture = fixture();
        MockHttpSession receptionist = loginAsReceptionist();

        mockMvc.perform(get("/api/v1/payment-attempts")
                        .session(receptionist)
                        .param("membershipId", fixture.membershipId().toString()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/payment-attempts")
                        .session(receptionist)
                        .param("membershipId", "not-a-uuid")
                        .param("membershipPeriodId", fixture.periodId().toString()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/payment-attempts")
                        .session(receptionist)
                        .param("membershipId", fixture.membershipId().toString())
                        .param("membershipPeriodId", fixture.periodId().toString())
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));

        mockMvc.perform(get("/api/v1/payment-attempts")
                        .session(receptionist)
                        .param("membershipId", fixture.membershipId().toString())
                        .param("membershipPeriodId", fixture.periodId().toString())
                        .param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void collectionRequiresAuthenticationAndDoesNotRequireCsrfForRead() throws Exception {
        mockMvc.perform(get("/api/v1/payment-attempts")
                        .param("membershipId", UUID.randomUUID().toString())
                        .param("membershipPeriodId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());

        Fixture fixture = fixture();
        mockMvc.perform(get("/api/v1/payment-attempts")
                        .session(loginAsReceptionist())
                        .param("membershipId", fixture.membershipId().toString())
                        .param("membershipPeriodId", fixture.periodId().toString()))
                .andExpect(status().isOk());
    }

    private Fixture fixture() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        UUID clientId = createClient(admin, uniqueEmail());
        UUID planId = createPlan(admin, uniqueName("Attempt plan"), "25.00", "USD");
        UUID membershipId = createMembership(admin, clientId, planId, null, "2026-10-01");
        return new Fixture(clientId, membershipId, getMembershipPeriodId(membershipId));
    }

    private UUID persistAttempt(
            Fixture fixture,
            UUID actorId,
            UUID branchId,
            Instant createdAt) {
        UUID attemptId = UUID.randomUUID();
        paymentAttemptStore.create(new PersistPaymentAttemptCommand(
                attemptId,
                fixture.clientId(),
                fixture.membershipId(),
                fixture.periodId(),
                PaymentProvider.STRIPE,
                new BigDecimal("25.00"),
                "USD",
                actorId,
                createdAt,
                branchId));
        return attemptId;
    }

    private UUID createProcessingAttempt(
            Fixture fixture,
            UUID actorId,
            Instant createdAt,
            String checkoutReference) {
        UUID attemptId = persistAttempt(fixture, actorId, PRINCIPAL_BRANCH, createdAt);
        paymentAttemptStore.markProcessing(new ProcessPaymentAttemptCommand(
                attemptId, 0, checkoutReference, createdAt.plusSeconds(900),
                actorId, createdAt.plusSeconds(1)));
        return attemptId;
    }

    private PaymentProviderEventProcessingResult processProviderEvent(
            UUID attemptId,
            String eventReference,
            String checkoutReference,
            PaymentProviderEventType eventType,
            BigDecimal amount,
            String currency,
            Instant occurredAt) {
        String providerPaymentReference = eventType == PaymentProviderEventType.CHECKOUT_COMPLETED
                ? "payment-" + attemptId
                : null;
        return paymentProviderEventApplicationService.process(new VerifiedPaymentProviderEvent(
                PaymentProvider.STRIPE,
                eventReference,
                eventType,
                attemptId,
                checkoutReference,
                providerPaymentReference,
                amount,
                currency,
                occurredAt));
    }

    private UUID createInactiveBranch() {
        UUID branchId = UUID.randomUUID();
        String code = "TEST_" + branchId.toString().replace("-", "")
                .substring(0, 16).toUpperCase(java.util.Locale.ROOT);
        jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, country_code, timezone,
                     status, is_initial_branch, version)
                values (?, ?, ?, 'Historical payment-test branch', 'SV',
                        'America/El_Salvador', 'INACTIVE', false, 0)
                """, branchId, ORGANIZATION_ID, code);
        return branchId;
    }

    private UUID userId(String username) {
        return jdbcTemplate.queryForObject(
                "select id from gym.users where username = ?", UUID.class, username);
    }

    private record Fixture(UUID clientId, UUID membershipId, UUID periodId) {
    }
}
