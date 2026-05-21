package com.ls.agent.common;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.ls.agent.common", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule common_should_not_contain_business_layer_classes =
            classes()
                    .that().resideInAPackage("com.ls.agent.common..")
                    .should(notUseBusinessLayerNaming());

    private static ArchCondition<JavaClass> notUseBusinessLayerNaming() {
        return new ArchCondition<>("not use business layer naming") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String simpleName = item.getSimpleName();
                boolean violated = simpleName.endsWith("Entity")
                        || simpleName.endsWith("Mapper")
                        || simpleName.endsWith("Service")
                        || simpleName.endsWith("Controller")
                        || simpleName.endsWith("Repository")
                        || simpleName.endsWith("Command");

                if (violated) {
                    events.add(SimpleConditionEvent.violated(item,
                            item.getName() + " must stay out of common because common is shared infrastructure only"));
                }
            }
        };
    }
}
