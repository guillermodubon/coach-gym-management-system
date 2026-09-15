package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RoleCodeTest {

    @Test
    void v1ContainsExactlyAdminAndReceptionist() {
        assertThat(Arrays.asList(RoleCode.values()))
                .containsExactly(
                        RoleCode.ADMIN,
                        RoleCode.RECEPTIONIST);
    }

    @Test
    void maintenanceIsNotASupportedStaffRole() {
        assertThatThrownBy(() -> RoleCode.valueOf("MAINTENANCE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
