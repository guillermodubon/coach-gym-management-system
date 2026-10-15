package io.github.guillermodubon.coachgym.equipment.application;

import io.github.guillermodubon.coachgym.equipment.domain.EquipmentValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipmentCategorySearchQueryTest {

    // ── defaults ──────────────────────────────────────────────────────────────

    @Test
    void defaults_areApplied_whenAllInputsAreNull() {
        EquipmentCategorySearchQuery q = EquipmentCategorySearchQuery.from(null, 0, 0, null, null);
        assertThat(q.page()).isZero();
        assertThat(q.size()).isEqualTo(EquipmentCategorySearchQuery.DEFAULT_SIZE);
        assertThat(q.sortField()).isEqualTo(EquipmentCategorySortField.NAME);
        assertThat(q.direction()).isEqualTo(EquipmentSortDirection.ASC);
        assertThat(q.active()).isNull();
    }

    // ── active filter ─────────────────────────────────────────────────────────

    @Test
    void activeTrue_isAccepted() {
        EquipmentCategorySearchQuery q = EquipmentCategorySearchQuery.from("true", 0, 25, null, null);
        assertThat(q.active()).isTrue();
    }

    @Test
    void activeFalse_isAccepted() {
        EquipmentCategorySearchQuery q = EquipmentCategorySearchQuery.from("false", 0, 25, null, null);
        assertThat(q.active()).isFalse();
    }

    @Test
    void active_parsedCaseInsensitive() {
        EquipmentCategorySearchQuery q = EquipmentCategorySearchQuery.from("TRUE", 0, 25, null, null);
        assertThat(q.active()).isTrue();
    }

    @Test
    void blankActive_returnsNullFilter() {
        EquipmentCategorySearchQuery q = EquipmentCategorySearchQuery.from("  ", 0, 25, null, null);
        assertThat(q.active()).isNull();
    }

    @Test
    void invalidActive_throwsValidationException() {
        assertThatThrownBy(() -> EquipmentCategorySearchQuery.from("yes", 0, 25, null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("active");
    }

    // ── pagination ────────────────────────────────────────────────────────────

    @Test
    void negativePage_throwsValidationException() {
        assertThatThrownBy(() -> EquipmentCategorySearchQuery.from(null, -1, 25, null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("Page");
    }

    @Test
    void sizeZero_defaultsToDefaultSize() {
        EquipmentCategorySearchQuery q = EquipmentCategorySearchQuery.from(null, 0, 0, null, null);
        assertThat(q.size()).isEqualTo(EquipmentCategorySearchQuery.DEFAULT_SIZE);
    }

    @Test
    void sizeExceedingMax_throwsValidationException() {
        assertThatThrownBy(() -> EquipmentCategorySearchQuery.from(null, 0, 101, null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("100");
    }

    // ── sort field ────────────────────────────────────────────────────────────

    @Test
    void invalidSortField_throwsValidationException() {
        assertThatThrownBy(() -> EquipmentCategorySearchQuery.from(null, 0, 25, "DESCRIPTION", null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("sort field");
    }

    @Test
    void idSortField_isAccepted() {
        EquipmentCategorySearchQuery q = EquipmentCategorySearchQuery.from(null, 0, 25, "ID", null);
        assertThat(q.sortField()).isEqualTo(EquipmentCategorySortField.ID);
    }

    // ── direction ─────────────────────────────────────────────────────────────

    @Test
    void invalidDirection_throwsValidationException() {
        assertThatThrownBy(() -> EquipmentCategorySearchQuery.from(null, 0, 25, null, "UP"))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("direction");
    }
}
