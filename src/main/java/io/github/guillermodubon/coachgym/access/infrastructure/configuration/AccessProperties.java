package io.github.guillermodubon.coachgym.access.infrastructure.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Optional operational access settings supplied by the deployment. */
@ConfigurationProperties(prefix = "gym.access")
record AccessProperties(Duration duplicateScanWindow) {
}
