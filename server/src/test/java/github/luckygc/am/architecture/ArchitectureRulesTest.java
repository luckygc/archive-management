package github.luckygc.am.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Query;
import jakarta.data.repository.Update;
import jakarta.persistence.Entity;

import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.processing.HQL;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.stereotype.Component;
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
import github.luckygc.am.common.api.CursorPageResponse;

@AnalyzeClasses(packages = "github.luckygc.am", importOptions = DoNotIncludeTests.class)
@DisplayName("架构边界规则")
class ArchitectureRulesTest {

    private static final Set<String> SOFT_DELETE_ENTITY_NAMES =
            Set.of(
                    "github.luckygc.am.module.archive.metadata.ArchiveFonds",
                    "github.luckygc.am.module.archive.metadata.ArchiveCategory",
                    "github.luckygc.am.module.archive.metadata.ArchiveField",
                    "github.luckygc.am.module.archive.metadata.ArchiveFieldLayout",
                    "github.luckygc.am.module.archive.item.ArchiveItem",
                    "github.luckygc.am.module.archive.item.ArchiveVolume");

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
    static final ArchRule module_controllers_should_reside_in_web_package =
            classes()
                    .that()
                    .resideInAPackage("github.luckygc.am.module..")
                    .and()
                    .haveSimpleNameEndingWith("Controller")
                    .should()
                    .resideInAPackage("..web..");

    @ArchTest
    static final ArchRule module_services_should_reside_in_service_package =
            classes()
                    .that()
                    .resideInAPackage("github.luckygc.am.module..")
                    .and()
                    .haveSimpleNameEndingWith("Service")
                    .should()
                    .resideInAPackage("..service..");

    @ArchTest
    static final ArchRule module_managers_should_reside_in_manager_package =
            classes()
                    .that()
                    .resideInAPackage("github.luckygc.am.module..")
                    .and()
                    .haveSimpleNameEndingWith("Manager")
                    .should()
                    .resideInAPackage("..manager..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule module_mappers_should_reside_in_mapper_package =
            classes()
                    .that()
                    .resideInAPackage("github.luckygc.am.module..")
                    .and()
                    .haveSimpleNameEndingWith("Mapper")
                    .should()
                    .resideInAPackage("..mapper..");

    @ArchTest
    static final ArchRule module_repositories_should_reside_in_repository_package =
            classes()
                    .that()
                    .resideInAPackage("github.luckygc.am.module..")
                    .and()
                    .haveSimpleNameEndingWith("DataRepository")
                    .and()
                    .haveSimpleNameNotStartingWith("_")
                    .should()
                    .resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule module_web_should_not_depend_on_data_access =
            noClasses()
                    .that()
                    .resideInAPackage("github.luckygc.am.module..web..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "github.luckygc.am.module..repository..",
                            "github.luckygc.am.module..mapper..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule module_data_access_should_not_depend_on_upper_layers =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "github.luckygc.am.module..repository..",
                            "github.luckygc.am.module..mapper..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "github.luckygc.am.module..web..",
                            "github.luckygc.am.module..service..",
                            "github.luckygc.am.module..manager..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule project_should_not_depend_on_spring_data =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.springframework.data..");

    @ArchTest
    static final ArchRule project_should_not_depend_on_jackson2_runtime_apis =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.fasterxml.jackson.core..",
                            "com.fasterxml.jackson.databind..",
                            "com.fasterxml.jackson.dataformat..",
                            "com.fasterxml.jackson.module..");

    @ArchTest
    static final ArchRule module_should_not_use_jdbc_client =
            noClasses()
                    .that()
                    .resideInAPackage("..module..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient");

    @ArchTest
    static final ArchRule project_should_not_use_jakarta_data_save_annotation =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("jakarta.data.repository.Save");

    @ArchTest
    static void project_repositories_should_not_extend_jakarta_crud_repository(
            JavaClasses classes) {
        List<String> violations =
                classes.stream()
                        .filter(ArchitectureRulesTest::isProjectDataRepository)
                        .filter(repository -> repository.isAssignableTo(CrudRepository.class))
                        .map(JavaClass::getName)
                        .sorted()
                        .toList();

        assertTrue(
                violations.isEmpty(),
                () -> "项目 Repository 不应继承 Jakarta CrudRepository，应使用项目自定义基础接口: " + violations);
    }

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

