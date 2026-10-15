package io.github.guillermodubon.coachgym.client.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UpdateClientCommandTest {

    @Test
    void normalizesMutableClientData() {
        UpdateClientCommand command = new UpdateClientCommand(
                " Ana ", " López ", " ANA@EXAMPLE.TEST ", " 7000-0000 ",
                LocalDate.of(1995, 5, 10),
                new UpdateEmergencyContactCommand(
                        " José López ", " Father ", " 7111-1111 "),
                3L);

        assertThat(command.firstName()).isEqualTo("Ana");
        assertThat(command.lastName()).isEqualTo("López");
        assertThat(command.email()).isEqualTo("ana@example.test");
        assertThat(command.phone()).isEqualTo("7000-0000");
        assertThat(command.expectedVersion()).isEqualTo(3L);
        assertThat(command.emergencyContact().fullName()).isEqualTo("José López");
    }

    @Test
    void acceptsOptionalEmailBirthDateAndEmergencyContact() {
        UpdateClientCommand command = new UpdateClientCommand(
                "Ana", "López", null, "7000-0000", null, null, 0L);

        assertThat(command.email()).isNull();
        assertThat(command.dateOfBirth()).isNull();
        assertThat(command.emergencyContact()).isNull();
    }

    @Test
    void rejectsBlankInvalidOrFutureData() {
        assertThatThrownBy(() -> new UpdateClientCommand(
                " ", "López", null, "7000-0000", null, null, 0L))
                .isInstanceOf(ClientValidationException.class);
        assertThatThrownBy(() -> new UpdateClientCommand(
                "Ana", "López", "invalid", "7000-0000", null, null, 0L))
                .isInstanceOf(ClientValidationException.class);
        assertThatThrownBy(() -> new UpdateClientCommand(
                "Ana", "López", null, "7000-0000",
                LocalDate.now().plusDays(1), null, 0L))
                .isInstanceOf(ClientValidationException.class);
        assertThatThrownBy(() -> new UpdateClientCommand(
                "Ana", "López", null, "7000-0000", null, null, -1L))
                .isInstanceOf(ClientValidationException.class);
    }
}
