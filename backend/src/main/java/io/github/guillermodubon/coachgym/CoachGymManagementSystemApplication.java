package io.github.guillermodubon.coachgym;

import org.springframework.boot.SpringApplication;
import org.springframework.modulith.Modulith;

@Modulith(systemName = "Coach Gym Management System", sharedModules = "shared")
public class CoachGymManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoachGymManagementSystemApplication.class, args);
    }

}
