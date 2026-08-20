package io.github.guillermodubon.coachgym.membership.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.application.MembershipApplicationService;
import io.github.guillermodubon.coachgym.membership.application.MembershipFreezeApplicationService;
import io.github.guillermodubon.coachgym.membership.application.MembershipNotRenewableException;
import io.github.guillermodubon.coachgym.membership.application.MembershipVersionConflictException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class MembershipRenewalErrorMappingTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "60716b73-f2e8-4198-acd7-316e02631c4b");

    private final MembershipController controller =
            new MembershipController(
                    Mockito.mock(
                            MembershipApplicationService.class),
                    Mockito.mock(
                            MembershipFreezeApplicationService.class));

    @Test
    void mapsNotRenewableStateToConflict() {
        ResponseEntity<ProblemDetail> response =
                controller.handleNotRenewable(
                        new MembershipNotRenewableException(
                                MEMBERSHIP_ID,
                                MembershipStatus.FROZEN));

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(
                response.getBody()
                        .getProperties())
                .containsEntry(
                        "code",
                        "MEMBERSHIP_NOT_RENEWABLE");
    }

    @Test
    void mapsVersionConflictToConflict() {
        ResponseEntity<ProblemDetail> response =
                controller.handleVersionConflict(
                        new MembershipVersionConflictException(
                                MEMBERSHIP_ID,
                                1,
                                2));

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(
                response.getBody()
                        .getProperties())
                .containsEntry(
                        "code",
                        "MEMBERSHIP_VERSION_CONFLICT");
    }
}