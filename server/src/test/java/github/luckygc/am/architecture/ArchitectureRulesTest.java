package github.luckygc.am.architecture;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "github.luckygc.am", importOptions = DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule common_should_not_depend_on_business_or_infrastructure =
            noClasses()
                    .that().resideInAPackage("..common..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..module..", "..infrastructure..");

    @ArchTest
    static final ArchRule infrastructure_should_not_depend_on_business_ =
        noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..module..");
}
