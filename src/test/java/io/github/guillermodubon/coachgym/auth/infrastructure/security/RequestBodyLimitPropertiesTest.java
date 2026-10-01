package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestBodyLimitPropertiesTest {

    @Test
    void suppliesBoundedDefaultsWhenTheBinderLeavesPrimitiveValuesUnset() {
        RequestBodyLimitProperties properties =
                new RequestBodyLimitProperties(0, 0, 0);

        assertThat(properties.maxJsonBodyBytes()).isEqualTo(1_048_576);
        assertThat(properties.maxFormBodyBytes()).isEqualTo(65_536);
        assertThat(properties.maxMultipartBodyBytes()).isEqualTo(8 * 1_048_576);
        assertThat(properties.isValid()).isTrue();
    }

    @Test
    void acceptsConfiguredValuesAtTheirDocumentedBounds() {
        assertThat(new RequestBodyLimitProperties(
                1_024, 1_024, 1_024).isValid()).isTrue();
        assertThat(new RequestBodyLimitProperties(
                8 * 1_048_576, 1_048_576, 16 * 1_048_576).isValid()).isTrue();
    }

    @Test
    void rejectsValuesOutsideBoundsAndJsonLimitAboveMultipartLimit() {
        assertThat(new RequestBodyLimitProperties(1_023, 1_024, 1_024).isValid()).isFalse();
        assertThat(new RequestBodyLimitProperties(1_024, 1_023, 1_024).isValid()).isFalse();
        assertThat(new RequestBodyLimitProperties(1_024, 1_024, 1_023).isValid()).isFalse();
        assertThat(new RequestBodyLimitProperties(
                8 * 1_048_576 + 1, 1_024, 16 * 1_048_576).isValid()).isFalse();
        assertThat(new RequestBodyLimitProperties(
                1_024, 1_048_576 + 1, 16 * 1_048_576).isValid()).isFalse();
        assertThat(new RequestBodyLimitProperties(
                1_024, 1_024, 16 * 1_048_576 + 1).isValid()).isFalse();
        assertThat(new RequestBodyLimitProperties(
                2_048, 1_024, 1_024).isValid()).isFalse();
    }
}
