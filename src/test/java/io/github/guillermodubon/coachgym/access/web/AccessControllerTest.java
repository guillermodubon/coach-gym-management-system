package io.github.guillermodubon.coachgym.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.application.AccessApplicationService;
import io.github.guillermodubon.coachgym.access.application.AccessRecordPage;
import io.github.guillermodubon.coachgym.access.application.AccessRecordSearchQuery;
import io.github.guillermodubon.coachgym.access.application.CheckInCommand;
import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class AccessControllerTest {

    private static final UUID RECORD_ID =
            UUID.fromString(
                    "40000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "50000000-0000-0000-0000-000000000001");

    private static final Instant NOW =
            Instant.parse(
                    "2026-09-15T20:00:00Z");

    private AccessApplicationService service;
    private AccessController controller;

    @BeforeEach
    void setUp() {
        service =
                mock(AccessApplicationService.class);

        controller =
                new AccessController(service);
    }

    @Test
    void returnsOkForDeniedBusinessDecision() {
        AccessRecordDetails denied =
                deniedRecord();

        given(service.checkIn(
                any(CheckInCommand.class),
                any(AuthenticatedActor.class)))
                .willReturn(denied);

        ResponseEntity<AccessRecordResponse> response =
                controller.checkIn(
                        new CheckInRequest("XYZ-999999"),
                        authentication());

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody().result())
                .isEqualTo(AccessResult.DENIED);

        assertThat(response.getBody().reasonCode())
                .isEqualTo(
                        AccessReasonCode.IDENTIFIER_NOT_FOUND);
    }

    @Test
    void sendsRawIdentifierAndAuthenticatedActorToService() {
        given(service.checkIn(
                any(CheckInCommand.class),
                any(AuthenticatedActor.class)))
                .willReturn(deniedRecord());

        controller.checkIn(
                new CheckInRequest(
                        "  xyz-999999  "),
                authentication());

        ArgumentCaptor<CheckInCommand> commandCaptor =
                ArgumentCaptor.forClass(
                        CheckInCommand.class);

        ArgumentCaptor<AuthenticatedActor> actorCaptor =
                ArgumentCaptor.forClass(
                        AuthenticatedActor.class);

        verify(service).checkIn(
                commandCaptor.capture(),
                actorCaptor.capture());

        assertThat(
                commandCaptor.getValue()
                        .rawIdentifier())
                .isEqualTo("  xyz-999999  ");

        assertThat(actorCaptor.getValue().id())
                .isEqualTo(ACTOR_ID);

        assertThat(actorCaptor.getValue().username())
                .isEqualTo("receptionist");
    }

    @Test
    void delegatesFindById() {
        given(service.findById(RECORD_ID))
                .willReturn(deniedRecord());

        ResponseEntity<AccessRecordResponse> response =
                controller.findById(RECORD_ID);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody().id())
                .isEqualTo(RECORD_ID);

        verify(service)
                .findById(RECORD_ID);
    }


    @Test
    void buildsSearchQueryWithApprovedFilters() {
        given(service.findAll(
                any(AccessRecordSearchQuery.class)))
                .willReturn(
                        new AccessRecordPage(
                                List.of(),
                                0,
                                25,
                                0,
                                0));

        ResponseEntity<AccessRecordPageResponse> response =
                controller.findAll(
                        null,
                        null,
                        "DENIED",
                        "IDENTIFIER_NOT_FOUND",
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(60),
                        ACTOR_ID,
                        0,
                        25,
                        "CHECKED_IN_AT",
                        "DESC");

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody().items())
                .isEmpty();

        ArgumentCaptor<AccessRecordSearchQuery> captor =
                ArgumentCaptor.forClass(
                        AccessRecordSearchQuery.class);

        verify(service)
                .findAll(captor.capture());

        AccessRecordSearchQuery query =
                captor.getValue();

        assertThat(query.result())
                .isEqualTo(AccessResult.DENIED);

        assertThat(query.reasonCode())
                .isEqualTo(
                        AccessReasonCode.IDENTIFIER_NOT_FOUND);

        assertThat(query.checkedInFrom())
                .isEqualTo(NOW.minusSeconds(60));

        assertThat(query.checkedInUntil())
                .isEqualTo(NOW.plusSeconds(60));

        assertThat(query.processedByUserId())
                .isEqualTo(ACTOR_ID);

        assertThat(query.page())
                .isZero();

        assertThat(query.size())
                .isEqualTo(25);
    }

    private static AccessRecordDetails deniedRecord() {
        return new AccessRecordDetails(
                RECORD_ID,
                "XYZ-999999",
                null,
                null,
                null,
                null,
                AccessResult.DENIED,
                AccessReasonCode.IDENTIFIER_NOT_FOUND,
                "The presented identifier could not be resolved.",
                NOW,
                ACTOR_ID);
    }

    private static Authentication authentication() {
        CoachGymUserPrincipal principal =
                mock(CoachGymUserPrincipal.class);

        given(principal.authenticatedActor())
                .willReturn(
                        new AuthenticatedActor(
                                ACTOR_ID,
                                "receptionist"));

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of());
    }
}
