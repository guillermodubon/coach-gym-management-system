package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.github.guillermodubon.coachgym.membership.MembershipPaymentDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPaymentPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.domain.MembershipCreation;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPeriodDates;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPricingSnapshot;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MembershipPaymentQueryAdapterTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    private static final UUID OTHER_MEMBERSHIP_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000099");

    private static final UUID ACTOR_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");

    private static final UUID PLAN_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");

    private static final Instant NOW =
            Instant.parse("2026-08-25T18:00:00Z");

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "coach-admin");

    private MembershipJpaRepository membershipRepository;
    private MembershipPeriodJpaRepository periodRepository;
    private MembershipPaymentQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        membershipRepository = mock(MembershipJpaRepository.class);
        periodRepository = mock(MembershipPeriodJpaRepository.class);
        adapter = new MembershipPaymentQueryAdapter(
                membershipRepository, periodRepository);
    }

    // ------------------------------------------------------------------
    // findMembershipForPayment
    // ------------------------------------------------------------------

    @Test
    void returnsMembershipDetailsWhenActive() {
        MembershipJpaEntity entity =
                MembershipJpaEntity.create(CLIENT_ID, ACTOR, NOW);

        given(membershipRepository.findById(MEMBERSHIP_ID))
                .willReturn(Optional.of(entity));

        Optional<MembershipPaymentDetails> result =
                adapter.findMembershipForPayment(MEMBERSHIP_ID);

        assertThat(result).isPresent();
        MembershipPaymentDetails details = result.get();
        assertThat(details.clientId()).isEqualTo(CLIENT_ID);
        assertThat(details.status()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void returnsMembershipDetailsForFrozenMembership() {
        MembershipJpaEntity entity =
                MembershipJpaEntity.create(CLIENT_ID, ACTOR, NOW);

        entity.changeStatus(
                MembershipStatus.ACTIVE,
                MembershipStatus.FROZEN,
                ACTOR, NOW);

        given(membershipRepository.findById(MEMBERSHIP_ID))
                .willReturn(Optional.of(entity));

        Optional<MembershipPaymentDetails> result =
                adapter.findMembershipForPayment(MEMBERSHIP_ID);

        assertThat(result).isPresent();
        assertThat(result.get().status())
                .isEqualTo(MembershipStatus.FROZEN);
    }

    @Test
    void returnsMembershipDetailsForCancelledMembership() {
        // Adapter returns status regardless; policy rejects CANCELLED.
        MembershipJpaEntity entity =
                MembershipJpaEntity.create(CLIENT_ID, ACTOR, NOW);

        entity.cancel(
                MembershipStatus.ACTIVE,
                LocalDate.of(2026, 8, 25),
                "Test cancellation",
                ACTOR, NOW);

        given(membershipRepository.findById(MEMBERSHIP_ID))
                .willReturn(Optional.of(entity));

        Optional<MembershipPaymentDetails> result =
                adapter.findMembershipForPayment(MEMBERSHIP_ID);

        assertThat(result).isPresent();
        assertThat(result.get().status())
                .isEqualTo(MembershipStatus.CANCELLED);
    }

    @Test
    void returnsEmptyWhenMembershipNotFound() {
        given(membershipRepository.findById(MEMBERSHIP_ID))
                .willReturn(Optional.empty());

        Optional<MembershipPaymentDetails> result =
                adapter.findMembershipForPayment(MEMBERSHIP_ID);

        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------
    // findPeriodForPayment
    // ------------------------------------------------------------------

    @Test
    void returnsPeriodDetailsWithMembershipId() {
        MembershipPeriodJpaEntity entity =
                periodEntity(MEMBERSHIP_ID, new BigDecimal("25.00"), "USD");

        given(periodRepository.findById(PERIOD_ID))
                .willReturn(Optional.of(entity));

        Optional<MembershipPaymentPeriodDetails> result =
                adapter.findPeriodForPayment(PERIOD_ID);

        assertThat(result).isPresent();
        MembershipPaymentPeriodDetails details = result.get();
        assertThat(details.membershipId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(details.finalPrice()).isEqualByComparingTo("25.00");
        assertThat(details.currency()).isEqualTo("USD");
    }

    @Test
    void returnsEmptyWhenPeriodNotFound() {
        given(periodRepository.findById(PERIOD_ID))
                .willReturn(Optional.empty());

        Optional<MembershipPaymentPeriodDetails> result =
                adapter.findPeriodForPayment(PERIOD_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void exposesMembershipIdSoApplicationLayerCanDetectMismatch() {
        // Period belongs to OTHER_MEMBERSHIP_ID; adapter returns it.
        // Application layer checks period.membershipId() != command.membershipId()
        // and throws PAYMENT_PERIOD_MISMATCH — not the adapter's job.
        MembershipPeriodJpaEntity entity =
                periodEntity(OTHER_MEMBERSHIP_ID, new BigDecimal("25.00"), "USD");

        given(periodRepository.findById(PERIOD_ID))
                .willReturn(Optional.of(entity));

        Optional<MembershipPaymentPeriodDetails> result =
                adapter.findPeriodForPayment(PERIOD_ID);

        assertThat(result).isPresent();
        assertThat(result.get().membershipId()).isEqualTo(OTHER_MEMBERSHIP_ID);
    }

    @Test
    void trimsCurrencyWhitespaceFromStoredCharColumn() {
        MembershipPeriodJpaEntity entity =
                periodEntity(MEMBERSHIP_ID, new BigDecimal("25.00"), "USD");

        given(periodRepository.findById(PERIOD_ID))
                .willReturn(Optional.of(entity));

        Optional<MembershipPaymentPeriodDetails> result =
                adapter.findPeriodForPayment(PERIOD_ID);

        assertThat(result).isPresent();
        assertThat(result.get().currency()).doesNotContainAnyWhitespaces();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static MembershipPeriodJpaEntity periodEntity(
            UUID membershipId,
            BigDecimal finalPrice,
            String currency) {

        MembershipPricingSnapshot pricing =
                MembershipPricingSnapshot.withoutPromotion(
                        PLAN_ID,
                        "PLAN-000001",
                        "Monthly Access",
                        1,
                        DurationUnit.MONTH,
                        finalPrice,
                        currency);

        MembershipPeriodDates dates =
                new MembershipPeriodDates(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 1));

        MembershipCreation creation =
                new MembershipCreation(CLIENT_ID, dates, pricing);

        return MembershipPeriodJpaEntity.initial(
                membershipId, creation, ACTOR, NOW);
    }
}
