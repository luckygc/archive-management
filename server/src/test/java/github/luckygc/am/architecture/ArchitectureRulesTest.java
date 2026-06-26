package github.luckygc.am.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Query;
import jakarta.data.repository.Save;
import jakarta.data.repository.Update;

import org.hibernate.annotations.processing.HQL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import github.luckygc.am.app.ArchiveManagementApplication;

@AnalyzeClasses(packages = "github.luckygc.am", importOptions = DoNotIncludeTests.class)
@DisplayName("架构边界规则")
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

    @ArchTest
    static final ArchRule rest_controllers_should_not_use_class_level_request_mapping =
            classes()
                    .that()
                    .areAnnotatedWith(RestController.class)
                    .should()
                    .notBeAnnotatedWith(RequestMapping.class);

    @ArchTest
    static final ArchRule project_should_not_depend_on_spring_data =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.springframework.data..");

    @ArchTest
    static final ArchRule module_should_not_use_jdbc_client =
            noClasses()
                    .that()
                    .resideInAPackage("..module..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient");

    @ArchTest
    static void repository_custom_methods_should_declare_operation_annotation(JavaClasses classes) {
        List<String> violations =
                classes.stream()
                        .filter(ArchitectureRulesTest::isProjectDataRepository)
                        .flatMap(
                                repository ->
                                        repository.getMethods().stream()
                                                .filter(
                                                        method ->
                                                                method.getOwner()
                                                                        .equals(repository))
                                                .filter(
                                                        method ->
                                                                !hasRepositoryOperationAnnotation(
                                                                        method))
                                                .map(
                                                        method ->
                                                                repository.getName()
                                                                        + "#"
                                                                        + method.getName()))
                        .sorted()
                        .toList();

        assertTrue(
                violations.isEmpty(),
                () -> "Repository 自定义方法必须显式标注 Jakarta Data/HQL 操作注解: " + violations);
    }

    @Test
    @DisplayName("Spring Modulith 模块结构校验通过")
    void springModulithModuleStructureShouldBeValid() {
        ApplicationModules.of(ArchiveManagementApplication.class).verify();
    }

    private static boolean isProjectDataRepository(JavaClass javaClass) {
        return javaClass.isInterface()
                && javaClass.getPackageName().startsWith("github.luckygc.am.module")
                && javaClass.getSimpleName().endsWith("DataRepository");
    }

    private static boolean hasRepositoryOperationAnnotation(JavaMethod method) {
        return method.isAnnotatedWith(Find.class)
                || method.isAnnotatedWith(Query.class)
                || method.isAnnotatedWith(HQL.class)
                || method.isAnnotatedWith(Insert.class)
                || method.isAnnotatedWith(Update.class)
                || method.isAnnotatedWith(Delete.class)
                || method.isAnnotatedWith(Save.class);
    }
}
