package io.github.guillermodubon.coachgym.configuration;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({GymProperties.class, InitialAdminProperties.class})
class GymConfiguration {

    @Bean
    Clock gymClock(GymProperties gymProperties) {
        return Clock.system(ZoneId.of(gymProperties.timeZone()));
    }
}
