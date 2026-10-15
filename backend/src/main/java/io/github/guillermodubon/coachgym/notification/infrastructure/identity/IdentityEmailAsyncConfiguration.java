package io.github.guillermodubon.coachgym.notification.infrastructure.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Bounded executor for best-effort identity email work, preventing request-thread timing leaks. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class IdentityEmailAsyncConfiguration {

    @Bean("identityEmailTaskExecutor")
    ThreadPoolTaskExecutor identityEmailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("identity-email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
