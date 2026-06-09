package com.ls.agent.core;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.ls.agent.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    private static final List<String> BUSINESS_PACKAGES = List.of(
            "identity",
            "model",
            "profile",
            "skill",
            "mcp",
            "agent",
            "experience",
            "memory",
            "rag",
            "evaluation",
            "context",
            "quota",
            "trace",
            "security",
            "alert",
            "team"
    );

    @Test
    void businessPackagesShouldNotAccessOtherPackagesPersistenceDetails() {
        var importedClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.ls.agent.core");

        for (String sourcePackage : BUSINESS_PACKAGES) {
            List<String> forbiddenPersistencePackages = BUSINESS_PACKAGES.stream()
                    .filter(packageName -> !packageName.equals(sourcePackage))
                    .flatMap(packageName -> List.of(
                            "com.ls.agent.core." + packageName + ".entity..",
                            "com.ls.agent.core." + packageName + ".mapper.."
                    ).stream())
                    .toList();

            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.ls.agent.core." + sourcePackage + "..")
                    .should().accessClassesThat().resideInAnyPackage(forbiddenPersistencePackages.toArray(String[]::new))
                    .because("cross-package collaboration must go through api/dto/command/result, not another package's entity or mapper")
                    .allowEmptyShould(true);

            rule.check(importedClasses);
        }
    }

    @Test
    void mappersShouldOnlyLiveInsideCoreBusinessPackages() {
        var importedClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.ls.agent");

        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages(
                        "com.ls.agent.core.identity.mapper..",
                        "com.ls.agent.core.model.mapper..",
                        "com.ls.agent.core.profile.mapper..",
                        "com.ls.agent.core.skill.mapper..",
                        "com.ls.agent.core.mcp.mapper..",
                        "com.ls.agent.core.agent.mapper..",
                        "com.ls.agent.core.experience.mapper..",
                        "com.ls.agent.core.memory.mapper..",
                        "com.ls.agent.core.rag.mapper..",
                        "com.ls.agent.core.quota.mapper..",
                        "com.ls.agent.core.trace.mapper..",
                        "com.ls.agent.core.security.mapper..",
                        "com.ls.agent.core.alert.mapper..",
                        "com.ls.agent.core.team.mapper..")
                .should().haveSimpleNameEndingWith("Mapper")
                .because("Mapper is persistence detail and must stay inside the owning core package");

        rule.check(importedClasses);
    }
}
