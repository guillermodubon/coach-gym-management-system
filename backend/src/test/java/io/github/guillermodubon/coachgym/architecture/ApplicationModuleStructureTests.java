package io.github.guillermodubon.coachgym.architecture;

import io.github.guillermodubon.coachgym.CoachGymManagementSystemApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModuleStructureTests {

    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(CoachGymManagementSystemApplication.class).verify();
    }
}
