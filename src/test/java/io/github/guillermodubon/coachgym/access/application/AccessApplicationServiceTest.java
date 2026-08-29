package io.github.guillermodubon.coachgym.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.domain.AccessValidationException;
import io.github.guillermodubon.coachgym.client.ClientAccessDetails;
import io.github.guillermodubon.coachgym.client.ClientAccessQuery;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.membership.MembershipAccessDetails;
import io.github.guillermodubon.coachgym.membership.MembershipAccessQuery;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class AccessApplicationServiceTest {

    // ── Fixed IDs ─────────────────────────────────────────────────────────────

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

    private static final UUID OTHER_CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000099");

    // ── Time fixtures ─────────────────────────────────────────────────────────

    /**
     * Fixed clock in El Salvador time zone (UTC-6).
     * NOW = 2026-09-15T20:00:00Z → local date 2026-09-15 in UTC-6.
     */
    private static final Instant NOW =
            Instant.parse("2026-09-15T20:00:00Z");

    private static final ZoneId GYM_ZONE =
            ZoneId.of("America/El_Salvador");

    private static final LocalDate OPERATIONAL_DATE =
            LocalDate.ofInstant(NOW, GYM_ZONE); // 2026-09-15

    // Period surrounding the operational date.
    private static final LocalDate PERIOD_STARTS  = OPERATIONAL_DATE.minusDays(14);
    private static final LocalDate PERIOD_ENDS    = OPERATIONAL_DATE.plusDays(14);

    // ── Actor ─────────────────────────────────────────────────────────────────

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "receptionist");

    // ── Collaborators ─────────────────────────────────────────────────────────

    private AccessRecordStore accessRecordStore;
    private ClientAccessQuery clientAccessQuery;
    private MembershipAccessQuery membershipAccessQuery;
    private ApplicationEventPublisher eventPublisher;
    private AccessApplicationService service;

    @BeforeEach
    void setUp() {
        accessRecordStore      = mock(AccessRecordStore.class);
        clientAccessQuery      = mock(ClientAccessQuery.class);
        membershipAccessQuery  = mock(MembershipAccessQuery.class);
        eventPublisher         = mock(ApplicationEventPublisher.class);

        Clock clock = Clock.fixed(NOW, GYM_ZONE);

        service = new AccessApplicationService(
                accessRecordStore, clientAccessQuery,
                membershipAccessQuery, eventPublisher, clock);
    }

    // ── Happy path — membership code ─────────────────────────────────────────

    @Test
    void allowsCheckInByMembershipCode() {
        givenActiveMembershipByCode("MEM-000001");
        givenClientById(CLIENT_ID, ClientStatus.ACTIVE);
        AccessRecordDetails stored = allowedRecord();
        given(accessRecordStore.persist(any(), any(), any(), any(), any(), any(),
                eq(AccessResult.ALLOWED), eq(AccessReasonCode.ACCESS_ALLOWED),
                any(), eq(NOW), eq(ACTOR_ID)))
                .willReturn(stored);

        AccessRecordDetails result =
                service.checkIn(new CheckInCommand("MEM-000001"), ACTOR);

        assertThat(result.result()).isEqualTo(AccessResult.ALLOWED);
        assertThat(result.id()).isEqualTo(RECORD_ID);
    }

    @Test
    void allowsCheckInByClientCode() {
        givenActiveClientByCode("CLI-000001");
        givenCurrentMembershipByClientId(CLIENT_ID);
        AccessRecordDetails stored = allowedRecord();
        given(accessRecordStore.persist(any(), any(), any(), any(), any(), any(),
                eq(AccessResult.ALLOWED), eq(AccessReasonCode.ACCESS_ALLOWED),
                any(), eq(NOW), eq(ACTOR_ID)))
                .willReturn(stored);

        AccessRecordDetails result =
                service.checkIn(new CheckInCommand("CLI-000001"), ACTOR);

        assertThat(result.result()).isEqualTo(AccessResult.ALLOWED);
    }

    // ── Persist-then-publish ordering ────────────────────────────────────────

    @Test
    void publishesEventAfterPersist() {
        givenActiveMembershipByCode("MEM-000001");
        givenClientById(CLIENT_ID, ClientStatus.ACTIVE);
        given(accessRecordStore.persist(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()))
                .willReturn(allowedRecord());

        service.checkIn(new CheckInCommand("MEM-000001"), ACTOR);

        // Verify persist was called before publishEvent.
        var inOrder = org.mockito.Mockito.inOrder(accessRecordStore, eventPublisher);
        inOrder.verify(accessRecordStore).persist(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
        inOrder.verify(eventPublisher).publishEvent(any(AccessAttemptRecorded.class));
    }

    @Test
    void eventContainsCorrectFields() {
        givenActiveMembershipByCode("MEM-000001");
        givenClientById(
                CLIENT_ID,
                ClientStatus.ACTIVE);

        given(accessRecordStore.persist(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
                .willReturn(allowedRecord());

        service.checkIn(
                new CheckInCommand("MEM-000001"),
                ACTOR);

        ArgumentCaptor<AccessAttemptRecorded> captor =
                ArgumentCaptor.forClass(
                        AccessAttemptRecorded.class);

        verify(eventPublisher)
                .publishEvent(captor.capture());

        AccessAttemptRecorded event =
                captor.getValue();

        assertThat(event.accessRecordId())
                .isEqualTo(RECORD_ID);

        assertThat(event.presentedIdentifier())
                .isEqualTo("MEM-000001");

        assertThat(event.presentedIdentifierType())
                .isEqualTo("MEMBERSHIP_CODE");

        assertThat(event.clientId())
                .isEqualTo(CLIENT_ID);

        assertThat(event.clientCode())
                .isEqualTo("CLI-000001");

        assertThat(event.membershipId())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(event.membershipCode())
                .isEqualTo("MEM-000001");

        assertThat(event.result())
                .isEqualTo(AccessResult.ALLOWED);

        assertThat(event.reasonCode())
                .isEqualTo(
                        AccessReasonCode.ACCESS_ALLOWED);

        assertThat(event.checkedInAt())
                .isEqualTo(NOW);

        assertThat(event.actorUserId())
                .isEqualTo(ACTOR_ID);

        assertThat(event.actorIdentifier())
                .isEqualTo("receptionist");

        assertThat(event.occurredAt())
                .isEqualTo(NOW);
    }

    @Test
    void eventContainsNullClientAndMembershipForIdentifierNotFound() {
        given(membershipAccessQuery.findByCode("MEM-999999")).willReturn(Optional.empty());
        given(accessRecordStore.persist(any(), any(), any(), any(), any(), any(),
                eq(AccessResult.DENIED), eq(AccessReasonCode.IDENTIFIER_NOT_FOUND),
                any(), any(), any()))
                .willReturn(deniedRecord(AccessReasonCode.IDENTIFIER_NOT_FOUND));

        service.checkIn(new CheckInCommand("MEM-999999"), ACTOR);

        ArgumentCaptor<AccessAttemptRecorded> captor =
                ArgumentCaptor.forClass(AccessAttemptRecorded.class);
        verify(eventPublisher).publishEvent(captor.capture());

        assertThat(captor.getValue().clientId()).isNull();
        assertThat(captor.getValue().membershipId()).isNull();
    }

    // ── Denial paths ─────────────────────────────────────────────────────────

    @Test
    void deniesWhenIdentifierNotFound() {
        given(membershipAccessQuery.findByCode("MEM-999999")).willReturn(Optional.empty());
        AccessRecordDetails stored = deniedRecord(AccessReasonCode.IDENTIFIER_NOT_FOUND);
        given(accessRecordStore.persist(any(), any(), any(), any(), any(), any(),
                eq(AccessResult.DENIED), eq(AccessReasonCode.IDENTIFIER_NOT_FOUND),
                any(), any(), any()))
                .willReturn(stored);

        AccessRecordDetails result =
                service.checkIn(new CheckInCommand("MEM-999999"), ACTOR);

        assertThat(result.result()).isEqualTo(AccessResult.DENIED);
        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.IDENTIFIER_NOT_FOUND);
    }

    @Test
    void deniesWhenClientIsInactive() {
        givenActiveMembershipByCode("MEM-000001");
        givenClientById(CLIENT_ID, ClientStatus.INACTIVE);
        AccessRecordDetails stored = deniedRecord(AccessReasonCode.CLIENT_INACTIVE);
        given(accessRecordStore.persist(any(), any(), any(), any(), any(), any(),
                eq(AccessResult.DENIED), eq(AccessReasonCode.CLIENT_INACTIVE),
                any(), any(), any()))
                .willReturn(stored);

        AccessRecordDetails result =
                service.checkIn(new CheckInCommand("MEM-000001"), ACTOR);

        assertThat(result.result()).isEqualTo(AccessResult.DENIED);
        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.CLIENT_INACTIVE);
    }

    @Test
    void deniesWhenNoCurrentMembership() {
        givenActiveClientByCode("CLI-000001");
        given(membershipAccessQuery.findCurrentByClientId(CLIENT_ID))
                .willReturn(Optional.empty());
        AccessRecordDetails stored = deniedRecord(AccessReasonCode.MEMBERSHIP_NOT_FOUND);
        given(accessRecordStore.persist(any(), any(), any(), any(), any(), any(),
                eq(AccessResult.DENIED), eq(AccessReasonCode.MEMBERSHIP_NOT_FOUND),
                any(), any(), any()))
                .willReturn(stored);

        AccessRecordDetails result =
                service.checkIn(new CheckInCommand("CLI-000001"), ACTOR);

        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.MEMBERSHIP_NOT_FOUND);
    }

    // ── Unresolved-identifier persistence ─────────────────────────────────────

    @Test
    void persistsNullClientAndMembershipForUnresolvedIdentifier() {
        given(membershipAccessQuery.findByCode("MEM-999999")).willReturn(Optional.empty());
        given(accessRecordStore.persist(
                eq("MEM-999999"),
                eq(null), eq(null),     // clientId, clientCode
                eq(null), eq(null),     // membershipId, membershipCode
                eq(null),               // membershipPeriodId
                eq(AccessResult.DENIED),
                eq(AccessReasonCode.IDENTIFIER_NOT_FOUND),
                any(), any(), any()))
                .willReturn(deniedRecord(AccessReasonCode.IDENTIFIER_NOT_FOUND));

        service.checkIn(new CheckInCommand("MEM-999999"), ACTOR);

        verify(accessRecordStore).persist(
                eq("MEM-999999"),
                eq(null), eq(null),
                eq(null), eq(null),
                eq(null),
                eq(AccessResult.DENIED),
                eq(AccessReasonCode.IDENTIFIER_NOT_FOUND),
                any(), any(), any());
    }

    // ── Ownership cross-check ─────────────────────────────────────────────────

    @Test
    void throwsIllegalStateExceptionWhenMembershipClientMismatch() {
        // Membership says clientId = OTHER_CLIENT_ID, but client lookup by
        // that UUID returns a client with id = OTHER_CLIENT_ID — ownership
        // check should pass. We simulate a genuine mismatch by returning a
        // client with a different id than what the membership declares.
        MembershipAccessDetails mem = activeMembership(OTHER_CLIENT_ID);
        given(membershipAccessQuery.findByCode("MEM-000001"))
                .willReturn(Optional.of(mem));
        // Client loaded by OTHER_CLIENT_ID unexpectedly returns CLIENT_ID.
        ClientAccessDetails mismatchedClient = new ClientAccessDetails(
                CLIENT_ID, "CLI-000001", ClientStatus.ACTIVE);
        given(clientAccessQuery.findById(OTHER_CLIENT_ID))
                .willReturn(Optional.of(mismatchedClient));

        assertThatThrownBy(() -> service.checkIn(
                new CheckInCommand("MEM-000001"), ACTOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Internal data inconsistency");

        verify(accessRecordStore, never()).persist(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── Single clock capture ──────────────────────────────────────────────────

    @Test
    void usesGyZoneForOperationalDate() {
        // Clock is fixed at 2026-09-15T20:00:00Z (UTC).
        // El Salvador is UTC-6 → local date is 2026-09-15.
        // A period starting 2026-09-15 should be valid (inclusive).
        MembershipAccessDetails mem = new MembershipAccessDetails(
                MEMBERSHIP_ID, "MEM-000001", CLIENT_ID,
                MembershipStatus.ACTIVE, PERIOD_ID,
                OPERATIONAL_DATE,         // starts today — inclusive
                OPERATIONAL_DATE.plusDays(30),
                null, null);

        given(membershipAccessQuery.findByCode("MEM-000001"))
                .willReturn(Optional.of(mem));
        givenClientById(CLIENT_ID, ClientStatus.ACTIVE);
        given(accessRecordStore.persist(any(), any(), any(), any(), any(), any(),
                eq(AccessResult.ALLOWED), any(), any(), any(), any()))
                .willReturn(allowedRecord());

        AccessRecordDetails result =
                service.checkIn(new CheckInCommand("MEM-000001"), ACTOR);

        assertThat(result.result()).isEqualTo(AccessResult.ALLOWED);
    }

    @Test
    void passesOccurredAtFromClockToPersistAndEvent() {
        givenActiveMembershipByCode("MEM-000001");
        givenClientById(CLIENT_ID, ClientStatus.ACTIVE);
        given(accessRecordStore.persist(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), eq(NOW), eq(ACTOR_ID)))
                .willReturn(allowedRecord());

        service.checkIn(new CheckInCommand("MEM-000001"), ACTOR);

        // Verify persist received exactly NOW.
        verify(accessRecordStore).persist(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), eq(NOW), eq(ACTOR_ID));

        // Verify event also received NOW.
        ArgumentCaptor<AccessAttemptRecorded> captor =
                ArgumentCaptor.forClass(AccessAttemptRecorded.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().occurredAt()).isEqualTo(NOW);
    }

    // ── No persist / event on technical failures ──────────────────────────────

    @Test
    void doesNotPersistWhenCommandIsNull() {
        assertThatThrownBy(() -> service.checkIn(null, ACTOR))
                .isInstanceOf(AccessValidationException.class);

        verify(accessRecordStore, never()).persist(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void doesNotPersistWhenActorIsNull() {
        assertThatThrownBy(() ->
                service.checkIn(new CheckInCommand("MEM-000001"), null))
                .isInstanceOf(AccessValidationException.class);

        verify(accessRecordStore, never()).persist(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void doesNotPersistWhenIdentifierIsBlank() {
        assertThatThrownBy(() ->
                service.checkIn(new CheckInCommand("  "), ACTOR))
                .isInstanceOf(AccessValidationException.class);

        verify(accessRecordStore, never()).persist(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void returnsRecordWhenFound() {
        given(accessRecordStore.findById(RECORD_ID))
                .willReturn(Optional.of(allowedRecord()));

        AccessRecordDetails result = service.findById(RECORD_ID);

        assertThat(result.id()).isEqualTo(RECORD_ID);
    }

    @Test
    void throwsNotFoundForUnknownId() {
        given(accessRecordStore.findById(RECORD_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(RECORD_ID))
                .isInstanceOf(AccessRecordNotFoundException.class)
                .hasMessageContaining(RECORD_ID.toString());
    }

    @Test
    void throwsValidationExceptionForNullId() {
        assertThatThrownBy(() -> service.findById(null))
                .isInstanceOf(AccessValidationException.class);
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void returnsPageFromStore() {
        AccessRecordSearchQuery query = AccessRecordSearchQuery.from(
                null, null, null, null, null, null, null, 0, 25, null, null);
        AccessRecordPage page = new AccessRecordPage(
                java.util.List.of(), 0, 25, 0L, 0);
        given(accessRecordStore.findAll(query)).willReturn(page);

        AccessRecordPage result = service.findAll(query);

        assertThat(result.totalElements()).isZero();
    }

    @Test
    void throwsValidationExceptionForNullQuery() {
        assertThatThrownBy(() -> service.findAll(null))
                .isInstanceOf(AccessValidationException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void givenActiveMembershipByCode(String code) {
        given(membershipAccessQuery.findByCode(code))
                .willReturn(Optional.of(activeMembership(CLIENT_ID)));
    }

    private void givenCurrentMembershipByClientId(UUID clientId) {
        given(membershipAccessQuery.findCurrentByClientId(clientId))
                .willReturn(Optional.of(activeMembership(clientId)));
    }

    private void givenActiveClientByCode(String code) {
        given(clientAccessQuery.findByCode(code))
                .willReturn(Optional.of(
                        new ClientAccessDetails(CLIENT_ID, "CLI-000001", ClientStatus.ACTIVE)));
    }

    private void givenClientById(UUID clientId, ClientStatus status) {
        given(clientAccessQuery.findById(clientId))
                .willReturn(Optional.of(
                        new ClientAccessDetails(clientId, "CLI-000001", status)));
    }

    private static MembershipAccessDetails activeMembership(UUID clientId) {
        return new MembershipAccessDetails(
                MEMBERSHIP_ID, "MEM-000001", clientId,
                MembershipStatus.ACTIVE, PERIOD_ID,
                PERIOD_STARTS, PERIOD_ENDS,
                null, null);
    }

    private static AccessRecordDetails allowedRecord() {
        return new AccessRecordDetails(
                RECORD_ID, "MEM-000001",
                CLIENT_ID, "CLI-000001",
                MEMBERSHIP_ID, "MEM-000001",
                AccessResult.ALLOWED, AccessReasonCode.ACCESS_ALLOWED,
                "Membership is active and its current period is valid.",
                NOW, ACTOR_ID);
    }

    private static AccessRecordDetails deniedRecord(AccessReasonCode reasonCode) {
        return new AccessRecordDetails(
                RECORD_ID, "MEM-000001",
                null, null,
                null, null,
                AccessResult.DENIED, reasonCode,
                "Denied.",
                NOW, ACTOR_ID);
    }
}
