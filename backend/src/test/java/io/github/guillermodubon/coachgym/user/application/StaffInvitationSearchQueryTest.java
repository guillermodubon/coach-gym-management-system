package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffInvitationStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffInvitationSearchQueryTest {

    @Test
    void normalizesAndRedactsEmailSearchAndUsesBoundedDefaults() {
        StaffInvitationSearchQuery query = new StaffInvitationSearchQuery(
                StaffInvitationStatus.PENDING, RoleCode.ADMIN, StaffScopeType.BRANCH,
                UUID.randomUUID(), "  ADMIN@EXAMPLE  ", null, null, null, null,
                0, 25, null, null);

        assertThat(query.emailQuery()).isEqualTo("admin@example");
        assertThat(query.sort()).isEqualTo(StaffInvitationSortField.CREATED_AT);
        assertThat(query.direction()).isEqualTo(StaffInvitationSortDirection.DESC);
        assertThat(query.toString()).doesNotContain("admin@example");
    }

    @Test
    void rejectsWildcardAndUnboundedEmailSearch() {
        assertThatThrownBy(() -> query("adm%"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> query("ab"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> query("a".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidDateRangesAndExcessiveOffsets() {
        Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
        Instant later = Instant.parse("2026-02-01T00:00:00Z");

        assertThatThrownBy(() -> new StaffInvitationSearchQuery(
                null, null, null, null, null, later, earlier, null, null,
                0, 25, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaffInvitationSearchQuery(
                null, null, null, null, null, null, null, later, earlier,
                0, 25, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaffInvitationSearchQuery(
                null, null, null, null, null, null, null, null, null,
                101, 100, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StaffInvitationSearchQuery query(String email) {
        return new StaffInvitationSearchQuery(
                null, null, null, null, email, null, null, null, null,
                0, 25, null, null);
    }
}
