package io.github.guillermodubon.coachgym.accesscredential;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

class AccessCredentialQrArchitectureTest {

    private static final JavaClasses PROJECT_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.guillermodubon.coachgym");

    @Test
    void QRLibraryIsConfinedToTheInfrastructureAdapter() {
        noClasses()
                .that()
                .resideOutsideOfPackage("..accesscredential.infrastructure..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.google.zxing..")
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

    @Test
    void lifecycleModuleDoesNotDependOnCheckInInternals() {
        noClasses()
                .that()
                .resideInAPackage("..accesscredential..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..access.application..",
                        "..access.infrastructure..",
                        "..access.web..")
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

    @Test
    void lifecycleModuleDoesNotExposeQrCheckInEndpoint() {
        boolean checkInEndpointPresent = PROJECT_CLASSES.stream()
                .filter(javaClass -> javaClass.getPackageName()
                        .startsWith("io.github.guillermodubon.coachgym.accesscredential"))
                .flatMap(javaClass -> javaClass.getAllMethods().stream())
                .flatMap(method -> method.tryGetAnnotationOfType(PostMapping.class).stream())
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .anyMatch(path -> path.toLowerCase(java.util.Locale.ROOT).contains("check-in"));

        assertThat(checkInEndpointPresent).isFalse();
    }
}
