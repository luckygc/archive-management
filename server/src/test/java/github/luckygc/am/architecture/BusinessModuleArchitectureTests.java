package github.luckygc.am.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import github.luckygc.am.architecture.fixtures.module.alpha.repository.AlphaRepository;
import github.luckygc.am.architecture.fixtures.module.alpha.service.CrossModuleDataAccessClient;
import github.luckygc.am.architecture.fixtures.module.alpha.service.CycleFirst;
import github.luckygc.am.architecture.fixtures.module.alpha.service.SameModuleDataAccessClient;
import github.luckygc.am.architecture.fixtures.module.beta.mapper.BetaMapper;
import github.luckygc.am.architecture.fixtures.module.beta.repository.BetaRepository;
import github.luckygc.am.architecture.fixtures.module.beta.service.BetaService;
import github.luckygc.am.architecture.fixtures.module.beta.service.CycleSecond;

@AnalyzeClasses(packages = "github.luckygc.am.module", importOptions = DoNotIncludeTests.class)
@DisplayName("业务模块协作边界")
class BusinessModuleArchitectureTests {

    private static final String MODULE_ROOT = "github.luckygc.am.module";
    private static final String FIXTURE_MODULE_ROOT =
            "github.luckygc.am.architecture.fixtures.module";

    @ArchTest
    static final ArchRule business_modules_should_not_access_foreign_data_access =
            dataAccessRule(MODULE_ROOT);

    @ArchTest
    static final ArchRule business_modules_should_not_form_cycles = cycleRule(MODULE_ROOT);

    @Test
    @DisplayName("Spring Modulith 识别并校验全部八个业务模块")
    void springModulithShouldDiscoverAndVerifyBusinessModules() {
        // 启动类位于 app 包，Modulith 不采用 SpringBootApplication 的 scanBasePackages。
        ApplicationModules modules = ApplicationModules.of(MODULE_ROOT, new DoNotIncludeTests());
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()).toList())
                .isNotEmpty()
                .containsExactlyInAnyOrder(
                        "approval",
                        "archive",
                        "authentication",
                        "authorization",
                        "intake",
                        "organization",
                        "storage",
                        "todo");
        modules.verify();
    }

    @Test
    @DisplayName("跨模块直连 Repository 和 Mapper 的反例均被拒绝")
    void crossModuleRepositoryAndMapperAccessShouldBeRejected() {
        JavaClasses fixtures =
                new ClassFileImporter()
                        .importClasses(
                                CrossModuleDataAccessClient.class,
                                BetaRepository.class,
                                BetaMapper.class);

        List<String> violations =
                dataAccessRule(FIXTURE_MODULE_ROOT)
                        .evaluate(fixtures)
                        .getFailureReport()
                        .getDetails();

        assertThat(violations)
                .anySatisfy(
                        violation ->
                                assertThat(violation)
                                        .contains("CrossModuleDataAccessClient", "BetaRepository"))
                .anySatisfy(
                        violation ->
                                assertThat(violation)
                                        .contains("CrossModuleDataAccessClient", "BetaMapper"));
    }

    @Test
    @DisplayName("本模块持久化访问和跨模块 Service 协作允许通过")
    void sameModuleDataAccessAndCrossModuleServiceCallsShouldBeAllowed() {
        JavaClasses fixtures =
                new ClassFileImporter()
                        .importClasses(
                                SameModuleDataAccessClient.class,
                                AlphaRepository.class,
                                BetaService.class);

        assertThat(dataAccessRule(FIXTURE_MODULE_ROOT).evaluate(fixtures).hasViolation()).isFalse();
        assertThat(cycleRule(FIXTURE_MODULE_ROOT).evaluate(fixtures).hasViolation()).isFalse();
    }

    @Test
    @DisplayName("两个业务模块之间的循环依赖反例被拒绝")
    void businessModuleCycleShouldBeRejected() {
        JavaClasses fixtures =
                new ClassFileImporter().importClasses(CycleFirst.class, CycleSecond.class);

        assertThat(
                        cycleRule(FIXTURE_MODULE_ROOT)
                                .evaluate(fixtures)
                                .getFailureReport()
                                .getDetails())
                .anySatisfy(
                        violation -> assertThat(violation).contains("CycleFirst", "CycleSecond"));
    }

    private static ArchRule dataAccessRule(String moduleRoot) {
        return classes()
                .that()
                .resideInAPackage(moduleRoot + "..")
                .should(
                        new ArchCondition<JavaClass>("不得直接访问其他业务模块的 Repository 或 Mapper") {
                            @Override
                            public void check(JavaClass origin, ConditionEvents events) {
                                String originModule = moduleName(origin, moduleRoot);
                                origin.getDirectDependenciesFromSelf()
                                        .forEach(
                                                dependency -> {
                                                    JavaClass target = dependency.getTargetClass();
                                                    String targetModule =
                                                            moduleName(target, moduleRoot);
                                                    if (!targetModule.isEmpty()
                                                            && !originModule.equals(targetModule)
                                                            && isDataAccess(target)) {
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

    private static ArchRule cycleRule(String moduleRoot) {
        return slices().matching(moduleRoot + ".(*)..").should().beFreeOfCycles();
    }

    private static String moduleName(JavaClass javaClass, String moduleRoot) {
        String prefix = moduleRoot + ".";
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(prefix)) {
            return "";
        }
        String relativePackage = packageName.substring(prefix.length());
        int separator = relativePackage.indexOf('.');
        return separator < 0 ? relativePackage : relativePackage.substring(0, separator);
    }

    private static boolean isDataAccess(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return packageName.endsWith(".repository")
                || packageName.contains(".repository.")
                || packageName.endsWith(".mapper")
                || packageName.contains(".mapper.")
                || javaClass.isAnnotatedWith("jakarta.data.repository.Repository")
                || javaClass.isAnnotatedWith("org.apache.ibatis.annotations.Mapper");
    }
}
