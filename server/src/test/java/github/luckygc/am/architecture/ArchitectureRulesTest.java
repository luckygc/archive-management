package github.luckygc.am.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "github.luckygc.am", importOptions = DoNotIncludeTests.class)
class ArchitectureRulesTest {

    private static final String MODULE_PACKAGE = "github.luckygc.am.module.";

    @ArchTest
    static final ArchRule common_should_not_depend_on_business_or_infrastructure =
            noClasses()
                    .that().resideInAPackage("..common..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..module..", "..infrastructure..");

    @ArchTest
    static final ArchRule business_modules_should_only_depend_on_other_module_apis =
            classes()
                    .that().resideInAPackage("..module..")
                    .should(onlyDependOnOtherModuleApis());

    private static ArchCondition<JavaClass> onlyDependOnOtherModuleApis() {
        return new ArchCondition<>("只依赖其他业务模块的 api 包") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String originModule = moduleName(item.getPackageName());
                if (originModule == null) {
                    return;
                }

                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass targetClass = dependency.getTargetClass();
                    String targetPackage = targetClass.getPackageName();
                    String targetModule = moduleName(targetPackage);
                    if (targetModule == null || originModule.equals(targetModule)) {
                        continue;
                    }
                    if (!isModuleApiPackage(targetPackage, targetModule)) {
                        events.add(SimpleConditionEvent.violated(
                                dependency,
                                "%s 依赖了模块 %s 的非 api 包：%s"
                                        .formatted(item.getName(), targetModule, targetClass.getName())));
                    }
                }
            }
        };
    }

    private static String moduleName(String packageName) {
        if (!packageName.startsWith(MODULE_PACKAGE)) {
            return null;
        }
        String relativePackage = packageName.substring(MODULE_PACKAGE.length());
        int separatorIndex = relativePackage.indexOf('.');
        if (separatorIndex < 0) {
            return relativePackage.isBlank() ? null : relativePackage;
        }
        return relativePackage.substring(0, separatorIndex);
    }

    private static boolean isModuleApiPackage(String packageName, String moduleName) {
        String apiPackage = MODULE_PACKAGE + moduleName + ".api";
        return packageName.equals(apiPackage) || packageName.startsWith(apiPackage + ".");
    }
}
