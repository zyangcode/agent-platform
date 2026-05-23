package com.ls.agent.gateway;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureRulesTest {

    @Test
    void gatewayModuleShouldNotAccessCorePersistenceDetails() {
        var importedClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeJars())
                .importPackages("com.ls.agent.gateway");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.ls.agent.gateway..")
                .should().accessClassesThat().resideInAnyPackage(
                        "com.ls.agent.core..entity..",
                        "com.ls.agent.core..mapper..",
                        "com.ls.agent.core..internal.."
                )
                .because("gateway must depend on core api/dto/command only, never persistence details");

        rule.check(importedClasses);
    }
}
