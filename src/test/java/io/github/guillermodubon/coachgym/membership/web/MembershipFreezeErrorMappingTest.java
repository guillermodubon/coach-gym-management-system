package io.github.guillermodubon.coachgym.membership.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.application.MembershipAlreadyFrozenException;
import io.github.guillermodubon.coachgym.membership.application.MembershipApplicationService;
import io.github.guillermodubon.coachgym.membership.application.MembershipFreezeApplicationService;
import io.github.guillermodubon.coachgym.membership.application.MembershipFreezeNotFoundException;
import io.github.guillermodubon.coachgym.membership.application.MembershipFreezeStateConflictException;
import io.github.guillermodubon.coachgym.membership.application.MembershipNotFrozenException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class MembershipFreezeErrorMappingTest {

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
                                MembershipFreezeApplicationService.class));
    }

    @Test
    void shouldMapAlreadyFrozenToConflict() {
        ResponseEntity<ProblemDetail> response =
                controller.handleAlreadyFrozen(
                        new MembershipAlreadyFrozenException(
                                MEMBERSHIP_ID));

        assertConflict(
                response,
                "MEMBERSHIP_ALREADY_FROZEN");
    }

    @Test
    void shouldMapFreezeStateConflictToConflict() {
        ResponseEntity<ProblemDetail> response =
                controller.handleFreezeStateConflict(
                        new MembershipFreezeStateConflictException(
                                MEMBERSHIP_ID,
                                MembershipStatus.EXPIRED));

        assertConflict(
                response,
                "MEMBERSHIP_FREEZE_STATE_CONFLICT");
    }

    @Test
    void shouldMapNotFrozenToConflict() {
        ResponseEntity<ProblemDetail> response =
                controller.handleNotFrozen(
                        new MembershipNotFrozenException(
                                MEMBERSHIP_ID,
                                MembershipStatus.ACTIVE));

        assertConflict(
                response,
                "MEMBERSHIP_NOT_FROZEN");
    }

    @Test
    void shouldMapMissingOpenFreezeToNotFound() {
        ResponseEntity<ProblemDetail> response =
                controller.handleFreezeNotFound(
                        new MembershipFreezeNotFoundException(
                                MEMBERSHIP_ID));

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody()
                .getProperties())
                .containsEntry(
                        "code",
                        "MEMBERSHIP_FREEZE_NOT_FOUND");
    }

    private static void assertConflict(
            ResponseEntity<ProblemDetail> response,
            String expectedCode) {

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody()
                .getProperties())
                .containsEntry(
                        "code",
                        expectedCode);
    }
}
