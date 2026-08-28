package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.membership.MembershipPaymentDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPaymentPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPaymentQuery;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentRegistered;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.domain.PaymentAmountMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentCurrencyMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentMembershipMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentMembershipStateConflictException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentValidationException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class PaymentApplicationServiceTest {

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    private static final UUID PAYMENT_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");

    private static final UUID OTHER_MEMBERSHIP_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000099");

    private static final UUID OTHER_CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000099");

    private static final BigDecimal AMOUNT = new BigDecimal("25.00");
    private static final String CURRENCY = "USD";

    private static final Instant NOW =
            Instant.parse("2026-08-25T18:35:00Z");

    private static final Instant PAID_AT =
            Instant.parse("2026-08-25T18:30:00Z");

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "coach-admin");

    private PaymentStore paymentStore;
    private MembershipPaymentQuery membershipPaymentQuery;
    private ApplicationEventPublisher eventPublisher;
    private PaymentApplicationService service;

    @BeforeEach
    void setUp() {
        paymentStore = mock(PaymentStore.class);
        membershipPaymentQuery = mock(MembershipPaymentQuery.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        service = new PaymentApplicationService(
                paymentStore, membershipPaymentQuery, eventPublisher, clock);
    }

    // ------------------------------------------------------------------
    // register — happy path
    // ------------------------------------------------------------------

    @Test
    void registersPaymentSuccessfully() {
        givenActiveMembershipAndPeriod();
        given(paymentStore.existsByMethodAndExternalReference(any(), any()))
                .willReturn(false);
        PaymentDetails stored = paymentDetails();
        given(paymentStore.register(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any()))
                .willReturn(stored);

        PaymentDetails result = service.register(validCommand(), ACTOR);

        assertThat(result.id()).isEqualTo(PAYMENT_ID);
        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.amount()).isEqualByComparingTo("25.00");
    }

    @Test
    void publishesPaymentRegisteredEventAfterPersistence() {
        givenActiveMembershipAndPeriod();
        given(paymentStore.existsByMethodAndExternalReference(any(), any()))
                .willReturn(false);
        given(paymentStore.register(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any()))
                .willReturn(paymentDetails());

        service.register(validCommand(), ACTOR);

        ArgumentCaptor<PaymentRegistered> captor =
                ArgumentCaptor.forClass(PaymentRegistered.class);
        verify(eventPublisher).publishEvent(captor.capture());

        PaymentRegistered event = captor.getValue();
        assertThat(event.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(event.clientId()).isEqualTo(CLIENT_ID);
        assertThat(event.membershipId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(event.membershipPeriodId()).isEqualTo(PERIOD_ID);
        assertThat(event.amount()).isEqualByComparingTo("25.00");
        assertThat(event.currency()).isEqualTo("USD");
        assertThat(event.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(event.resultingStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(event.actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(event.actorIdentifier()).isEqualTo("coach-admin");
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.hasExternalReference()).isFalse();
    }

    @Test
    void hasExternalReferenceTrueWhenReferencePresent() {
        givenActiveMembershipAndPeriod();
        given(paymentStore.existsByMethodAndExternalReference(
                eq(PaymentMethod.CARD), eq("REF-001")))
                .willReturn(false);
        PaymentDetails withRef = new PaymentDetails(
                PAYMENT_ID, "PAY-000001", CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CARD, PaymentStatus.PAID,
                "REF-001", PAID_AT, ACTOR_ID, NOW, NOW, 0L);
        given(paymentStore.register(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any()))
                .willReturn(withRef);

        RegisterPaymentCommand cmd = new RegisterPaymentCommand(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CARD, "REF-001", PAID_AT);

        service.register(cmd, ACTOR);

        ArgumentCaptor<PaymentRegistered> captor =
                ArgumentCaptor.forClass(PaymentRegistered.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().hasExternalReference()).isTrue();
    }

    @Test
    void allowsPaymentForFrozenMembership() {
        givenMembershipWithStatus(MembershipStatus.FROZEN);
        givenPeriodUnderMembership();
        given(paymentStore.existsByMethodAndExternalReference(any(), any()))
                .willReturn(false);
        given(paymentStore.register(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any()))
                .willReturn(paymentDetails());

        PaymentDetails result = service.register(validCommand(), ACTOR);

        assertThat(result).isNotNull();
    }

    @Test
    void allowsPaymentForExpiredMembership() {
        givenMembershipWithStatus(MembershipStatus.EXPIRED);
        givenPeriodUnderMembership();
        given(paymentStore.existsByMethodAndExternalReference(any(), any()))
                .willReturn(false);
        given(paymentStore.register(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any()))
                .willReturn(paymentDetails());

        PaymentDetails result = service.register(validCommand(), ACTOR);

        assertThat(result).isNotNull();
    }

    // ------------------------------------------------------------------
    // register — 404 cases
    // ------------------------------------------------------------------

    @Test
    void throwsMembershipNotFoundWhenMembershipAbsent() {
        given(membershipPaymentQuery.findMembershipForPayment(MEMBERSHIP_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(validCommand(), ACTOR))
                .isInstanceOf(PaymentMembershipNotFoundException.class)
                .hasMessageContaining(MEMBERSHIP_ID.toString());

        verify(paymentStore, never()).register(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void throwsPeriodNotFoundWhenPeriodAbsent() {
        given(membershipPaymentQuery.findMembershipForPayment(MEMBERSHIP_ID))
                .willReturn(Optional.of(membershipDetails(MembershipStatus.ACTIVE)));
        given(membershipPaymentQuery.findPeriodForPayment(PERIOD_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(validCommand(), ACTOR))
                .isInstanceOf(PaymentPeriodNotFoundException.class)
                .hasMessageContaining(PERIOD_ID.toString());

        verify(paymentStore, never()).register(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ------------------------------------------------------------------
    // register — mismatch / conflict cases
    // ------------------------------------------------------------------

    @Test
    void throwsPeriodMismatchWhenPeriodBelongsToAnotherMembership() {
        given(membershipPaymentQuery.findMembershipForPayment(MEMBERSHIP_ID))
                .willReturn(Optional.of(membershipDetails(MembershipStatus.ACTIVE)));
        // Period exists but its membershipId points elsewhere
        given(membershipPaymentQuery.findPeriodForPayment(PERIOD_ID))
                .willReturn(Optional.of(new MembershipPaymentPeriodDetails(
                        PERIOD_ID, OTHER_MEMBERSHIP_ID, AMOUNT, CURRENCY)));

        assertThatThrownBy(() -> service.register(validCommand(), ACTOR))
                .isInstanceOf(PaymentPeriodMismatchException.class)
                .hasMessageContaining(PERIOD_ID.toString())
                .hasMessageContaining(MEMBERSHIP_ID.toString());

        verify(paymentStore, never()).register(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void throwsMembershipMismatchWhenClientDoesNotOwnMembership() {
        given(membershipPaymentQuery.findMembershipForPayment(MEMBERSHIP_ID))
                .willReturn(Optional.of(new MembershipPaymentDetails(
                        MEMBERSHIP_ID, OTHER_CLIENT_ID, MembershipStatus.ACTIVE)));
        givenPeriodUnderMembership();

        assertThatThrownBy(() -> service.register(validCommand(), ACTOR))
                .isInstanceOf(PaymentMembershipMismatchException.class);

        verify(paymentStore, never()).register(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void throwsStateConflictForCancelledMembership() {
        givenMembershipWithStatus(MembershipStatus.CANCELLED);
        givenPeriodUnderMembership();

        assertThatThrownBy(() -> service.register(validCommand(), ACTOR))
                .isInstanceOf(PaymentMembershipStateConflictException.class)
                .hasMessageContaining("CANCELLED");

        verify(paymentStore, never()).register(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void throwsAmountMismatchWhenAmountDiffersFromFinalPrice() {
        given(membershipPaymentQuery.findMembershipForPayment(MEMBERSHIP_ID))
                .willReturn(Optional.of(membershipDetails(MembershipStatus.ACTIVE)));
        // Period finalPrice is 30.00, command amount is 25.00
        given(membershipPaymentQuery.findPeriodForPayment(PERIOD_ID))
                .willReturn(Optional.of(new MembershipPaymentPeriodDetails(
                        PERIOD_ID, MEMBERSHIP_ID, new BigDecimal("30.00"), CURRENCY)));

        assertThatThrownBy(() -> service.register(validCommand(), ACTOR))
                .isInstanceOf(PaymentAmountMismatchException.class);

        verify(paymentStore, never()).register(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void throwsCurrencyMismatchWhenCurrencyDiffers() {
        given(membershipPaymentQuery.findMembershipForPayment(MEMBERSHIP_ID))
                .willReturn(Optional.of(membershipDetails(MembershipStatus.ACTIVE)));
        given(membershipPaymentQuery.findPeriodForPayment(PERIOD_ID))
                .willReturn(Optional.of(new MembershipPaymentPeriodDetails(
                        PERIOD_ID, MEMBERSHIP_ID, AMOUNT, "EUR")));

        assertThatThrownBy(() -> service.register(validCommand(), ACTOR))
                .isInstanceOf(PaymentCurrencyMismatchException.class);

        verify(paymentStore, never()).register(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void throwsValidationExceptionWhenPaidAtIsInTheFuture() {
        givenActiveMembershipAndPeriod();
        Instant futurePaidAt = NOW.plusSeconds(60);
        RegisterPaymentCommand cmd = new RegisterPaymentCommand(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CASH, null, futurePaidAt);

        assertThatThrownBy(() -> service.register(cmd, ACTOR))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("future");

        verify(paymentStore, never()).register(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void throwsDuplicateReferenceWhenReferenceAlreadyUsed() {
        givenActiveMembershipAndPeriod();
        given(paymentStore.existsByMethodAndExternalReference(
                PaymentMethod.CARD, "REF-001"))
                .willReturn(true);

        RegisterPaymentCommand cmd = new RegisterPaymentCommand(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CARD, "REF-001", PAID_AT);

        assertThatThrownBy(() -> service.register(cmd, ACTOR))
                .isInstanceOf(DuplicatePaymentReferenceException.class);

        verify(paymentStore, never()).register(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void doesNotCheckDuplicateWhenExternalReferenceIsNull() {
        givenActiveMembershipAndPeriod();
        given(paymentStore.register(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any()))
                .willReturn(paymentDetails());

        service.register(validCommand(), ACTOR); // no reference

        verify(paymentStore, never())
                .existsByMethodAndExternalReference(any(), any());
    }

    // ------------------------------------------------------------------
    // register — command / actor guard cases (no side effects)
    // ------------------------------------------------------------------

    @Test
    void throwsValidationExceptionForNullCommand() {
        assertThatThrownBy(() -> service.register(null, ACTOR))
                .isInstanceOf(PaymentValidationException.class);

        verify(paymentStore, never()).register(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void throwsValidationExceptionForNullActor() {
        assertThatThrownBy(() -> service.register(validCommand(), null))
                .isInstanceOf(PaymentValidationException.class);

        verify(paymentStore, never()).register(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // findById
    // ------------------------------------------------------------------

    @Test
    void returnsPaymentWhenFound() {
        given(paymentStore.findById(PAYMENT_ID))
                .willReturn(Optional.of(paymentDetails()));

        PaymentDetails result = service.findById(PAYMENT_ID);

        assertThat(result.id()).isEqualTo(PAYMENT_ID);
    }

    @Test
    void throwsPaymentNotFoundWhenAbsent() {
        given(paymentStore.findById(PAYMENT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(PAYMENT_ID))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(PAYMENT_ID.toString());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void givenActiveMembershipAndPeriod() {
        givenMembershipWithStatus(MembershipStatus.ACTIVE);
        givenPeriodUnderMembership();
    }

    private void givenMembershipWithStatus(MembershipStatus status) {
        given(membershipPaymentQuery.findMembershipForPayment(MEMBERSHIP_ID))
                .willReturn(Optional.of(membershipDetails(status)));
    }

    private void givenPeriodUnderMembership() {
        given(membershipPaymentQuery.findPeriodForPayment(PERIOD_ID))
                .willReturn(Optional.of(new MembershipPaymentPeriodDetails(
                        PERIOD_ID, MEMBERSHIP_ID, AMOUNT, CURRENCY)));
    }

    private static MembershipPaymentDetails membershipDetails(
            MembershipStatus status) {

        return new MembershipPaymentDetails(
                MEMBERSHIP_ID, CLIENT_ID, status);
    }

    private static RegisterPaymentCommand validCommand() {
        return new RegisterPaymentCommand(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CASH, null, PAID_AT);
    }

    private static PaymentDetails paymentDetails() {
        return new PaymentDetails(
                PAYMENT_ID, "PAY-000001", CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                AMOUNT, CURRENCY, PaymentMethod.CASH, PaymentStatus.PAID,
                null, PAID_AT, ACTOR_ID, NOW, NOW, 0L);
    }
}
