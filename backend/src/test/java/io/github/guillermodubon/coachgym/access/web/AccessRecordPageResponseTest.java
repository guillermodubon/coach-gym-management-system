package io.github.guillermodubon.coachgym.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.application.AccessRecordPage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessRecordPageResponseTest {

    private static final UUID RECORD_ID =
            UUID.fromString(
                    "40000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "50000000-0000-0000-0000-000000000001");

    private static final Instant NOW =
            Instant.parse(
                    "2026-09-15T20:00:00Z");

    @Test
    void mapsPageWithItems() {
        AccessRecordPage source =
                new AccessRecordPage(
                        List.of(deniedDetails()),
                        1,
                        10,
                        11,
                        2);

        AccessRecordPageResponse response =
                AccessRecordPageResponse.from(source);

        assertThat(response.items()).hasSize(1);

        assertThat(response.items().getFirst().id())
                .isEqualTo(RECORD_ID);

        assertThat(response.items().getFirst().result())
                .isEqualTo(AccessResult.DENIED);

        assertThat(response.items().getFirst().reasonCode())
                .isEqualTo(
                        AccessReasonCode.IDENTIFIER_NOT_FOUND);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(11);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    void mapsEmptyPage() {
        AccessRecordPage source =
                new AccessRecordPage(
                        List.of(),
                        0,
                        25,
                        0,
                        0);

        AccessRecordPageResponse response =
                AccessRecordPageResponse.from(source);

        assertThat(response.items()).isEmpty();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(25);
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void responseItemsAreImmutable() {
        AccessRecordPageResponse response =
                AccessRecordPageResponse.from(
                        new AccessRecordPage(
                                List.of(deniedDetails()),
                                0,
                                25,
                                1,
                                1));

        assertThatThrownBy(() ->
                response.items().add(
                        AccessRecordResponse.from(
                                deniedDetails())))
                .isInstanceOf(
                        UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullPage() {
        assertThatThrownBy(() ->
                AccessRecordPageResponse.from(null))
                .isInstanceOf(
                        IllegalArgumentException.class)
                .hasMessage(
                        "Access record page must be provided.");
    }

    private static AccessRecordDetails deniedDetails() {
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
}
