package github.luckygc.am.module.archive.governance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceBinding;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceBindingType;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceScheme;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersion;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersionStatus;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceScope;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceScopeType;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceBindingDataRepository;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceSchemeDataRepository;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceSchemeVersionDataRepository;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceScopeDataRepository;

@DisplayName("档案治理方案服务")
class ArchiveGovernanceServiceTests {

    private ArchiveGovernanceSchemeDataRepository schemeRepository;
    private ArchiveGovernanceSchemeVersionDataRepository versionRepository;
    private ArchiveGovernanceScopeDataRepository scopeRepository;
    private ArchiveGovernanceBindingDataRepository bindingRepository;
    private ArchiveGovernanceService service;

    @BeforeEach
    void setUp() {
        schemeRepository = mock(ArchiveGovernanceSchemeDataRepository.class);
        versionRepository = mock(ArchiveGovernanceSchemeVersionDataRepository.class);
        scopeRepository = mock(ArchiveGovernanceScopeDataRepository.class);
        bindingRepository = mock(ArchiveGovernanceBindingDataRepository.class);
        service =
                new ArchiveGovernanceService(
                        schemeRepository, versionRepository, scopeRepository, bindingRepository);
    }

    @Test
    @DisplayName("创建治理方案时裁剪字段并拒绝重复编码")
    void createSchemeShouldTrimFieldsAndRejectDuplicateCode() {
        when(schemeRepository.findBySchemeCode("default_governance")).thenReturn(null);
        when(schemeRepository.insert(any(ArchiveGovernanceScheme.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 9L));
        ArchiveGovernanceService.ArchiveGovernanceSchemeResponse response =
                service.createScheme(
                        new ArchiveGovernanceService.CreateArchiveGovernanceSchemeRequest(
                                " default_governance ", " 默认方案 ", " 说明 ", true, 3),
                        7L);

        assertThat(response.id()).isEqualTo(9L);
        ArgumentCaptor<ArchiveGovernanceScheme> captor =
                ArgumentCaptor.forClass(ArchiveGovernanceScheme.class);
        verify(schemeRepository).insert(captor.capture());
        assertThat(captor.getValue().getSchemeCode()).isEqualTo("default_governance");
        assertThat(captor.getValue().getSchemeName()).isEqualTo("默认方案");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(7L);

        when(schemeRepository.findBySchemeCode("default_governance"))
                .thenReturn(new ArchiveGovernanceScheme());
        assertThatThrownBy(
                        () ->
                                service.createScheme(
                                        new ArchiveGovernanceService
                                                .CreateArchiveGovernanceSchemeRequest(
                                                "default_governance", "默认方案", null, true, 0),
                                        7L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("治理方案编码已存在");
    }

    @Test
    @DisplayName("发布草稿治理方案版本时记录发布人和发布时间")
    void publishVersionShouldMoveDraftToPublished() {
        ArchiveGovernanceSchemeVersion version = version(3L, ArchiveGovernanceSchemeVersionStatus.DRAFT);
        when(versionRepository.findById(3L)).thenReturn(Optional.of(version));
        when(versionRepository.update(version)).thenReturn(version);

        ArchiveGovernanceService.ArchiveGovernanceSchemeVersionResponse response =
                service.publishVersion(3L, 8L);

        assertThat(response.status()).isEqualTo(ArchiveGovernanceSchemeVersionStatus.PUBLISHED);
        assertThat(version.getPublishedBy()).isEqualTo(8L);
        assertThat(version.getPublishedAt()).isNotNull();
        verify(versionRepository).update(version);
    }

    @Test
    @DisplayName("解析默认治理方案版本时按分类、全宗、全局优先级命中")
    void resolveDefaultVersionShouldPreferCategoryOverFondsAndGlobal() {
        when(scopeRepository.findByScopeTypeAndDefaultFlag(ArchiveGovernanceScopeType.CATEGORY, true))
                .thenReturn(List.of(scope(30L, ArchiveGovernanceScopeType.CATEGORY, null, "C001")));
        when(scopeRepository.findByScopeTypeAndDefaultFlag(ArchiveGovernanceScopeType.FONDS, true))
                .thenReturn(List.of(scope(20L, ArchiveGovernanceScopeType.FONDS, "F001", null)));
        when(scopeRepository.findByScopeTypeAndDefaultFlag(ArchiveGovernanceScopeType.GLOBAL, true))
                .thenReturn(List.of(scope(10L, ArchiveGovernanceScopeType.GLOBAL, null, null)));
        when(versionRepository.findById(30L))
                .thenReturn(Optional.of(version(30L, ArchiveGovernanceSchemeVersionStatus.PUBLISHED)));
        when(versionRepository.findById(20L))
                .thenReturn(Optional.of(version(20L, ArchiveGovernanceSchemeVersionStatus.PUBLISHED)));
        when(versionRepository.findById(10L))
                .thenReturn(Optional.of(version(10L, ArchiveGovernanceSchemeVersionStatus.PUBLISHED)));

        ArchiveGovernanceService.ArchiveGovernanceSchemeVersionResponse response =
                service.resolveDefaultVersion("F001", "C001");

        assertThat(response.id()).isEqualTo(30L);
    }

    @Test
    @DisplayName("按治理方案版本读取适用范围")
    void listScopesShouldReturnVersionScopes() {
        when(versionRepository.findById(11L))
                .thenReturn(Optional.of(version(11L, ArchiveGovernanceSchemeVersionStatus.DRAFT)));
        ArchiveGovernanceScope scope = scope(11L, ArchiveGovernanceScopeType.FONDS, "F001", null);
        scope.setId(101L);
        when(scopeRepository.findBySchemeVersionId(11L)).thenReturn(List.of(scope));

        List<ArchiveGovernanceService.ArchiveGovernanceScopeResponse> responses =
                service.listScopes(11L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(101L);
        assertThat(responses.getFirst().schemeVersionId()).isEqualTo(11L);
        assertThat(responses.getFirst().scopeType()).isEqualTo(ArchiveGovernanceScopeType.FONDS);
        assertThat(responses.getFirst().fondsCode()).isEqualTo("F001");
        assertThat(responses.getFirst().defaultFlag()).isTrue();
    }

    @Test
    @DisplayName("按治理方案版本读取装配绑定")
    void listBindingsShouldReturnVersionBindings() {
        when(versionRepository.findById(11L))
                .thenReturn(Optional.of(version(11L, ArchiveGovernanceSchemeVersionStatus.DRAFT)));
        ArchiveGovernanceBinding binding = new ArchiveGovernanceBinding();
        binding.setId(201L);
        binding.setSchemeVersionId(11L);
        binding.setBindingType(ArchiveGovernanceBindingType.RULE_SET);
        binding.setTargetType("RULE");
        binding.setTargetId(301L);
        binding.setTargetCode("retention_rules");
        binding.setBindingOrder(2);
        when(bindingRepository.findBySchemeVersionId(11L)).thenReturn(List.of(binding));

        List<ArchiveGovernanceService.ArchiveGovernanceBindingResponse> responses =
                service.listBindings(11L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(201L);
        assertThat(responses.getFirst().schemeVersionId()).isEqualTo(11L);
        assertThat(responses.getFirst().bindingType()).isEqualTo(ArchiveGovernanceBindingType.RULE_SET);
        assertThat(responses.getFirst().targetType()).isEqualTo("RULE");
        assertThat(responses.getFirst().targetId()).isEqualTo(301L);
        assertThat(responses.getFirst().targetCode()).isEqualTo("retention_rules");
        assertThat(responses.getFirst().bindingOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("配置引用保护只拦截已发布、冻结或退役治理版本引用")
    void requireTargetNotReferencedShouldOnlyCheckProtectedVersionBindings() {
        when(bindingRepository.countProtectedByBindingTypeAndTargetId(
                        ArchiveGovernanceBindingType.ONTOLOGY, 301L))
                .thenReturn(0L);

        assertThatNoException()
                .isThrownBy(
                        () ->
                                service.requireTargetNotReferenced(
                                        ArchiveGovernanceBindingType.ONTOLOGY, 301L));

        when(bindingRepository.countProtectedByBindingTypeAndTargetId(
                        ArchiveGovernanceBindingType.ONTOLOGY, 302L))
                .thenReturn(1L);
        assertThatThrownBy(
                        () ->
                                service.requireTargetNotReferenced(
                                        ArchiveGovernanceBindingType.ONTOLOGY, 302L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("该配置已被治理方案版本引用");
    }

    private ArchiveGovernanceScheme withId(ArchiveGovernanceScheme scheme, Long id) {
        scheme.setId(id);
        return scheme;
    }

    private ArchiveGovernanceSchemeVersion version(
            Long id, ArchiveGovernanceSchemeVersionStatus status) {
        ArchiveGovernanceSchemeVersion version = new ArchiveGovernanceSchemeVersion();
        version.setId(id);
        version.setSchemeId(1L);
        version.setVersionCode("v" + id);
        version.setStatus(status);
        return version;
    }

    private ArchiveGovernanceScope scope(
            Long versionId,
            ArchiveGovernanceScopeType scopeType,
            String fondsCode,
            String categoryCode) {
        ArchiveGovernanceScope scope = new ArchiveGovernanceScope();
        scope.setSchemeVersionId(versionId);
        scope.setScopeType(scopeType);
        scope.setFondsCode(fondsCode);
        scope.setCategoryCode(categoryCode);
        scope.setDefaultFlag(true);
        return scope;
    }
}
