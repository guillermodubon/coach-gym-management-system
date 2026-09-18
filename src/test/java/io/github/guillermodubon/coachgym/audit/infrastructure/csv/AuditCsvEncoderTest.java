package io.github.guillermodubon.coachgym.audit.infrastructure.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuditCsvEncoderTest {

    private final AuditCsvEncoder encoder = new AuditCsvEncoder();
    private final AuditCsvCellSafetyPolicy cellSafetyPolicy =
            new AuditCsvCellSafetyPolicy();

    @Test
    void writesTheExactFixedHeaderAsUtf8WithoutBom() throws IOException {
        StringWriter writer = new StringWriter();

        new AuditCsvHeaderWriter().write(writer);

        assertThat(writer.toString()).isEqualTo(
                "entry_id,occurred_at,actor_user_id,actor_identifier,"
                        + "action_code,resource_type,resource_id,resource_code,"
                        + "summary,correlation_id,metadata\r\n");
        byte[] bytes = writer.toString().getBytes(StandardCharsets.UTF_8);
        assertThat(bytes).isNotEmpty();
        assertThat(bytes[0]).isNotEqualTo((byte) 0xef);
    }

    @Test
    void quotesRfcSensitiveFieldsAndPreservesUnicode() {
        assertThat(encoder.encode("plain")).isEqualTo("plain");
        assertThat(encoder.encode("a,b")).isEqualTo("\"a,b\"");
        assertThat(encoder.encode("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(encoder.encode("line1\r\nline2")).isEqualTo("\"line1\r\nline2\"");
        assertThat(encoder.encode("áéñ 東京")).isEqualTo("áéñ 東京");
        assertThat(encoder.encode(null)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("formulaValues")
    void neutralizesFormulaPrefixesAfterWhitespaceAndControls(String value) {
        assertThat(cellSafetyPolicy.protectText(value)).startsWith("'");
        assertThat(encoder.encode(value)).contains("'");
    }

    @Test
    void leavesTypedNegativeNumbersUnchangedButProtectsTextualFormulaValues() {
        assertThat(encoder.encode(-10)).isEqualTo("-10");
        assertThat(encoder.encode(-10.25)).isEqualTo("-10.25");
        assertThat(encoder.encode("-10+20")).isEqualTo("'-10+20");
        assertThat(encoder.encode("  =HYPERLINK(\"https://example.invalid\")"))
                .contains("'  =HYPERLINK(");
    }

    @Test
    void boundsIndividualCellsAndDoesNotCloseCallerOwnedWriter() throws IOException {
        StringWriter writer = new StringWriter();
        String value = "x".repeat(AuditCsvCellSafetyPolicy.MAX_CELL_LENGTH + 10);

        encoder.writeCell(writer, value);

        assertThat(writer.toString()).hasSize(AuditCsvCellSafetyPolicy.MAX_CELL_LENGTH);

        CloseTrackingWriter trackingWriter = new CloseTrackingWriter();
        encoder.writeCell(trackingWriter, "value");
        assertThat(trackingWriter.closed).isFalse();
    }

    static Stream<Arguments> formulaValues() {
        return Stream.of(
                Arguments.of("=1+1"),
                Arguments.of("+SUM(A1:A2)"),
                Arguments.of("-10+20"),
                Arguments.of("@SUM(1,1)"),
                Arguments.of("  =HYPERLINK(\"https://example.invalid\")"),
                Arguments.of("\t=cmd"),
                Arguments.of("\r\n@cmd"),
                Arguments.of("\ufeff=cmd"));
    }

    private static final class CloseTrackingWriter extends StringWriter {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
