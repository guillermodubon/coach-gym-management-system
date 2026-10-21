package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StaffDirectorySearchQueryTest {

    @Test
    void normalizesWhitespaceAndUsesSafeDefaults() {
        StaffDirectorySearchQuery query = new StaffDirectorySearchQuery(
                "  ADA   Lovelace ", null, null, null, null, 0, 25, null, null);

        assertThat(query.search()).isEqualTo("ada lovelace");
        assertThat(query.sort()).isEqualTo(StaffDirectorySortField.LAST_NAME);
        assertThat(query.direction()).isEqualTo(StaffDirectorySortDirection.ASC);
    }

    @Test
    void acceptsLiteralSqlWildcardCharactersAndTreatsWhitespaceOnlyAsNoFilter() {
        StaffDirectorySearchQuery literal = new StaffDirectorySearchQuery(
                "%_\\", null, null, null, null, 0, 25, null, null);
        StaffDirectorySearchQuery blank = new StaffDirectorySearchQuery(
                "   ", null, null, null, null, 0, 25, null, null);

        assertThat(literal.search()).isEqualTo("%_\\");
        assertThat(blank.search()).isNull();
    }

    @Test
    void rejectsUnboundedOrUnsafeSearchAndPageValues() {
        assertThatThrownBy(() -> query("x", -1, 25))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> query("x", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> query("x", 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> query("x".repeat(121), 0, 25))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> query("first\nlast", 0, 25))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StaffDirectorySearchQuery query(String search, int page, int size) {
        return new StaffDirectorySearchQuery(
                search, null, null, null, null, page, size, null, null);
    }
}
