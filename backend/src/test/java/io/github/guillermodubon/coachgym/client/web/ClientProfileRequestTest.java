package io.github.guillermodubon.coachgym.client.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ClientProfileRequestTest {

    @Test
    void mapsUpdateRequestWithoutExposingImmutableFields() {
        UpdateClientProfileRequest request = new UpdateClientProfileRequest(
                "Ana",
                "Lopez",
                "ana@example.com",
                "7012-3456",
                LocalDate.of(1995, 4, 10),
                new EmergencyContactProfileRequest(
                        "Maria Lopez", "Sister", "7000-0000"),
                3L);

        var command = request.toCommand();

        assertThat(command.firstName()).isEqualTo("Ana");
        assertThat(command.emergencyContact().fullName())
                .isEqualTo("Maria Lopez");
        assertThat(command.expectedVersion()).isEqualTo(3);
    }

    @Test
    void mapsLifecycleRequestToBothAdministrativeCommands() {
        ClientLifecycleRequest request =
                new ClientLifecycleRequest("Administrative review", 2L);

        assertThat(request.toDeactivateCommand().reason())
                .isEqualTo("Administrative review");
        assertThat(request.toReactivateCommand().expectedVersion())
                .isEqualTo(2);
    }
}
