package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.auth.application.LoginOutcomeRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.junit.jupiter.api.Test;

class MicrometerLoginOutcomeRecorderTest {

    @Test
    void publishesExpectedCountersWithOnlyFiniteOutcomeTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("meterRegistry", registry);
        MicrometerLoginOutcomeRecorder recorder = new MicrometerLoginOutcomeRecorder(
                beanFactory.getBeanProvider(MeterRegistry.class));

        recorder.record(LoginOutcomeRecorder.Outcome.SUCCESS);
        recorder.record(LoginOutcomeRecorder.Outcome.REJECTED);
        recorder.record(LoginOutcomeRecorder.Outcome.RATE_LIMITED);

        assertThat(registry.get("coachgym.authentication.login.attempts")
                .tag("outcome", "SUCCESS").counter().count()).isEqualTo(1.0);
        assertThat(registry.getMeters()).allSatisfy(meter -> {
            assertThat(meter.getId().getTags()).extracting(Tag::getKey)
                    .containsExactly("outcome");
            assertThat(meter.getId().getTag("outcome"))
                    .isIn("SUCCESS", "REJECTED", "RATE_LIMITED");
        });
    }

    @Test
    void isNoOpWhenTheTestOrRuntimeContextHasNoMeterRegistry() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        MicrometerLoginOutcomeRecorder recorder = new MicrometerLoginOutcomeRecorder(
                beanFactory.getBeanProvider(MeterRegistry.class));

        org.assertj.core.api.Assertions.assertThatCode(
                        () -> recorder.record(LoginOutcomeRecorder.Outcome.SUCCESS))
                .doesNotThrowAnyException();
    }
}
