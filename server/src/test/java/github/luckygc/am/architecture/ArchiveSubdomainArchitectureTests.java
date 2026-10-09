package github.luckygc.am.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import github.luckygc.am.architecture.fixtures.archive.item.repository.ItemRepository;
import github.luckygc.am.architecture.fixtures.archive.item.service.CrossSubdomainDataAccessClient;
import github.luckygc.am.architecture.fixtures.archive.item.service.OwnedDataAccessClient;
import github.luckygc.am.architecture.fixtures.archive.item.web.WebSharedMapperClient;
import github.luckygc.am.architecture.fixtures.archive.mapper.SharedMapper;
import github.luckygc.am.architecture.fixtures.archive.mapper.SharedQuery;
import github.luckygc.am.architecture.fixtures.archive.metadata.mapper.MetadataMapper;
import github.luckygc.am.architecture.fixtures.archive.metadata.repository.MetadataRepository;
import github.luckygc.am.architecture.fixtures.archive.metadata.service.MetadataService;
import github.luckygc.am.architecture.fixtures.archive.metadata.service.ReadOnlyCatalogQueryWithRuleDependency;
import github.luckygc.am.architecture.fixtures.archive.rule.service.ForeignSharedMapperClient;
import github.luckygc.am.architecture.fixtures.archive.rule.service.RuleService;

@AnalyzeClasses(
        packages = "github.luckygc.am.module.archive",
        importOptions = DoNotIncludeTests.class)
@DisplayName("档案子域持久化所有权")
class ArchiveSubdomainArchitectureTests {

    private static final String ARCHIVE_ROOT = "github.luckygc.am.module.archive";
    private static final String FIXTURE_ROOT = "github.luckygc.am.architecture.fixtures.archive";

    @ArchTest
    static final ArchRule archive_subdomains_should_access_only_owned_data_access =
            dataAccessRule(ARCHIVE_ROOT);

    @ArchTest
    static final ArchRule metadata_catalog_query_should_not_depend_on_runtime_rules =
            catalogQueryRule(ARCHIVE_ROOT);

    @Test
    @DisplayName("同一档案模块内跨子域的Repository和Mapper访问也被拒绝")
    void foreignSubdomainRepositoryAndMapperAreRejected() {
        JavaClasses fixtures =
                new ClassFileImporter()
                        .importClasses(
                                CrossSubdomainDataAccessClient.class,
                                MetadataRepository.class,
                                MetadataMapper.class);

        List<String> violations =
                dataAccessRule(FIXTURE_ROOT).evaluate(fixtures).getFailureReport().getDetails();

        assertThat(violations)
                .anySatisfy(
                        violation ->
                                assertThat(violation)
                                        .contains(
                                                "CrossSubdomainDataAccessClient",
                                                "MetadataRepository"))
                .anySatisfy(
                        violation ->
                                assertThat(violation)
                                        .contains(
                                                "CrossSubdomainDataAccessClient",
                                                "MetadataMapper"));
    }

    @Test
    @DisplayName("所属子域Repository、跨子域Service及共享查询类型可使用")
    void ownedDataAccessAndServiceCollaborationAreAllowed() {
        JavaClasses fixtures =
                new ClassFileImporter()
                        .importClasses(
                                OwnedDataAccessClient.class,
                                ItemRepository.class,
                                MetadataService.class,
                                SharedMapper.class,
                                SharedQuery.class);

        assertThat(dataAccessRule(FIXTURE_ROOT).evaluate(fixtures).hasViolation()).isFalse();
    }

    @Test
    @DisplayName("共享Mapper不能由规则子域或Web调用")
    void sharedMapperIsLimitedToOwningBusinessServices() {
        JavaClasses fixtures =
                new ClassFileImporter()
                        .importClasses(
                                ForeignSharedMapperClient.class,
                                WebSharedMapperClient.class,
                                SharedMapper.class);

        List<String> violations =
                dataAccessRule(FIXTURE_ROOT).evaluate(fixtures).getFailureReport().getDetails();

        assertThat(violations)
                .anySatisfy(
                        violation ->
                                assertThat(violation)
                                        .contains("ForeignSharedMapperClient", "SharedMapper"))
                .anySatisfy(
                        violation ->
                                assertThat(violation)
                                        .contains("WebSharedMapperClient", "SharedMapper"));
    }

    @Test
    @DisplayName("元数据字段目录查询反向依赖规则的反例被拒绝")
    void metadataCatalogCannotRestoreRuntimeDependency() {
        JavaClasses fixtures =
                new ClassFileImporter()
                        .importClasses(
                                ReadOnlyCatalogQueryWithRuleDependency.class, RuleService.class);

        assertThat(
                        catalogQueryRule(FIXTURE_ROOT)
                                .evaluate(fixtures)
                                .getFailureReport()
                                .getDetails())
                .anySatisfy(
                        violation ->
                                assertThat(violation)
                                        .contains(
                                                "ReadOnlyCatalogQueryWithRuleDependency",
                                                "RuleService"));
    }

