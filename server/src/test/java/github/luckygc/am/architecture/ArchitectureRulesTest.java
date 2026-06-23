package github.luckygc.am.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "github.luckygc.am", importOptions = DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule common_should_not_depend_on_business_or_infrastructure =
            noClasses()
                    .that()
                    .resideInAPackage("..common..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "github.luckygc.am.module..", "github.luckygc.am.infrastructure..");

    @ArchTest
    static final ArchRule common_should_not_depend_on_web_frameworks =
            noClasses()
                    .that()
                    .resideInAPackage("..common..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule module_should_not_depend_on_infrastructure =
            noClasses()
                    .that()
                    .resideInAPackage("..module..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("github.luckygc.am.infrastructure..");

    @ArchTest
    static final ArchRule infrastructure_should_not_depend_on_business_ =
            noClasses()
                    .that()
                    .resideInAPackage("..infrastructure..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("github.luckygc.am.module..");
}
