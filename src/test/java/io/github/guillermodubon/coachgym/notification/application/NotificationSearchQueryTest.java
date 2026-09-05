package io.github.guillermodubon.coachgym.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.notification.domain.NotificationValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationSearchQueryTest {

    @Test
    void suppliesStableInboxDefaults() {
        NotificationSearchQuery query = NotificationSearchQuery.defaults();
        assertThat(query.readFilter()).isEqualTo(NotificationReadFilter.ALL);
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(25);
        assertThat(query.sortField()).isEqualTo(NotificationSortField.CREATED_AT);
        assertThat(query.sortDirection()).isEqualTo(NotificationSortDirection.DESC);
    }

    @Test
    void parsesAllowlistedValuesCaseInsensitively() {
        assertThat(NotificationReadFilter.from("unread"))
                .isEqualTo(NotificationReadFilter.UNREAD);
        assertThat(NotificationSortField.from("created_at"))
                .isEqualTo(NotificationSortField.CREATED_AT);
        assertThat(NotificationSortDirection.from("asc"))
                .isEqualTo(NotificationSortDirection.ASC);
    }

    @Test
    void rejectsInvalidPaginationRangeAndSortValues() {
        assertThatThrownBy(() -> new NotificationSearchQuery(
                null, null, null, null, null, null, -1, 25, null, null))
                .isInstanceOf(NotificationValidationException.class);
        assertThatThrownBy(() -> new NotificationSearchQuery(
                null, null, null, null, null, null, 0, 101, null, null))
                .isInstanceOf(NotificationValidationException.class);
        assertThatThrownBy(() -> NotificationSortField.from("database_column"))
                .isInstanceOf(NotificationValidationException.class);
    }

    @Test
    void rejectsInvertedCreatedRange() {
        assertThatThrownBy(() -> new NotificationSearchQuery(
                null, null, null, null,
                Instant.parse("2026-09-06T00:00:00Z"),
                Instant.parse("2026-09-05T00:00:00Z"),
                0, 25, null, null))
                .isInstanceOf(NotificationValidationException.class);
    }
}
