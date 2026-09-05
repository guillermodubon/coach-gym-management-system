package io.github.guillermodubon.coachgym.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.maintenance.IncidentDetails;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentLookupServiceTest {

    @Mock private IncidentStore incidentStore;

    @Test
    void delegatesPublicIncidentLookupToExistingStore() {
        UUID incidentId = UUID.randomUUID();
        IncidentDetails details = org.mockito.Mockito.mock(IncidentDetails.class);
        when(incidentStore.findById(incidentId)).thenReturn(Optional.of(details));

        IncidentLookupService service = new IncidentLookupService(incidentStore);

        assertThat(service.findById(incidentId)).containsSame(details);
        verify(incidentStore).findById(incidentId);
    }

    @Test
    void nullIdentifierReturnsEmptyWithoutStoreAccess() {
        IncidentLookupService service = new IncidentLookupService(incidentStore);
        assertThat(service.findById(null)).isEmpty();
    }
}
