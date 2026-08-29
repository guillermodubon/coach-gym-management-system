package io.github.guillermodubon.coachgym.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.domain.AccessValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccessRecordSearchQueryTest {

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    // ── Valid construction ────────────────────────────────────────────────────

    @Test
    void buildsDefaultQuery() {
        AccessRecordSearchQuery q = AccessRecordSearchQuery.from(
                null, null, null, null, null, null, null,
                0, 25, null, null);

        assertThat(q.page()).isZero();
        assertThat(q.size()).isEqualTo(25);
        assertThat(q.sortField()).isEqualTo(AccessSortField.CHECKED_IN_AT);
        assertThat(q.direction()).isEqualTo(AccessSortDirection.DESC);
        assertThat(q.clientId()).isNull();
        assertThat(q.result()).isNull();
        assertThat(q.reasonCode()).isNull();
        assertThat(q.processedByUserId()).isNull();
    }

    @Test
    void parsesCheckedInAtSortFieldExplicitly() {
        AccessRecordSearchQuery q = AccessRecordSearchQuery.from(
                null, null, null, null, null, null, null,
                0, 25, "CHECKED_IN_AT", "ASC");

        assertThat(q.sortField()).isEqualTo(AccessSortField.CHECKED_IN_AT);
        assertThat(q.direction()).isEqualTo(AccessSortDirection.ASC);
    }

    @Test
    void parsesAllFilters() {
        UUID membershipId = UUID.randomUUID();
        UUID processedBy = UUID.randomUUID();
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant until = Instant.parse("2026-09-30T23:59:59Z");

        AccessRecordSearchQuery q = AccessRecordSearchQuery.from(
                CLIENT_ID, membershipId,
                "DENIED", "CLIENT_INACTIVE",
                from, until, processedBy,
                2, 10, "CHECKED_IN_AT", "DESC");

        assertThat(q.clientId()).isEqualTo(CLIENT_ID);
        assertThat(q.membershipId()).isEqualTo(membershipId);
        assertThat(q.result()).isEqualTo(AccessResult.DENIED);
        assertThat(q.reasonCode()).isEqualTo(AccessReasonCode.CLIENT_INACTIVE);
        assertThat(q.checkedInFrom()).isEqualTo(from);
        assertThat(q.checkedInUntil()).isEqualTo(until);
        assertThat(q.processedByUserId()).isEqualTo(processedBy);
        assertThat(q.page()).isEqualTo(2);
        assertThat(q.size()).isEqualTo(10);
    }

    @Test
    void parsesResultCaseInsensitively() {
        AccessRecordSearchQuery q = AccessRecordSearchQuery.from(
                null, null, "allowed", null, null, null, null,
                0, 25, null, null);

        assertThat(q.result()).isEqualTo(AccessResult.ALLOWED);
    }

    @Test
    void parsesReasonCodeCaseInsensitively() {
        AccessRecordSearchQuery q = AccessRecordSearchQuery.from(
                null, null, null, "membership_frozen", null, null, null,
                0, 25, null, null);

        assertThat(q.reasonCode()).isEqualTo(AccessReasonCode.MEMBERSHIP_FROZEN);
    }

    @Test
    void allowsEqualCheckedInFromAndUntil() {
        Instant ts = Instant.parse("2026-09-15T12:00:00Z");
        AccessRecordSearchQuery q = AccessRecordSearchQuery.from(
                null, null, null, null, ts, ts, null,
                0, 25, null, null);

        assertThat(q.checkedInFrom()).isEqualTo(ts);
        assertThat(q.checkedInUntil()).isEqualTo(ts);
    }

    // ── Pagination validation ─────────────────────────────────────────────────

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> AccessRecordSearchQuery.from(
                null, null, null, null, null, null, null,
                -1, 25, null, null))
                .isInstanceOf(AccessValidationException.class)
                .hasMessageContaining("Page must not be negative");
    }

    @Test
    void rejectsSizeZero() {
        assertThatThrownBy(() -> AccessRecordSearchQuery.from(
                null, null, null, null, null, null, null,
                0, 0, null, null))
                .isInstanceOf(AccessValidationException.class)
                .hasMessageContaining("Page size");
    }

    @Test
    void rejectsSizeAboveMax() {
        assertThatThrownBy(() -> AccessRecordSearchQuery.from(
                null, null, null, null, null, null, null,
                0, 101, null, null))
                .isInstanceOf(AccessValidationException.class)
                .hasMessageContaining("Page size");
    }

    @Test
    void acceptsMaxSize() {
        AccessRecordSearchQuery q = AccessRecordSearchQuery.from(
                null, null, null, null, null, null, null,
                0, 100, null, null);

        assertThat(q.size()).isEqualTo(100);
    }

    @Test
    void rejectsCheckedInFromAfterUntil() {
        Instant from = Instant.parse("2026-09-30T00:00:00Z");
        Instant until = Instant.parse("2026-09-01T00:00:00Z");

        assertThatThrownBy(() -> AccessRecordSearchQuery.from(
                null, null, null, null, from, until, null,
                0, 25, null, null))
                .isInstanceOf(AccessValidationException.class)
                .hasMessageContaining("checkedInFrom");
    }

    // ── Sort validation ───────────────────────────────────────────────────────

    @Test
    void rejectsUnsupportedSortField() {
        assertThatThrownBy(() -> AccessRecordSearchQuery.from(
                null, null, null, null, null, null, null,
                0, 25, "CREATED_AT", null))
                .isInstanceOf(AccessValidationException.class)
                .hasMessageContaining("sort field");
    }

    @Test
    void rejectsUnsupportedSortDirection() {
        assertThatThrownBy(() -> AccessRecordSearchQuery.from(
                null, null, null, null, null, null, null,
                0, 25, null, "RANDOM"))
                .isInstanceOf(AccessValidationException.class)
                .hasMessageContaining("sort direction");
    }

    // ── Filter parse rejection ────────────────────────────────────────────────

    @Test
    void rejectsUnknownResultFilter() {
        assertThatThrownBy(() -> AccessRecordSearchQuery.from(
                null, null, "MAYBE", null, null, null, null,
                0, 25, null, null))
                .isInstanceOf(AccessValidationException.class)
                .hasMessageContaining("result filter");
    }

    @Test
    void rejectsUnknownReasonCodeFilter() {
        assertThatThrownBy(() -> AccessRecordSearchQuery.from(
                null, null, null, "WRONG_CODE", null, null, null,
                0, 25, null, null))
                .isInstanceOf(AccessValidationException.class)
                .hasMessageContaining("reason code filter");
    }

    @Test
    void allowsAllNineReasonCodes() {
        for (AccessReasonCode code : AccessReasonCode.values()) {
            AccessRecordSearchQuery q = AccessRecordSearchQuery.from(
                    null, null, null, code.name(), null, null, null,
                    0, 25, null, null);
            assertThat(q.reasonCode()).isEqualTo(code);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALLOWED", "DENIED"})
    void allowsBothResultValues(String value) {
        AccessRecordSearchQuery q = AccessRecordSearchQuery.from(
                null, null, value, null, null, null, null,
                0, 25, null, null);
        assertThat(q.result()).isNotNull();
    }
}
