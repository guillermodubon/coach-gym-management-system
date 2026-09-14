package io.github.guillermodubon.coachgym.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import io.github.guillermodubon.coachgym.membership.MembershipAccessDetails;
import io.github.guillermodubon.coachgym.membership.MembershipAccessQuery;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

class QrAccessCheckInApplicationServiceTest {

    private static final UUID CLIENT_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID MEMBERSHIP_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");
    private static final UUID PERIOD_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001");
    private static final UUID CREDENTIAL_ID = UUID.fromString(
            "60000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000001");
    private static final UUID RECORD_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-15T20:00:00Z");
    private static final String PAYLOAD =
            "cgac:v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private AccessRecordStore accessRecordStore;
    private AccessCredentialResolver credentialResolver;
    private ClientAccessQuery clientAccessQuery;
    private MembershipAccessQuery membershipAccessQuery;
    private ApplicationEventPublisher eventPublisher;
    private AccessApplicationService service;

    @BeforeEach
    void setUp() {
        accessRecordStore = mock(AccessRecordStore.class);
        credentialResolver = mock(AccessCredentialResolver.class);
        clientAccessQuery = mock(ClientAccessQuery.class);
        membershipAccessQuery = mock(MembershipAccessQuery.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new AccessApplicationService(
                accessRecordStore,
                clientAccessQuery,
                membershipAccessQuery,
                credentialResolver,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Optional.of(new DuplicateScanPolicy(Duration.ofSeconds(30))));

        given(credentialResolver.resolveAndLock(any()))
                .willReturn(Optional.of(new ResolvedAccessCredential(
                        CREDENTIAL_ID, CLIENT_ID, AccessCredentialStatus.ACTIVE)));
        given(clientAccessQuery.findById(CLIENT_ID))
                .willReturn(Optional.of(new ClientAccessDetails(
                        CLIENT_ID, "CLI-000001", ClientStatus.ACTIVE)));
        given(membershipAccessQuery.findCurrentByClientId(CLIENT_ID))
                .willReturn(Optional.of(new MembershipAccessDetails(
                        MEMBERSHIP_ID,
                        "MEM-000001",
                        CLIENT_ID,
                        MembershipStatus.ACTIVE,
                        PERIOD_ID,
                        NOW.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1),
                        NOW.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1),
                        null,
                        null)));
        given(accessRecordStore.findMostRecentAllowedQrAttempt(
                eq(CREDENTIAL_ID), any())).willReturn(Optional.empty());
    }

    @Test
    void persistsAllowedQrAttemptWithSafeSourceAndPublishesAfterPersistence() {
        AccessRecordDetails stored = record(AccessResult.ALLOWED,
                AccessReasonCode.ACCESS_ALLOWED, NOW);
        given(accessRecordStore.persistQr(
                eq("QR_CREDENTIAL"), eq(CREDENTIAL_ID), eq(CLIENT_ID),
                eq("CLI-000001"), eq(MEMBERSHIP_ID), eq("MEM-000001"),
                eq(PERIOD_ID), eq(AccessResult.ALLOWED),
                eq(AccessReasonCode.ACCESS_ALLOWED), any(), eq(NOW), eq(ACTOR_ID)))
                .willReturn(stored);

        AccessRecordDetails result = service.checkInQr(command(), actor());

        assertThat(result).isEqualTo(stored);
        InOrder order = inOrder(accessRecordStore, eventPublisher);
        order.verify(accessRecordStore).persistQr(
                eq("QR_CREDENTIAL"), eq(CREDENTIAL_ID), eq(CLIENT_ID),
                eq("CLI-000001"), eq(MEMBERSHIP_ID), eq("MEM-000001"),
                eq(PERIOD_ID), eq(AccessResult.ALLOWED),
                eq(AccessReasonCode.ACCESS_ALLOWED), any(), eq(NOW), eq(ACTOR_ID));
        order.verify(eventPublisher).publishEvent(any(AccessAttemptRecorded.class));
    }

    @Test
    void recordsDuplicateInsideWindowAsDenied() {
        given(accessRecordStore.findMostRecentAllowedQrAttempt(
                eq(CREDENTIAL_ID), any()))
                .willReturn(Optional.of(record(
                        AccessResult.ALLOWED,
                        AccessReasonCode.ACCESS_ALLOWED,
                        NOW.minusSeconds(5))));
        AccessRecordDetails denied = record(
                AccessResult.DENIED,
                AccessReasonCode.DUPLICATE_CHECK_IN,
                NOW);
        given(accessRecordStore.persistQr(
                any(), any(), any(), any(), any(), any(), any(),
                eq(AccessResult.DENIED), eq(AccessReasonCode.DUPLICATE_CHECK_IN),
                any(), eq(NOW), eq(ACTOR_ID))).willReturn(denied);

        assertThat(service.checkInQr(command(), actor()).reasonCode())
                .isEqualTo(AccessReasonCode.DUPLICATE_CHECK_IN);
        verify(accessRecordStore).persistQr(
                eq("QR_CREDENTIAL"), eq(CREDENTIAL_ID), eq(CLIENT_ID),
                eq("CLI-000001"), eq(MEMBERSHIP_ID), eq("MEM-000001"),
                eq(PERIOD_ID), eq(AccessResult.DENIED),
                eq(AccessReasonCode.DUPLICATE_CHECK_IN), any(), eq(NOW), eq(ACTOR_ID));
    }

    @Test
    void exactBoundaryIsEligibleForNormalAccess() {
        given(accessRecordStore.findMostRecentAllowedQrAttempt(
                eq(CREDENTIAL_ID), any()))
                .willReturn(Optional.of(record(
                        AccessResult.ALLOWED,
                        AccessReasonCode.ACCESS_ALLOWED,
                        NOW.minusSeconds(30))));
        AccessRecordDetails allowed = record(
                AccessResult.ALLOWED,
                AccessReasonCode.ACCESS_ALLOWED,
                NOW);
        given(accessRecordStore.persistQr(
                any(), any(), any(), any(), any(), any(), any(),
                eq(AccessResult.ALLOWED), eq(AccessReasonCode.ACCESS_ALLOWED),
                any(), eq(NOW), eq(ACTOR_ID))).willReturn(allowed);

        assertThat(service.checkInQr(command(), actor()).result())
                .isEqualTo(AccessResult.ALLOWED);
    }

    @Test
    void unknownCredentialDoesNotPersistOrPublish() {
        given(credentialResolver.resolveAndLock(any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkInQr(command(), actor()))
                .isInstanceOf(QrAccessCredentialUnavailableException.class);
        verify(accessRecordStore, never()).persistQr(
                any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void persistenceFailureDoesNotPublishAnEvent() {
        given(accessRecordStore.persistQr(
                any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()))
                .willThrow(new AccessRecordDataAccessException(
                        "QR access attempt could not be persisted.", null));

        assertThatThrownBy(() -> service.checkInQr(command(), actor()))
                .isInstanceOf(AccessRecordDataAccessException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    private static QrAccessCheckInCommand command() {
        return new QrAccessCheckInCommand(AccessCredentialQrPayload.parse(PAYLOAD));
    }

    private static AuthenticatedActor actor() {
        return new AuthenticatedActor(ACTOR_ID, "receptionist");
    }

    private static AccessRecordDetails record(
            AccessResult result,
            AccessReasonCode reasonCode,
            Instant checkedInAt) {
        return new AccessRecordDetails(
                RECORD_ID,
                "QR_CREDENTIAL",
                CLIENT_ID,
                "CLI-000001",
                MEMBERSHIP_ID,
                "MEM-000001",
                result,
                reasonCode,
                result == AccessResult.ALLOWED
                        ? "Membership is active and its current period is valid."
                        : "A recent QR check-in was already recorded.",
                checkedInAt,
                ACTOR_ID);
    }
}