    @ArchTest
    static void repository_upsert_methods_should_not_be_declared(JavaClasses classes) {
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
                                                                method.isAnnotatedWith(
                                                                                "jakarta.data.repository.Save")
                                                                        || method.getName()
                                                                                .equals("save")
                                                                        || method.getName()
                                                                                .toLowerCase()
                                                                                .contains("upsert"))
                                                .map(
                                                        method ->
                                                                repository.getName()
                                                                        + "#"
                                                                        + method.getName()))
                        .sorted()
                        .toList();

        assertTrue(
                violations.isEmpty(),
                () -> "Repository 禁止定义 save/upsert 或 @Save 方法: " + violations);
    }

    @ArchTest
    static void packages_with_project_classes_should_declare_null_marked(JavaClasses classes) {
        Set<String> packagesWithProjectClasses = new TreeSet<>();
        Set<String> nullMarkedPackages = new TreeSet<>();

        classes.forEach(
                javaClass -> {
                    if (javaClass.getSimpleName().equals("package-info")) {
                        if (javaClass.isAnnotatedWith(NullMarked.class)) {
                            nullMarkedPackages.add(javaClass.getPackageName());
                        }
                    } else {
                        packagesWithProjectClasses.add(javaClass.getPackageName());
                    }
                });

        packagesWithProjectClasses.removeAll(nullMarkedPackages);

        assertTrue(
                packagesWithProjectClasses.isEmpty(),
                () ->
                        "有 Java 类的包必须提供 package-info.java 并标注 @NullMarked: "
                                + packagesWithProjectClasses);
    }

    @ArchTest
    static void soft_delete_entities_should_use_hibernate_soft_delete(JavaClasses classes) {
        List<String> missingSoftDelete =
                classes.stream()
                        .filter(javaClass -> SOFT_DELETE_ENTITY_NAMES.contains(javaClass.getName()))
                        .filter(javaClass -> !javaClass.isAnnotatedWith(SoftDelete.class))
                        .map(JavaClass::getName)
                        .sorted()
                        .toList();
        List<String> explicitDeletedFlagFields =
                classes.stream()
                        .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
                        .flatMap(
                                javaClass ->
                                        javaClass.getFields().stream()
                                                .filter(
                                                        field ->
                                                                field.getName()
                                                                        .equals("deletedFlag"))
                                                .map(
                                                        field ->
                                                                javaClass.getName()
                                                                        + "#"
                                                                        + field.getName()))
                        .sorted()
                        .toList();

        assertTrue(
                missingSoftDelete.isEmpty(),
                () -> "软删除固定实体必须标注 Hibernate @SoftDelete: " + missingSoftDelete);
        assertTrue(
                explicitDeletedFlagFields.isEmpty(),
                () ->
                        "软删除字段由 Hibernate @SoftDelete 管理，实体不应显式映射 deletedFlag: "
                                + explicitDeletedFlagFields);
    }

    @ArchTest
    static void spring_component_classes_should_declare_single_constructor(JavaClasses classes) {
        List<String> violations =
                classes.stream()
                        .filter(ArchitectureRulesTest::isSpringComponentClass)
                        .filter(javaClass -> javaClass.getConstructors().size() != 1)
                        .map(
                                javaClass ->
                                        javaClass.getName()
                                                + " 构造函数数量="
                                                + javaClass.getConstructors().size())
                        .sorted()
                        .toList();

        assertTrue(violations.isEmpty(), () -> "Spring Bean 组件类必须且只能有一个构造函数: " + violations);
    }

    @ArchTest
    static void cursor_page_controllers_should_return_cursor_page_response_for_common_page_contract(
            JavaClasses classes) {
        List<String> violations =
                classes.stream()
                        .filter(javaClass -> javaClass.isAnnotatedWith(RestController.class))
                        .flatMap(javaClass -> javaClass.getMethods().stream())
                        .filter(ArchitectureRulesTest::hasPageRequestParameter)
                        .filter(
                                method ->
                                        !method.getRawReturnType()
                                                .isAssignableTo(CursorPageResponse.class))
                        .map(
                                method ->
                                        method.getOwner().getName()
                                                + "#"
                                                + method.getName()
                                                + " 未返回 CursorPageResponse 合同")
                        .sorted()
                        .toList();

        assertTrue(
                violations.isEmpty(),
                () ->
                        "声明 PageRequest 的 cursor 分页 Controller 应返回 CursorPageResponse 合同，由 ResponseBodyAdvice 填充 token: "
                                + violations);
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
                || method.isAnnotatedWith(Delete.class);
    }

    private static boolean isSpringComponentClass(JavaClass javaClass) {
        return !javaClass.isInterface()
                && !javaClass.isAnnotation()
                && (javaClass.isAnnotatedWith(Component.class)
                        || javaClass.isMetaAnnotatedWith(Component.class));
    }

    private static boolean hasPageRequestParameter(JavaMethod method) {
        return method.getRawParameterTypes().stream()
                .anyMatch(parameter -> parameter.getName().equals("jakarta.data.page.PageRequest"));
    }
}