    private static ArchRule dataAccessRule(String archiveRoot) {
        return classes()
                .that()
                .resideInAPackage(archiveRoot + "..")
                .should(
                        new ArchCondition<JavaClass>("只访问所属档案子域的持久化接口") {
                            @Override
                            public void check(JavaClass origin, ConditionEvents events) {
                                String originSubdomain = subdomain(origin, archiveRoot);
                                origin.getDirectDependenciesFromSelf()
                                        .forEach(
                                                dependency -> {
                                                    JavaClass target = dependency.getTargetClass();
                                                    String targetSubdomain =
                                                            subdomain(target, archiveRoot);
                                                    boolean violation;
                                                    if (targetSubdomain.equals("mapper")) {
                                                        // 共享查询类型不执行读写；共享持久化端口限定实际拥有其操作的子域。
                                                        violation =
                                                                isPersistencePort(target)
                                                                        && !mayUseSharedMapper(
                                                                                origin,
                                                                                archiveRoot,
                                                                                originSubdomain);
                                                    } else {
                                                        violation =
                                                                !targetSubdomain.isEmpty()
                                                                        && !originSubdomain.equals(
                                                                                targetSubdomain)
                                                                        && isDataAccess(target);
                                                    }
                                                    if (violation) {
                                                        events.add(
                                                                SimpleConditionEvent.violated(
                                                                        dependency,
                                                                        dependency
                                                                                .getDescription()));
                                                    }
                                                });
                            }
                        });
    }

    private static ArchRule catalogQueryRule(String archiveRoot) {
        return classes()
                .that()
                .resideInAPackage(archiveRoot + ".metadata.service..")
                .should(
                        new ArchCondition<JavaClass>("元数据纯查询Service不得反向依赖规则") {
                            @Override
                            public void check(JavaClass origin, ConditionEvents events) {
                                if (!isReadOnlyService(origin)) return;
                                origin.getDirectDependenciesFromSelf().stream()
                                        .filter(
                                                dependency ->
                                                        dependency
                                                                .getTargetClass()
                                                                .getPackageName()
                                                                .startsWith(archiveRoot + ".rule."))
                                        .forEach(
                                                dependency ->
                                                        events.add(
                                                                SimpleConditionEvent.violated(
                                                                        dependency,
                                                                        dependency
                                                                                .getDescription())));
                            }
                        });
    }

    private static boolean isReadOnlyService(JavaClass javaClass) {
        if (!javaClass.isAnnotatedWith(Service.class)) return false;
        List<JavaMethod> publicMethods =
                javaClass.getMethods().stream()
                        .filter(method -> method.getOwner().equals(javaClass))
                        .filter(method -> method.getModifiers().contains(JavaModifier.PUBLIC))
                        .toList();
        return !publicMethods.isEmpty()
                && publicMethods.stream()
                        .allMatch(
                                method -> {
                                    if (method.isAnnotatedWith(Transactional.class)) {
                                        return method.getAnnotationOfType(Transactional.class)
                                                .readOnly();
                                    }
                                    return javaClass.isAnnotatedWith(Transactional.class)
                                            && javaClass
                                                    .getAnnotationOfType(Transactional.class)
                                                    .readOnly();
                                });
    }

    private static boolean mayUseSharedMapper(
            JavaClass origin, String archiveRoot, String originSubdomain) {
        if (originSubdomain.equals("mapper")) return true;
        if (!Set.of("item", "metadata").contains(originSubdomain)) return false;
        String domainRoot = archiveRoot + "." + originSubdomain;
        String packageName = origin.getPackageName();
        return packageName.equals(domainRoot + ".service")
                || packageName.startsWith(domainRoot + ".service.")
                || packageName.equals(domainRoot + ".manager")
                || packageName.startsWith(domainRoot + ".manager.");
    }

    private static String subdomain(JavaClass javaClass, String archiveRoot) {
        String prefix = archiveRoot + ".";
        if (!javaClass.getPackageName().startsWith(prefix)) return "";
        String relative = javaClass.getPackageName().substring(prefix.length());
        int separator = relative.indexOf('.');
        return separator < 0 ? relative : relative.substring(0, separator);
    }

    private static boolean isDataAccess(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return packageName.endsWith(".repository")
                || packageName.contains(".repository.")
                || packageName.endsWith(".mapper")
                || packageName.contains(".mapper.")
                || isPersistencePort(javaClass);
    }

    private static boolean isPersistencePort(JavaClass javaClass) {
        return javaClass.isAnnotatedWith("jakarta.data.repository.Repository")
                || javaClass.isAnnotatedWith("org.apache.ibatis.annotations.Mapper");
    }
}
