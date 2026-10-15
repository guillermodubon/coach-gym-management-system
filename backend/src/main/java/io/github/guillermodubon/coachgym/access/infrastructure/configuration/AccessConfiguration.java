package io.github.guillermodubon.coachgym.access.infrastructure.configuration;

import io.github.guillermodubon.coachgym.access.domain.DuplicateScanPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccessProperties.class)
class AccessConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "gym.access",
            name = "duplicate-scan-window")
    DuplicateScanPolicy duplicateScanPolicy(AccessProperties properties) {
        return new DuplicateScanPolicy(properties.duplicateScanWindow());
    }
}
