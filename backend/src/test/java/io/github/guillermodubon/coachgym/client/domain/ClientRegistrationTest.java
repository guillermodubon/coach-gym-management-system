package io.github.guillermodubon.coachgym.client.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ClientRegistrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 13);

    @Test
    void normalizesRegistrationValuesAndEmail() {
        ClientRegistration registration = ClientRegistration.create(
                " Ana ",
                " Martínez ",
                " ANA@EXAMPLE.COM ",
                " +50370000000 ",
                LocalDate.of(1995, 4, 12),
                new EmergencyContactRegistration(" Carlos Martínez ", " Brother ", " +50371111111 "),
                TODAY);

        assertThat(registration.firstName()).isEqualTo("Ana");
        assertThat(registration.lastName()).isEqualTo("Martínez");
        assertThat(registration.email()).isEqualTo("ana@example.com");
        assertThat(registration.phone()).isEqualTo("+50370000000");
        assertThat(registration.emergencyContact().fullName()).isEqualTo("Carlos Martínez");
    }

    @Test
    void rejectsFutureDateOfBirth() {
        assertThatThrownBy(() -> ClientRegistration.create(
                        "Ana",
                        "Martínez",
                        null,
                        "+50370000000",
                        TODAY.plusDays(1),
                        null,
                        TODAY))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("Date of birth cannot be in the future.");
    }

    @Test
    void rejectsBlankRequiredFields() {
        assertThatThrownBy(() -> ClientRegistration.create(
                        " ",
                        "Martínez",
                        null,
                        "+50370000000",
                        null,
                        null,
                        TODAY))
                .isInstanceOf(ClientValidationException.class)
                .hasMessage("First name must not be blank.");
    }
}
