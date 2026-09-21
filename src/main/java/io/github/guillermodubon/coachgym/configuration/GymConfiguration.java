package io.github.guillermodubon.coachgym.configuration;

import java.time.Clock;
import java.time.ZoneId;
import io.github.guillermodubon.coachgym.shared.storage.SupabaseStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        GymProperties.class,
        InitialAdminProperties.class,
        DatabasePoolProperties.class,
        SupabaseStorageProperties.class})
class GymConfiguration {

    @Bean
    Clock gymClock(GymProperties gymProperties) {
        return Clock.system(ZoneId.of(gymProperties.timeZone()));
    }

}
