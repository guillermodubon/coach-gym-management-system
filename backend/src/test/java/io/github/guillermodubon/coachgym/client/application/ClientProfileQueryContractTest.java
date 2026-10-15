package io.github.guillermodubon.coachgym.client.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.client.ClientPage;
import io.github.guillermodubon.coachgym.client.ClientSearchQuery;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientProfileQueryContractTest {

    @Test
    void exposesReadOnlySearchAndProfilePorts() throws Exception {
        assertThat(ClientSearchStore.class.isInterface()).isTrue();
        assertThat(ClientSearchStore.class
                .getMethod("findAll", ClientSearchQuery.class)
                .getReturnType()).isEqualTo(ClientPage.class);
        assertThat(ClientOperationalProfileQuery.class.isInterface()).isTrue();
        assertThat(ClientOperationalProfileQuery.class
                .getMethod("findById", UUID.class)
                .getReturnType()).isEqualTo(Optional.class);
    }
}
