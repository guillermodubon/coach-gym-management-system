package io.github.guillermodubon.coachgym.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.domain.DuplicateScanPolicy;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialQrPayload;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialResolver;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.ResolvedAccessCredential;
import io.github.guillermodubon.coachgym.client.ClientAccessDetails;
import io.github.guillermodubon.coachgym.client.ClientAccessQuery;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyDetails;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyQuery;
import io.github.guillermodubon.coachgym.membership.MembershipAccessDetails;
import io.github.guillermodubon.coachgym.membership.MembershipAccessQuery;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.payment.ConfirmedPaymentForAccessQuery;
import io.github.guillermodubon.coachgym.payment.application.ConfirmedPaymentForAccessDataAccessException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class AccessPaymentPolicyApplicationServiceTest {

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MEMBERSHIP_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PERIOD_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID RECORD_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID CREDENTIAL_ID =
            UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-15T20:00:00Z");
    private static final ZoneId GYM_ZONE = ZoneId.of("America/El_Salvador");
    private static final LocalDate TODAY = LocalDate.ofInstant(NOW, GYM_ZONE);
    private static final String MEMBERSHIP_CODE = "MEM-000001";
    private static final String CLIENT_CODE = "CLI-000001";
    private static final String QR_PAYLOAD =
            "cgac:v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "receptionist");

    private AccessRecordStore accessRecordStore;
    private ClientAccessQuery clientAccessQuery;
    private MembershipAccessQuery membershipAccessQuery;
    private AccessCredentialResolver accessCredentialResolver;
    private ApplicationEventPublisher eventPublisher;
    private AccessPaymentPolicyQuery policyQuery;
    private ConfirmedPaymentForAccessQuery paymentQuery;
    private AccessApplicationService service;

    @BeforeEach
    void setUp() {
        accessRecordStore = mock(AccessRecordStore.class);
        clientAccessQuery = mock(ClientAccessQuery.class);
        membershipAccessQuery = mock(MembershipAccessQuery.class);
        accessCredentialResolver = mock(AccessCredentialResolver.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        policyQuery = mock(AccessPaymentPolicyQuery.class);
        paymentQuery = mock(ConfirmedPaymentForAccessQuery.class);

        service = new AccessApplicationService(
                accessRecordStore,
                clientAccessQuery,
                membershipAccessQuery,
                accessCredentialResolver,
                eventPublisher,
                Clock.fixed(NOW, GYM_ZONE),
                Optional.of(new DuplicateScanPolicy(Duration.ofSeconds(30))),
                policyQuery,
                paymentQuery);
    }

    @Test
    void enabledPolicyAllowsManualAccessWithExactConfirmedPayment() {
        givenEligibleManualMembership();
        given(policyQuery.findCurrent())
                .willReturn(new AccessPaymentPolicyDetails(true, 1));
        given(paymentQuery.hasConfirmedPaymentForPeriod(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID)).willReturn(true);
        given(accessRecordStore.persist(
                any(), any(), any(), any(), any(), any(),
                eq(AccessResult.ALLOWED), eq(AccessReasonCode.ACCESS_ALLOWED),
                any(), eq(NOW), eq(ACTOR_ID)))
                .willReturn(manualRecord(
                        AccessResult.ALLOWED, AccessReasonCode.ACCESS_ALLOWED));

        AccessRecordDetails result = service.checkIn(
                new CheckInCommand(MEMBERSHIP_CODE), ACTOR);

        assertThat(result.result()).isEqualTo(AccessResult.ALLOWED);
        verify(paymentQuery).hasConfirmedPaymentForPeriod(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID);
    }

    @Test
    void enabledPolicyRecordsManualPaymentRequiredDenialAndPublishesSafeEvent() {
        givenEligibleManualMembership();
        given(policyQuery.findCurrent())
                .willReturn(new AccessPaymentPolicyDetails(true, 1));
        given(paymentQuery.hasConfirmedPaymentForPeriod(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID)).willReturn(false);
        given(accessRecordStore.persist(
                any(), any(), any(), any(), any(), any(),
                eq(AccessResult.DENIED), eq(AccessReasonCode.PAYMENT_REQUIRED),
                any(), eq(NOW), eq(ACTOR_ID)))
                .willReturn(manualRecord(
                        AccessResult.DENIED, AccessReasonCode.PAYMENT_REQUIRED));

        AccessRecordDetails result = service.checkIn(
                new CheckInCommand(MEMBERSHIP_CODE), ACTOR);

        assertThat(result.result()).isEqualTo(AccessResult.DENIED);
        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.PAYMENT_REQUIRED);
        ArgumentCaptor<AccessAttemptRecorded> event =
                ArgumentCaptor.forClass(AccessAttemptRecorded.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().reasonCode())
                .isEqualTo(AccessReasonCode.PAYMENT_REQUIRED);
        assertThat(event.getValue().presentedIdentifier())
                .isEqualTo(MEMBERSHIP_CODE);
    }

    @Test
    void disabledPolicyPreservesManualAccessWithoutPaymentLookup() {
        givenEligibleManualMembership();
        given(policyQuery.findCurrent())
                .willReturn(new AccessPaymentPolicyDetails(false, 2));
        given(accessRecordStore.persist(
                any(), any(), any(), any(), any(), any(),
                eq(AccessResult.ALLOWED), eq(AccessReasonCode.ACCESS_ALLOWED),
                any(), eq(NOW), eq(ACTOR_ID)))
                .willReturn(manualRecord(
                        AccessResult.ALLOWED, AccessReasonCode.ACCESS_ALLOWED));

        AccessRecordDetails result = service.checkIn(
                new CheckInCommand(MEMBERSHIP_CODE), ACTOR);

        assertThat(result.result()).isEqualTo(AccessResult.ALLOWED);
        verifyNoInteractions(paymentQuery);
    }

    @Test
    void earlierClientDenialWinsWithoutLoadingPolicyOrPayment() {
        given(membershipAccessQuery.findByCode(MEMBERSHIP_CODE))
                .willReturn(Optional.of(activeMembership()));
        given(clientAccessQuery.findById(CLIENT_ID))
                .willReturn(Optional.of(new ClientAccessDetails(
                        CLIENT_ID, CLIENT_CODE, ClientStatus.INACTIVE)));
        given(accessRecordStore.persist(
                any(), any(), any(), any(), any(), any(),
                eq(AccessResult.DENIED), eq(AccessReasonCode.CLIENT_INACTIVE),
                any(), eq(NOW), eq(ACTOR_ID)))
                .willReturn(manualRecord(
                        AccessResult.DENIED, AccessReasonCode.CLIENT_INACTIVE));

        AccessRecordDetails result = service.checkIn(
                new CheckInCommand(MEMBERSHIP_CODE), ACTOR);

        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.CLIENT_INACTIVE);
        verifyNoInteractions(policyQuery, paymentQuery);
    }

    @Test
    void paymentQueryFailureIsTechnicalAndDoesNotPersistPaymentRequired() {
        givenEligibleManualMembership();
        given(policyQuery.findCurrent())
                .willReturn(new AccessPaymentPolicyDetails(true, 1));
        ConfirmedPaymentForAccessDataAccessException failure =
                new ConfirmedPaymentForAccessDataAccessException(
                        "database unavailable", new IllegalStateException());
        given(paymentQuery.hasConfirmedPaymentForPeriod(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID)).willThrow(failure);

        assertThatThrownBy(() -> service.checkIn(
                new CheckInCommand(MEMBERSHIP_CODE), ACTOR))
                .isInstanceOf(AccessPaymentPolicyEvaluationException.class)
                .hasMessage("Access payment policy could not be evaluated.")
                .hasCause(failure);
        verify(accessRecordStore, never()).persist(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void missingPolicyProjectionIsTechnicalAndDoesNotQueryPayment() {
        givenEligibleManualMembership();
        given(policyQuery.findCurrent()).willReturn(null);

        assertThatThrownBy(() -> service.checkIn(
                new CheckInCommand(MEMBERSHIP_CODE), ACTOR))
                .isInstanceOf(AccessPaymentPolicyEvaluationException.class)
                .hasMessage("Access payment policy query returned no policy.");
        verifyNoInteractions(paymentQuery);
        verify(accessRecordStore, never()).persist(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    void enabledPolicyDeniesQrAccessWithoutConfirmedPayment() {
        givenEligibleQrCredential();
        given(policyQuery.findCurrent())
                .willReturn(new AccessPaymentPolicyDetails(true, 1));
        given(paymentQuery.hasConfirmedPaymentForPeriod(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID)).willReturn(false);
        given(accessRecordStore.persistQr(
                any(), eq(CREDENTIAL_ID), any(), any(), any(), any(), any(),
                eq(AccessResult.DENIED), eq(AccessReasonCode.PAYMENT_REQUIRED),
                any(), eq(NOW), eq(ACTOR_ID)))
                .willReturn(qrRecord(
                        AccessResult.DENIED, AccessReasonCode.PAYMENT_REQUIRED));
        given(accessRecordStore.findMostRecentAllowedQrAttempt(
                eq(CREDENTIAL_ID), any())).willReturn(Optional.empty());

        AccessRecordDetails result = service.checkInQr(
                new QrAccessCheckInCommand(
                        AccessCredentialQrPayload.parse(QR_PAYLOAD)), ACTOR);

        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.PAYMENT_REQUIRED);
        verify(paymentQuery).hasConfirmedPaymentForPeriod(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID);
    }

    @Test
    void disabledPolicyPreservesQrAccessWithoutPaymentLookup() {
        givenEligibleQrCredential();
        given(policyQuery.findCurrent())
                .willReturn(new AccessPaymentPolicyDetails(false, 1));
        given(accessRecordStore.findMostRecentAllowedQrAttempt(
                eq(CREDENTIAL_ID), any())).willReturn(Optional.empty());
        given(accessRecordStore.persistQr(
                any(), eq(CREDENTIAL_ID), any(), any(), any(), any(), any(),
                eq(AccessResult.ALLOWED), eq(AccessReasonCode.ACCESS_ALLOWED),
                any(), eq(NOW), eq(ACTOR_ID)))
                .willReturn(qrRecord(
                        AccessResult.ALLOWED, AccessReasonCode.ACCESS_ALLOWED));

        AccessRecordDetails result = service.checkInQr(
                new QrAccessCheckInCommand(
                        AccessCredentialQrPayload.parse(QR_PAYLOAD)), ACTOR);

        assertThat(result.result()).isEqualTo(AccessResult.ALLOWED);
        verifyNoInteractions(paymentQuery);
    }

    @Test
    void enabledPolicyAllowsQrAccessWithConfirmedPayment() {
        givenEligibleQrCredential();
        given(policyQuery.findCurrent())
                .willReturn(new AccessPaymentPolicyDetails(true, 1));
        given(paymentQuery.hasConfirmedPaymentForPeriod(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID)).willReturn(true);
        given(accessRecordStore.persistQr(
                any(), eq(CREDENTIAL_ID), any(), any(), any(), any(), any(),
                eq(AccessResult.ALLOWED), eq(AccessReasonCode.ACCESS_ALLOWED),
                any(), eq(NOW), eq(ACTOR_ID)))
                .willReturn(qrRecord(
                        AccessResult.ALLOWED, AccessReasonCode.ACCESS_ALLOWED));
        given(accessRecordStore.findMostRecentAllowedQrAttempt(
                eq(CREDENTIAL_ID), any())).willReturn(Optional.empty());

        AccessRecordDetails result = service.checkInQr(
                new QrAccessCheckInCommand(
                        AccessCredentialQrPayload.parse(QR_PAYLOAD)), ACTOR);

        assertThat(result.result()).isEqualTo(AccessResult.ALLOWED);
        verify(paymentQuery).hasConfirmedPaymentForPeriod(
                CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID);
    }

    @Test
    void duplicateQrDecisionWinsWithoutPaymentLookup() {
        givenEligibleQrCredential();
        given(policyQuery.findCurrent())
                .willReturn(new AccessPaymentPolicyDetails(true, 1));
        given(accessRecordStore.findMostRecentAllowedQrAttempt(
                eq(CREDENTIAL_ID), any())).willReturn(Optional.of(
                        qrRecord(AccessResult.ALLOWED, AccessReasonCode.ACCESS_ALLOWED)));
        given(accessRecordStore.persistQr(
                any(), eq(CREDENTIAL_ID), any(), any(), any(), any(), any(),
                eq(AccessResult.DENIED), eq(AccessReasonCode.DUPLICATE_CHECK_IN),
                any(), eq(NOW), eq(ACTOR_ID)))
                .willReturn(qrRecord(
                        AccessResult.DENIED, AccessReasonCode.DUPLICATE_CHECK_IN));

        AccessRecordDetails result = service.checkInQr(
                new QrAccessCheckInCommand(
                        AccessCredentialQrPayload.parse(QR_PAYLOAD)), ACTOR);

        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.DUPLICATE_CHECK_IN);
        verifyNoInteractions(policyQuery, paymentQuery);
    }

    private void givenEligibleManualMembership() {
        given(membershipAccessQuery.findByCode(MEMBERSHIP_CODE))
                .willReturn(Optional.of(activeMembership()));
        given(clientAccessQuery.findById(CLIENT_ID))
                .willReturn(Optional.of(new ClientAccessDetails(
                        CLIENT_ID, CLIENT_CODE, ClientStatus.ACTIVE)));
    }

    private void givenEligibleQrCredential() {
        ResolvedAccessCredential credential = new ResolvedAccessCredential(
                CREDENTIAL_ID, CLIENT_ID, AccessCredentialStatus.ACTIVE);
        AccessCredentialQrPayload payload = AccessCredentialQrPayload.parse(QR_PAYLOAD);
        given(accessCredentialResolver.resolveAndLock(payload))
                .willReturn(Optional.of(credential));
        given(clientAccessQuery.findById(CLIENT_ID))
                .willReturn(Optional.of(new ClientAccessDetails(
                        CLIENT_ID, CLIENT_CODE, ClientStatus.ACTIVE)));
        given(membershipAccessQuery.findCurrentByClientId(CLIENT_ID))
                .willReturn(Optional.of(activeMembership()));
    }

    private MembershipAccessDetails activeMembership() {
        return new MembershipAccessDetails(
                MEMBERSHIP_ID,
                MEMBERSHIP_CODE,
                CLIENT_ID,
                MembershipStatus.ACTIVE,
                PERIOD_ID,
                TODAY.minusDays(10),
                TODAY.plusDays(20),
                null,
                null);
    }

    private AccessRecordDetails manualRecord(
            AccessResult result,
            AccessReasonCode reasonCode) {
        return new AccessRecordDetails(
                RECORD_ID,
                MEMBERSHIP_CODE,
                CLIENT_ID,
                CLIENT_CODE,
                MEMBERSHIP_ID,
                MEMBERSHIP_CODE,
                result,
                reasonCode,
                reasonCode.name(),
                NOW,
                ACTOR_ID);
    }

    private AccessRecordDetails qrRecord(
            AccessResult result,
            AccessReasonCode reasonCode) {
        return new AccessRecordDetails(
                RECORD_ID,
                "QR_CREDENTIAL",
                CLIENT_ID,
                CLIENT_CODE,
                MEMBERSHIP_ID,
                MEMBERSHIP_CODE,
                result,
                reasonCode,
                reasonCode.name(),
                NOW,
                ACTOR_ID);
    }
}
