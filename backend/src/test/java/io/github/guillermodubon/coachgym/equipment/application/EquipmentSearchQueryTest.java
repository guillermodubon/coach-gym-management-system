package io.github.guillermodubon.coachgym.equipment.application;

import io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipmentSearchQueryTest {

    // ── defaults ──────────────────────────────────────────────────────────────

    @Test
    void defaults_areApplied_whenAllInputsAreNull() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, null, null, 0, 0, null, null);
        assertThat(q.page()).isEqualTo(0);
        assertThat(q.size()).isEqualTo(EquipmentSearchQuery.DEFAULT_SIZE);
        assertThat(q.sortField()).isEqualTo(EquipmentSortField.NAME);
        assertThat(q.direction()).isEqualTo(EquipmentSortDirection.ASC);
        assertThat(q.categoryId()).isNull();
        assertThat(q.status()).isNull();
        assertThat(q.search()).isNull();
        assertThat(q.location()).isNull();
    }

    // ── filters ───────────────────────────────────────────────────────────────

    @Test
    void categoryId_filter_isPreserved() {
        UUID catId = UUID.randomUUID();
        EquipmentSearchQuery q = EquipmentSearchQuery.from(catId, null, null, null, 0, 25, null, null);
        assertThat(q.categoryId()).isEqualTo(catId);
    }

    @Test
    void status_filter_parsedCaseInsensitive() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, "available", null, null, 0, 25, null, null);
        assertThat(q.status()).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    void status_filter_mixedCase_isAccepted() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, "Out_Of_Service", null, null, 0, 25, null, null);
        assertThat(q.status()).isEqualTo(EquipmentStatus.OUT_OF_SERVICE);
    }

    @Test
    void nullStatus_returnsNullFilter() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, null, null, 0, 25, null, null);
        assertThat(q.status()).isNull();
    }

    @Test
    void blankStatus_returnsNullFilter() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, "  ", null, null, 0, 25, null, null);
        assertThat(q.status()).isNull();
    }

    @Test
    void invalidStatus_throwsValidationException() {
        assertThatThrownBy(() -> EquipmentSearchQuery.from(null, "BROKEN", null, null, 0, 25, null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("status");
    }

    @Test
    void search_isTrimmed() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, "  treadmill  ", null, 0, 25, null, null);
        assertThat(q.search()).isEqualTo("treadmill");
    }

    @Test
    void blankSearch_normalizesToNull() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, "   ", null, 0, 25, null, null);
        assertThat(q.search()).isNull();
    }

    @Test
    void location_isTrimmed() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, null, "  Room A  ", 0, 25, null, null);
        assertThat(q.location()).isEqualTo("Room A");
    }

    @Test
    void blankLocation_normalizesToNull() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, null, "  ", 0, 25, null, null);
        assertThat(q.location()).isNull();
    }

    // ── pagination ────────────────────────────────────────────────────────────

    @Test
    void negativePage_throwsValidationException() {
        assertThatThrownBy(() -> EquipmentSearchQuery.from(null, null, null, null, -1, 25, null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("Page");
    }

    @Test
    void zeroPage_isValid() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, null, null, 0, 25, null, null);
        assertThat(q.page()).isZero();
    }

    @Test
    void sizeZero_defaultsToDefaultSize() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, null, null, 0, 0, null, null);
        assertThat(q.size()).isEqualTo(EquipmentSearchQuery.DEFAULT_SIZE);
    }

    @Test
    void sizeExceedingMax_throwsValidationException() {
        assertThatThrownBy(() -> EquipmentSearchQuery.from(null, null, null, null, 0, 101, null, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("100");
    }

    @Test
    void sizeAtMax_isValid() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, null, null, 0, 100, null, null);
        assertThat(q.size()).isEqualTo(100);
    }

    @Test
    void sizeOne_isValid() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, null, null, 0, 1, null, null);
        assertThat(q.size()).isEqualTo(1);
    }

    // ── sort field ────────────────────────────────────────────────────────────

    @Test
    void nullSortField_defaultsToName() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, null, null, 0, 25, null, null);
        assertThat(q.sortField()).isEqualTo(EquipmentSortField.NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NAME", "CREATED_AT", "STATUS", "ID"})
    void validSortFields_areAccepted(String field) {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, null, null, 0, 25, field, null);
        assertThat(q.sortField()).isNotNull();
    }

    @Test
    void invalidSortField_throwsValidationException() {
        assertThatThrownBy(() -> EquipmentSearchQuery.from(null, null, null, null, 0, 25, "PRICE", null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("sort field");
    }

    @Test
    void sortField_parsedCaseInsensitive() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, null, null, 0, 25, "created_at", null);
        assertThat(q.sortField()).isEqualTo(EquipmentSortField.CREATED_AT);
    }

    // ── sort direction ────────────────────────────────────────────────────────

    @Test
    void nullDirection_defaultsToAsc() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, null, null, 0, 25, null, null);
        assertThat(q.direction()).isEqualTo(EquipmentSortDirection.ASC);
    }

    @Test
    void desc_direction_isAccepted() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, null, null, 0, 25, null, "DESC");
        assertThat(q.direction()).isEqualTo(EquipmentSortDirection.DESC);
    }

    @Test
    void invalidDirection_throwsValidationException() {
        assertThatThrownBy(() -> EquipmentSearchQuery.from(null, null, null, null, 0, 25, null, "SIDEWAYS"))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("direction");
    }

    @Test
    void direction_parsedCaseInsensitive() {
        EquipmentSearchQuery q = EquipmentSearchQuery.from(null, null, null, null, 0, 25, null, "desc");
        assertThat(q.direction()).isEqualTo(EquipmentSortDirection.DESC);
    }
}
