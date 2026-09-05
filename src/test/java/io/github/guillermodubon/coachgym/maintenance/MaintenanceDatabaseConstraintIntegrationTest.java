package io.github.guillermodubon.coachgym.maintenance;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaintenanceDatabaseConstraintIntegrationTest
        extends AbstractMaintenanceApiIntegrationTest {

    @Test
    void databaseRejectsInvalidCurrencyAndNegativeCost() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.maintenances
                    (id,equipment_id,maintenance_type,status,scheduled_on,
                     estimated_cost,currency,created_by_user_id,version)
                values (?,?,'PREVENTIVE','SCHEDULED',date '2026-09-10',-1,'US',?,0)
                """, UUID.randomUUID(), equipmentId, adminId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
