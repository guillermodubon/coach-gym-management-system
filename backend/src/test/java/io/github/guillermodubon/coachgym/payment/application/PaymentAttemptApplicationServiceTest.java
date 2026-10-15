package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.membership.MembershipPaymentDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPaymentPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPaymentQuery;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.domain.PaymentMembershipMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentMembershipStateConflictException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentAttemptStateConflictException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

class PaymentAttemptApplicationServiceTest {

    private static final UUID CLIENT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID MEMBERSHIP_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");
    private static final UUID PERIOD_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001");
    private static final UUID ATTEMPT_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-10T18:00:00Z");
    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "coach-admin");

    private PaymentAttemptStore store;
    private MembershipPaymentQuery membershipQuery;
    private ObjectProvider<CardCheckoutGateway> gatewayProvider;
    private ObjectProvider<CheckoutRedirectPolicy> redirectProvider;
    private ApplicationEventPublisher eventPublisher;
    private PaymentAttemptApplicationService service;

    @BeforeEach
    void setUp() {
        store = mock(PaymentAttemptStore.class);
        membershipQuery = mock(MembershipPaymentQuery.class);
        gatewayProvider = mock(ObjectProvider.class);
        redirectProvider = mock(ObjectProvider.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new PaymentAttemptApplicationService(
                store, membershipQuery, gatewayProvider, redirectProvider,
                eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void derivesAmountAndRedirectTargetsAndPersistsBeforeProviderCall() {
        givenMembershipAndPeriod();
        PaymentAttemptDetails created = attempt(PaymentAttemptStatus.CREATED, 0);
        PaymentAttemptDetails processing = attempt(PaymentAttemptStatus.PROCESSING, 1);
        CardCheckoutGateway gateway = mock(CardCheckoutGateway.class);
        CheckoutRedirectPolicy redirects = mock(CheckoutRedirectPolicy.class);
        ProviderCheckout checkout = new ProviderCheckout(
                PaymentProvider.STRIPE, "cs_test_1",
                URI.create("https://checkout.test/session"), NOW.plusSeconds(900));
        given(store.create(any(PersistPaymentAttemptCommand.class))).willReturn(created);
        given(gatewayProvider.getIfAvailable()).willReturn(gateway);
        given(redirectProvider.getIfAvailable()).willReturn(redirects);
        given(redirects.successUrl(PaymentProvider.STRIPE))
                .willReturn(URI.create("https://gym.test/success"));
        given(redirects.cancelUrl(PaymentProvider.STRIPE))
                .willReturn(URI.create("https://gym.test/cancel"));
        given(gateway.createCheckout(any(ProviderCheckoutRequest.class))).willReturn(checkout);
        given(store.markProcessing(any(ProcessPaymentAttemptCommand.class)))
                .willReturn(processing);

        PaymentAttemptCheckoutDetails result = service.createCheckout(
                new CreateCardCheckoutAttemptCommand(CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID), ACTOR);

        assertThat(result.attempt().status()).isEqualTo(PaymentAttemptStatus.PROCESSING);
        assertThat(result.checkoutUrl()).isEqualTo(checkout.checkoutUrl());
        ArgumentCaptor<PersistPaymentAttemptCommand> persisted =
                ArgumentCaptor.forClass(PersistPaymentAttemptCommand.class);
        verify(store).create(persisted.capture());
        assertThat(persisted.getValue().expectedAmount()).isEqualByComparingTo("25.00");
        assertThat(persisted.getValue().currency()).isEqualTo("USD");
        ArgumentCaptor<ProviderCheckoutRequest> request =
                ArgumentCaptor.forClass(ProviderCheckoutRequest.class);
        verify(gateway).createCheckout(request.capture());
        assertThat(request.getValue().amount()).isEqualByComparingTo("25.00");
        assertThat(request.getValue().successUrl()).isEqualTo(URI.create("https://gym.test/success"));
        InOrder order = inOrder(store, gateway);
        order.verify(store).create(any(PersistPaymentAttemptCommand.class));
        order.verify(gateway).createCheckout(any(ProviderCheckoutRequest.class));
    }

    @Test
    void providerFailureIsDurablyFailedAndNeverCreatesProcessingState() {
        givenMembershipAndPeriod();
        PaymentAttemptDetails created = attempt(PaymentAttemptStatus.CREATED, 0);
        PaymentAttemptDetails failed = failedAttempt();
        CardCheckoutGateway gateway = mock(CardCheckoutGateway.class);
        CheckoutRedirectPolicy redirects = mock(CheckoutRedirectPolicy.class);
        given(store.create(any(PersistPaymentAttemptCommand.class))).willReturn(created);
        given(gatewayProvider.getIfAvailable()).willReturn(gateway);
        given(redirectProvider.getIfAvailable()).willReturn(redirects);
        given(redirects.successUrl(PaymentProvider.STRIPE))
                .willReturn(URI.create("https://gym.test/success"));
        given(redirects.cancelUrl(PaymentProvider.STRIPE))
                .willReturn(URI.create("https://gym.test/cancel"));
        given(gateway.createCheckout(any(ProviderCheckoutRequest.class)))
                .willThrow(new PaymentProviderException(PaymentProviderFailureCode.TIMEOUT));
        given(store.markFailed(any(FailPaymentAttemptCommand.class))).willReturn(failed);

        assertThatThrownBy(() -> service.createCheckout(
                new CreateCardCheckoutAttemptCommand(CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID), ACTOR))
                .isInstanceOf(PaymentProviderException.class);

        ArgumentCaptor<FailPaymentAttemptCommand> failure =
                ArgumentCaptor.forClass(FailPaymentAttemptCommand.class);
        verify(store).markFailed(failure.capture());
        assertThat(failure.getValue().failureCode())
                .isEqualTo(PaymentAttemptFailureCode.PROVIDER_UNAVAILABLE);
        verify(store, never()).markProcessing(any());
    }

    @Test
    void cancellationCallsProviderBeforeDurableCancellation() {
        PaymentAttemptDetails processing = attempt(PaymentAttemptStatus.PROCESSING, 1);
        PaymentAttemptDetails cancelled = new PaymentAttemptDetails(
                ATTEMPT_ID, CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID, PaymentProvider.STRIPE,
                PaymentAttemptStatus.CANCELLED, new BigDecimal("25.00"), "USD",
                PaymentAttemptFailureCode.PROVIDER_CANCELLED, null, ACTOR_ID,
                NOW, NOW, NOW, 2);
        CardCheckoutGateway gateway = mock(CardCheckoutGateway.class);
        given(store.findProviderDetails(ATTEMPT_ID)).willReturn(Optional.of(
                new PaymentAttemptProviderDetails(processing, "cs_test_1")));
        given(gatewayProvider.getIfAvailable()).willReturn(gateway);
        given(store.markCancelled(any(CancelPaymentAttemptPersistenceCommand.class)))
                .willReturn(cancelled);

        PaymentAttemptDetails result = service.cancel(
                new CancelPaymentAttemptCommand(ATTEMPT_ID, 1), ACTOR);

        assertThat(result.status()).isEqualTo(PaymentAttemptStatus.CANCELLED);
        InOrder order = inOrder(gateway, store);
        order.verify(gateway).cancelCheckout(any(ProviderCheckoutCancellationRequest.class));
        order.verify(store).markCancelled(any(CancelPaymentAttemptPersistenceCommand.class));
    }

    @Test
    void failedProviderCancellationLeavesAttemptOpen() {
        PaymentAttemptDetails processing = attempt(PaymentAttemptStatus.PROCESSING, 1);
        CardCheckoutGateway gateway = mock(CardCheckoutGateway.class);
        given(store.findProviderDetails(ATTEMPT_ID)).willReturn(Optional.of(
                new PaymentAttemptProviderDetails(processing, "cs_test_1")));
        given(gatewayProvider.getIfAvailable()).willReturn(gateway);
        org.mockito.BDDMockito.willThrow(new PaymentProviderException(
                PaymentProviderFailureCode.UNAVAILABLE)).given(gateway)
                .cancelCheckout(any(ProviderCheckoutCancellationRequest.class));

        assertThatThrownBy(() -> service.cancel(
                new CancelPaymentAttemptCommand(ATTEMPT_ID, 1), ACTOR))
                .isInstanceOf(PaymentProviderException.class);
        verify(store, never()).markCancelled(any());
    }

    @Test
    void rejectsMembershipOwnershipAndCancelledStateBeforeCreatingAttempt() {
        given(membershipQuery.findMembershipForPayment(MEMBERSHIP_ID)).willReturn(Optional.of(
                new MembershipPaymentDetails(MEMBERSHIP_ID,
                        UUID.fromString("10000000-0000-0000-0000-000000000099"),
                        MembershipStatus.ACTIVE)));
        given(membershipQuery.findPeriodForPayment(PERIOD_ID)).willReturn(Optional.of(
                new MembershipPaymentPeriodDetails(PERIOD_ID, MEMBERSHIP_ID,
                        new BigDecimal("25.00"), "USD")));
        assertThatThrownBy(() -> service.createCheckout(
                new CreateCardCheckoutAttemptCommand(CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID), ACTOR))
                .isInstanceOf(PaymentMembershipMismatchException.class);
        verify(store, never()).create(any());

        given(membershipQuery.findMembershipForPayment(MEMBERSHIP_ID)).willReturn(Optional.of(
                new MembershipPaymentDetails(MEMBERSHIP_ID, CLIENT_ID, MembershipStatus.CANCELLED)));
        given(membershipQuery.findPeriodForPayment(PERIOD_ID)).willReturn(Optional.of(
                new MembershipPaymentPeriodDetails(PERIOD_ID, MEMBERSHIP_ID,
                        new BigDecimal("25.00"), "USD")));
        assertThatThrownBy(() -> service.createCheckout(
                new CreateCardCheckoutAttemptCommand(CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID), ACTOR))
                .isInstanceOf(PaymentMembershipStateConflictException.class);
    }

    @Test
    void rejectsCancellationFromCreatedState() {
        PaymentAttemptDetails created = attempt(PaymentAttemptStatus.CREATED, 0);
        given(store.findProviderDetails(ATTEMPT_ID)).willReturn(Optional.of(
                new PaymentAttemptProviderDetails(created, null)));

        assertThatThrownBy(() -> service.cancel(
                new CancelPaymentAttemptCommand(ATTEMPT_ID, 0), ACTOR))
                .isInstanceOf(PaymentAttemptStateConflictException.class);
        verify(gatewayProvider, never()).getIfAvailable();
    }

    private void givenMembershipAndPeriod() {
        given(membershipQuery.findMembershipForPayment(MEMBERSHIP_ID)).willReturn(Optional.of(
                new MembershipPaymentDetails(MEMBERSHIP_ID, CLIENT_ID, MembershipStatus.ACTIVE)));
        given(membershipQuery.findPeriodForPayment(PERIOD_ID)).willReturn(Optional.of(
                new MembershipPaymentPeriodDetails(PERIOD_ID, MEMBERSHIP_ID,
                        new BigDecimal("25.00"), "USD")));
    }

    private static PaymentAttemptDetails attempt(PaymentAttemptStatus status, long version) {
        return new PaymentAttemptDetails(
                ATTEMPT_ID, CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID, PaymentProvider.STRIPE,
                status, new BigDecimal("25.00"), "USD", null, null, ACTOR_ID,
                NOW, NOW, status.isTerminal() ? NOW : null, version);
    }

    private static PaymentAttemptDetails failedAttempt() {
        return new PaymentAttemptDetails(
                ATTEMPT_ID, CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID, PaymentProvider.STRIPE,
                PaymentAttemptStatus.FAILED, new BigDecimal("25.00"), "USD",
                PaymentAttemptFailureCode.PROVIDER_UNAVAILABLE, null, ACTOR_ID,
                NOW, NOW, NOW, 1);
    }
}
