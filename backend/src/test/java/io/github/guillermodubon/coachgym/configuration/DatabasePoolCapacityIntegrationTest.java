package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.datasource.hikari.connection-timeout=5000",
        "spring.datasource.hikari.validation-timeout=3000"
})
class DatabasePoolCapacityIntegrationTest {

    private static final int POOL_CAPACITY = 5;
    private static final int CONCURRENT_REQUESTS = 12;
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Test
    void requestsBeyondThePoolCapacityWaitAndCompleteWithoutLeakingConnections()
            throws Exception {
        HikariDataSource hikari = (HikariDataSource) dataSource;
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(POOL_CAPACITY);

        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch holdersAcquired = new CountDownLatch(POOL_CAPACITY);
        CountDownLatch releaseHolders = new CountDownLatch(1);
        AtomicInteger activeRequests = new AtomicInteger();
        AtomicInteger peakActiveRequests = new AtomicInteger();
        List<Future<Boolean>> requests = new ArrayList<>(CONCURRENT_REQUESTS);

        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            for (int request = 0; request < CONCURRENT_REQUESTS; request++) {
                boolean holdConnection = request < POOL_CAPACITY;
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent pool test did not start.");
                    }

                    try (Connection connection = dataSource.getConnection()) {
                        int active = activeRequests.incrementAndGet();
                        peakActiveRequests.accumulateAndGet(active, Math::max);
                        try {
                            if (holdConnection) {
                                holdersAcquired.countDown();
                                if (!releaseHolders.await(10, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException(
                                            "Pool holders were not released.");
                                }
                            }

                            try (Statement statement = connection.createStatement()) {
                                statement.execute("select pg_sleep(0.05)");
                            }
                            return true;
                        } finally {
                            activeRequests.decrementAndGet();
                        }
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(holdersAcquired.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(hikari.getHikariPoolMXBean().getActiveConnections())
                    .isEqualTo(POOL_CAPACITY);
            releaseHolders.countDown();

            for (Future<Boolean> request : requests) {
                assertThat(request.get(20, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            releaseHolders.countDown();
        }

        assertThat(peakActiveRequests.get())
                .isGreaterThan(1)
                .isLessThanOrEqualTo(POOL_CAPACITY);
        assertThat(hikari.getHikariPoolMXBean().getActiveConnections()).isZero();
    }
}
