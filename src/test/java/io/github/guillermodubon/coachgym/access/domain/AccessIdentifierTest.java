package io.github.guillermodubon.coachgym.access.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccessIdentifierTest {

    // ── Normalisation ─────────────────────────────────────────────────────────

    @Test
    void stripsLeadingAndTrailingWhitespace() {
        AccessIdentifier id = AccessIdentifier.of("  MEM-000001  ");
        assertThat(id.value()).isEqualTo("MEM-000001");
    }

    @Test
    void convertsToUpperCase() {
        AccessIdentifier id = AccessIdentifier.of("mem-000001");
        assertThat(id.value()).isEqualTo("MEM-000001");
    }

    @Test
    void stripsAndUpperCasesTogether() {
        AccessIdentifier id = AccessIdentifier.of("  cli-000042 ");
        assertThat(id.value()).isEqualTo("CLI-000042");
    }

    @Test
    void preservesAlreadyNormalisedValue() {
        AccessIdentifier id = AccessIdentifier.of("MEM-000001");
        assertThat(id.value()).isEqualTo("MEM-000001");
    }

    // ── Type inference ────────────────────────────────────────────────────────

    @Test
    void infersMembershipCodeTypeForMemPrefix() {
        AccessIdentifier id = AccessIdentifier.of("MEM-000001");
        assertThat(id.type()).isEqualTo(AccessIdentifierType.MEMBERSHIP_CODE);
    }

    @Test
    void infersClientCodeTypeForCliPrefix() {
        AccessIdentifier id = AccessIdentifier.of("CLI-000001");
        assertThat(id.type()).isEqualTo(AccessIdentifierType.CLIENT_CODE);
    }

    @Test
    void infersClientCodeTypeEvenWithLowerCaseInput() {
        AccessIdentifier id = AccessIdentifier.of("cli-000001");
        assertThat(id.type()).isEqualTo(AccessIdentifierType.CLIENT_CODE);
    }

    @Test
    void infersMembershipCodeTypeEvenWithMixedCaseInput() {
        AccessIdentifier id = AccessIdentifier.of("mEm-000001");
        assertThat(id.type()).isEqualTo(AccessIdentifierType.MEMBERSHIP_CODE);
    }

    @Test
    void infersUnknownTypeForUnrecognisedPrefix() {
        AccessIdentifier id = AccessIdentifier.of("XYZ-000001");
        assertThat(id.type()).isEqualTo(AccessIdentifierType.UNKNOWN);
    }

    @Test
    void infersUnknownTypeForNumericOnly() {
        AccessIdentifier id = AccessIdentifier.of("000001");
        assertThat(id.type()).isEqualTo(AccessIdentifierType.UNKNOWN);
    }

    @Test
    void infersUnknownTypeForPartialPrefix() {
        AccessIdentifier id = AccessIdentifier.of("ME-000001");
        assertThat(id.type()).isEqualTo(AccessIdentifierType.UNKNOWN);
    }

    // ── Rejection ─────────────────────────────────────────────────────────────

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> AccessIdentifier.of(null))
                .isInstanceOf(AccessValidationException.class)
                .hasMessage("Access identifier must be provided.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    void rejectsBlankInput(String blank) {
        assertThatThrownBy(() -> AccessIdentifier.of(blank))
                .isInstanceOf(AccessValidationException.class);
    }
}
