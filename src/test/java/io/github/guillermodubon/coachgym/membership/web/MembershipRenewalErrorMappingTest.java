package io.github.guillermodubon.coachgym.membership.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.application.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class MembershipRenewalErrorMappingTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "60716b73-f2e8-4198-acd7-316e02631c4b");

        @Mock
        private MembershipApplicationService
                membershipApplicationService;

        @Mock
        private MembershipFreezeApplicationService
                membershipFreezeApplicationService;

        @Mock
        private MembershipCancellationApplicationService
                membershipCancellationApplicationService;

        private MembershipController controller;

        @BeforeEach
        void setUp() {
            controller =
                    new MembershipController(
                            membershipApplicationService,
                            membershipFreezeApplicationService,
                            membershipCancellationApplicationService);
        }

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