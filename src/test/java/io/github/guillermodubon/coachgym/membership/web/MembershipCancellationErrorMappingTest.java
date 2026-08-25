package io.github.guillermodubon.coachgym.membership.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.application.MembershipAlreadyCancelledException;
import io.github.guillermodubon.coachgym.membership.application.MembershipApplicationService;
import io.github.guillermodubon.coachgym.membership.application.MembershipCancellationApplicationService;
import io.github.guillermodubon.coachgym.membership.application.MembershipCancellationStateConflictException;
import io.github.guillermodubon.coachgym.membership.application.MembershipFreezeApplicationService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class MembershipCancellationErrorMappingTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private MembershipController controller;

    @BeforeEach
    void setUp() {
        controller =
                new MembershipController(
                        mock(
                                MembershipApplicationService.class),
                        mock(
                                MembershipFreezeApplicationService.class),
                        mock(
                                MembershipCancellationApplicationService.class));
    }

    @Test
    void shouldMapAlreadyCancelledMembershipToConflict() {
        MembershipAlreadyCancelledException exception =
                new MembershipAlreadyCancelledException(
                        MEMBERSHIP_ID);

        ResponseEntity<ProblemDetail> response =
                controller.handleAlreadyCancelled(
                        exception);

        assertThat(response.getStatusCode())
                .isEqualTo(
                        HttpStatus.CONFLICT);

        ProblemDetail body =
                response.getBody();

        assertThat(body)
                .isNotNull();

        assertThat(body.getStatus())
                .isEqualTo(
                        HttpStatus.CONFLICT.value());

        assertThat(body.getProperties())
                .isNotNull()
                .containsEntry(
                        "code",
                        "MEMBERSHIP_ALREADY_CANCELLED");

        assertThat(body.getDetail())
                .isEqualTo(
                        exception.getMessage());
    }

    @Test
    void shouldMapCancellationStateConflictToConflict() {
        MembershipCancellationStateConflictException exception =
                new MembershipCancellationStateConflictException(
                        MEMBERSHIP_ID,
                        MembershipStatus.EXPIRED);

        ResponseEntity<ProblemDetail> response =
                controller.handleCancellationStateConflict(
                        exception);

        assertThat(response.getStatusCode())
                .isEqualTo(
                        HttpStatus.CONFLICT);

        ProblemDetail body =
                response.getBody();

        assertThat(body)
                .isNotNull();

        assertThat(body.getStatus())
                .isEqualTo(
                        HttpStatus.CONFLICT.value());

        assertThat(body.getProperties())
                .isNotNull()
                .containsEntry(
                        "code",
                        "MEMBERSHIP_CANCELLATION_STATE_CONFLICT");

        assertThat(body.getDetail())
                .isEqualTo(
                        exception.getMessage());
    }
}