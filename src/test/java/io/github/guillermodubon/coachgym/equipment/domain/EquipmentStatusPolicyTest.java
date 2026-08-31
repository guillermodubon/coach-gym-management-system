package io.github.guillermodubon.coachgym.equipment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipmentStatusPolicyTest {

    private static final String REASON = "Scheduled maintenance check";

    // ── Allowed transitions ───────────────────────────────────────────────────

    @Test
    void available_to_outOfService_isAllowed() {
        EquipmentStatusTransition t = EquipmentStatusPolicy.validate(
                EquipmentStatus.AVAILABLE, EquipmentStatus.OUT_OF_SERVICE, REASON);
        assertThat(t.from()).isEqualTo(EquipmentStatus.AVAILABLE);
        assertThat(t.to()).isEqualTo(EquipmentStatus.OUT_OF_SERVICE);
        assertThat(t.reason()).isEqualTo(REASON);
    }

    @Test
    void outOfService_to_available_isAllowed() {
        EquipmentStatusTransition t = EquipmentStatusPolicy.validate(
                EquipmentStatus.OUT_OF_SERVICE, EquipmentStatus.AVAILABLE, REASON);
        assertThat(t.from()).isEqualTo(EquipmentStatus.OUT_OF_SERVICE);
        assertThat(t.to()).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    void available_to_retired_isAllowed() {
        EquipmentStatusTransition t = EquipmentStatusPolicy.validate(
                EquipmentStatus.AVAILABLE, EquipmentStatus.RETIRED, REASON);
        assertThat(t.to()).isEqualTo(EquipmentStatus.RETIRED);
    }

    @Test
    void outOfService_to_retired_isAllowed() {
        EquipmentStatusTransition t = EquipmentStatusPolicy.validate(
                EquipmentStatus.OUT_OF_SERVICE, EquipmentStatus.RETIRED, REASON);
        assertThat(t.to()).isEqualTo(EquipmentStatus.RETIRED);
    }

    // ── RETIRED is terminal ───────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "AVAILABLE",
        "OUT_OF_SERVICE",
        "MAINTENANCE",
        "RETIRED"
    })
    void retired_to_anything_isRejected(EquipmentStatus target) {
        assertThatThrownBy(() -> EquipmentStatusPolicy.validate(
                EquipmentStatus.RETIRED, target, REASON))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("terminal");
    }

    // ── Same-status is rejected ───────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "AVAILABLE",
        "OUT_OF_SERVICE",
        "MAINTENANCE"
    })
    void sameStatus_isRejected(EquipmentStatus status) {
        assertThatThrownBy(() -> EquipmentStatusPolicy.validate(status, status, REASON))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("already in status");
    }

    // ── MAINTENANCE transitions are reserved ──────────────────────────────────

    @Test
    void available_to_maintenance_isRejected() {
        assertThatThrownBy(() -> EquipmentStatusPolicy.validate(
                EquipmentStatus.AVAILABLE, EquipmentStatus.MAINTENANCE, REASON))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("MAINTENANCE");
    }

    @Test
    void maintenance_to_available_isRejected() {
        assertThatThrownBy(() -> EquipmentStatusPolicy.validate(
                EquipmentStatus.MAINTENANCE, EquipmentStatus.AVAILABLE, REASON))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("MAINTENANCE");
    }

    @Test
    void maintenance_to_outOfService_isRejected() {
        assertThatThrownBy(() -> EquipmentStatusPolicy.validate(
                EquipmentStatus.MAINTENANCE, EquipmentStatus.OUT_OF_SERVICE, REASON))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("MAINTENANCE");
    }

    @Test
    void maintenance_to_retired_isRejected() {
        assertThatThrownBy(() -> EquipmentStatusPolicy.validate(
                EquipmentStatus.MAINTENANCE, EquipmentStatus.RETIRED, REASON))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("MAINTENANCE");
    }

    @Test
    void outOfService_to_maintenance_isRejected() {
        assertThatThrownBy(() -> EquipmentStatusPolicy.validate(
                EquipmentStatus.OUT_OF_SERVICE, EquipmentStatus.MAINTENANCE, REASON))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("MAINTENANCE");
    }

    // ── Null inputs ───────────────────────────────────────────────────────────

    @Test
    void nullCurrentStatus_isRejected() {
        assertThatThrownBy(() -> EquipmentStatusPolicy.validate(
                null, EquipmentStatus.OUT_OF_SERVICE, REASON))
                .isInstanceOf(EquipmentValidationException.class);
    }

    @Test
    void nullTargetStatus_isRejected() {
        assertThatThrownBy(() -> EquipmentStatusPolicy.validate(
                EquipmentStatus.AVAILABLE, null, REASON))
                .isInstanceOf(EquipmentValidationException.class);
    }

    // ── Reason validation ─────────────────────────────────────────────────────

    @Test
    void blankReason_isRejected() {
        assertThatThrownBy(() -> EquipmentStatusPolicy.validate(
                EquipmentStatus.AVAILABLE, EquipmentStatus.OUT_OF_SERVICE, "   "))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void nullReason_isRejected() {
        assertThatThrownBy(() -> EquipmentStatusPolicy.validate(
                EquipmentStatus.AVAILABLE, EquipmentStatus.OUT_OF_SERVICE, null))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void reason_isTrimmed() {
        EquipmentStatusTransition t = EquipmentStatusPolicy.validate(
                EquipmentStatus.AVAILABLE, EquipmentStatus.OUT_OF_SERVICE, "  trim me  ");
        assertThat(t.reason()).isEqualTo("trim me");
    }

    @Test
    void reasonExceedingMaxLength_isRejected() {
        String longReason = "a".repeat(2001);
        assertThatThrownBy(() -> EquipmentStatusPolicy.validate(
                EquipmentStatus.AVAILABLE, EquipmentStatus.OUT_OF_SERVICE, longReason))
                .isInstanceOf(EquipmentValidationException.class)
                .hasMessageContaining("2000");
    }

    @Test
    void reasonAtMaxLength_isAccepted() {
        String maxReason = "a".repeat(2000);
        EquipmentStatusTransition t = EquipmentStatusPolicy.validate(
                EquipmentStatus.AVAILABLE, EquipmentStatus.OUT_OF_SERVICE, maxReason);
        assertThat(t.reason()).hasSize(2000);
    }
}
