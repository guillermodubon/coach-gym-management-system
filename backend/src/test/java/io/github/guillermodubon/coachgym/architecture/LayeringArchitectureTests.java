package io.github.guillermodubon.coachgym.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

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
    @Test
    void accessDoesNotDependOnInternalPackagesOfOtherModules() {
        noClasses()
                .that()
                .resideInAPackage("..access..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..client.application..",
                        "..client.infrastructure..",
                        "..client.web..",
                        "..membership.application..",
                        "..membership.infrastructure..",
                        "..membership.web..",
                        "..audit.application..",
                        "..audit.infrastructure..",
                        "..audit.web..")
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

    @Test
    void auditDoesNotDependOnAccessInternals() {
        noClasses()
                .that()
                .resideInAPackage("..audit..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..access.application..",
                        "..access.domain..",
                        "..access.infrastructure..",
                        "..access.web..")
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

    @Test
    void equipmentWebDoesNotDependOnEquipmentPersistence() {
        noClasses()
                .that()
                .resideInAPackage(
                        "..equipment.web..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(
                        "..equipment.infrastructure.persistence..")
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

    @Test
    void equipmentDomainDoesNotDependOnSpring() {
        noClasses()
                .that()
                .resideInAPackage(
                        "..equipment.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(
                        "org.springframework..")
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

    @Test
    void equipmentDomainDoesNotDependOnJpa() {
        noClasses()
                .that()
                .resideInAPackage(
                        "..equipment.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(
                        "jakarta.persistence..")
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

    @Test
    void equipmentDoesNotDependOnInternalAuditPackages() {
        noClasses()
                .that()
                .resideInAPackage(
                        "..equipment..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..audit.application..",
                        "..audit.infrastructure..")
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

    @Test
    void equipmentDoesNotDependOnInternalMaintenancePackages() {
        noClasses()
                .that()
                .resideInAPackage(
                        "..equipment..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..maintenance.application..",
                        "..maintenance.domain..",
                        "..maintenance.infrastructure..",
                        "..maintenance.web..")
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

    @Test
    void equipmentDoesNotDependOnInternalIncidentPackages() {
        noClasses()
                .that()
                .resideInAPackage(
                        "..equipment..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..incident.application..",
                        "..incident.domain..",
                        "..incident.infrastructure..",
                        "..incident.web..")
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

    @Test
    void auditDoesNotDependOnEquipmentInternals() {
        noClasses()
                .that()
                .resideInAPackage(
                        "..audit..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..equipment.application..",
                        "..equipment.domain..",
                        "..equipment.infrastructure..",
                        "..equipment.web..")
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

    @Test
    void jpaEntitiesAreNotRestControllers() {
        noClasses()
                .that()
                .areAnnotatedWith(Entity.class)
                .should()
                .beAnnotatedWith(
                        RestController.class)
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

    @Test
    void equipmentWebDoesNotDependOnJpaEntities() {
        noClasses()
                .that()
                .resideInAPackage(
                        "..equipment.web..")
                .should()
                .dependOnClassesThat()
                .areAnnotatedWith(Entity.class)
                .allowEmptyShould(true)
                .check(PROJECT_CLASSES);
    }

}
