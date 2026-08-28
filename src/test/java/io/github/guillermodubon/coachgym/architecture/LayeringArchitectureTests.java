package io.github.guillermodubon.coachgym.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class LayeringArchitectureTests {

    private static final JavaClasses PROJECT_CLASSES = new ClassFileImporter()
            .importPackages("io.github.guillermodubon.coachgym");

    @Test
    void webLayerDoesNotDependOnInfrastructureLayer() {
        noClasses()
                .that()
                .resideInAPackage("..web..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..")
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

    @Test
    void membershipDoesNotDependOnInternalPackagesOfOtherModules() {
        noClasses()
                .that()
                .resideInAPackage(
                        "..membership..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..client.application..",
                        "..client.infrastructure..",
                        "..client.web..",
                        "..plan.application..",
                        "..plan.infrastructure..",
                        "..plan.web..",
                        "..promotion.application..",
                        "..promotion.infrastructure..",
                        "..promotion.web..")
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

    @Test
    void paymentDoesNotDependOnInternalPackagesOfOtherModules() {
        noClasses()
                .that()
                .resideInAPackage(
                        "..payment..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..membership.application..",
                        "..membership.infrastructure..",
                        "..membership.web..",
                        "..client.application..",
                        "..client.infrastructure..",
                        "..client.web..")
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }
}
