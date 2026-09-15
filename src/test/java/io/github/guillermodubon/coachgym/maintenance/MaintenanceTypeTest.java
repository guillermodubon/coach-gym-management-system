package io.github.guillermodubon.coachgym.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaintenanceTypeTest {

    @Test
    void exposesOnlyDatabaseSupportedTypes() {
        assertThat(MaintenanceType.values())
                .containsExactly(
                        MaintenanceType.PREVENTIVE,
                        MaintenanceType.CORRECTIVE);
    }
}
