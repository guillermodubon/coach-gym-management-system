package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ActuatorConfigurationContractTest {

    private static final Path CONFIGURATION = Path.of("src/main/resources/application.yml");

    @Test
    void exposesOnlySafeHealthAndInfoEndpointsWithProbeGroups() throws Exception {
        String yaml = managementSection();

        assertThat(yaml)
                .contains("include: ${management_endpoints_web_exposure_include:health,info}")
                .contains("probes: enabled: true")
                .contains("show-details: never")
                .contains("show-components: never")
                .contains("liveness: include: livenessstate")
                .contains("readiness: include: readinessstate,db,storage,email,stripe")
                .doesNotContain("env", "beans", "configprops", "mappings", "loggers", "heapdump");
    }

    @Test
    void includesCorrelationIdInTheApplicationLogPatternWithoutRequestBodies() throws Exception {
        assertThat(loggingSection())
                .contains("pattern: level: \"%5p [corr=%x{correlation_id:-}]\"")
                .doesNotContain("request_body", "response_body", "authorization", "cookie");
    }

    private static String normalized() throws Exception {
        return Files.readString(CONFIGURATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static String managementSection() throws Exception {
        String yaml = normalized();
        int start = yaml.lastIndexOf(" management:");
        int end = yaml.indexOf(" logging:", start);
        return yaml.substring(start, end < 0 ? yaml.length() : end);
    }

    private static String loggingSection() throws Exception {
        String yaml = normalized();
        int start = yaml.lastIndexOf(" logging:");
        int end = yaml.indexOf(" server:", start);
        return yaml.substring(start, end < 0 ? yaml.length() : end);
    }
}
