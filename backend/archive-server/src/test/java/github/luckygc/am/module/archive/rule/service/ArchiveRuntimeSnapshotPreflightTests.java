package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceScheme;
import github.luckygc.am.module.archive.governance.ArchiveGovernanceSchemeVersion;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceSchemeDataRepository;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceSchemeVersionDataRepository;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceScopeDataRepository;
import github.luckygc.am.module.archive.metadata.ArchiveFieldDataType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeActionType;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinition;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeDefinitionKind;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeFieldSource;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeStatus;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeActionDataRepository;
import github.luckygc.am.module.archive.rule.repository.ArchiveRuntimeDefinitionDataRepository;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeField;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeFieldCatalogService.ArchiveRuntimeFieldCatalog;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService.ArchiveRuntimeSnapshot;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeSnapshotService.ArchiveRuntimeSnapshotPreflightRequest;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("运行时配置快照预检")
class ArchiveRuntimeSnapshotPreflightTests {

    private ArchiveGovernanceSchemeDataRepository schemeRepository;
    private ArchiveGovernanceSchemeVersionDataRepository versionRepository;
    private ArchiveRuntimeDefinitionDataRepository definitionRepository;
    private ArchiveRuntimeFieldCatalogService fieldCatalogService;
    private ArchiveRuntimeSnapshotService service;

    @BeforeEach
    void setUp() {
        schemeRepository = mock(ArchiveGovernanceSchemeDataRepository.class);
        versionRepository = mock(ArchiveGovernanceSchemeVersionDataRepository.class);
        ArchiveGovernanceScopeDataRepository scopeRepository =
                mock(ArchiveGovernanceScopeDataRepository.class);
        definitionRepository = mock(ArchiveRuntimeDefinitionDataRepository.class);
        ArchiveRuntimeActionDataRepository actionRepository =
                mock(ArchiveRuntimeActionDataRepository.class);
        ArchiveRuntimeDefinitionService definitionService =
                mock(ArchiveRuntimeDefinitionService.class);
        fieldCatalogService = mock(ArchiveRuntimeFieldCatalogService.class);
        ArchiveGovernanceScheme scheme = new ArchiveGovernanceScheme();
        scheme.setId(10L);
        scheme.setSchemeCode("runtime-policy");
        scheme.setSchemeName("运行时策略");
        ArchiveGovernanceSchemeVersion version = new ArchiveGovernanceSchemeVersion();
        version.setId(101L);
        version.setSchemeId(10L);
        version.setVersionCode("v1");
        ArchiveRuntimeDefinition definition = definition("title-required", "metadata.title");
        when(versionRepository.findById(101L)).thenReturn(java.util.Optional.of(version));
        when(versionRepository.findBySchemeId(10L)).thenReturn(List.of(version));
        when(schemeRepository.findById(10L)).thenReturn(java.util.Optional.of(scheme));
        when(schemeRepository.findBySchemeCode("runtime-policy")).thenReturn(scheme);
        when(scopeRepository.findBySchemeVersionId(101L)).thenReturn(List.of());
        when(definitionRepository.findBySchemeVersionId(101L)).thenReturn(List.of(definition));
        when(actionRepository.findByDefinitionId(999L)).thenReturn(List.of());
        when(fieldCatalogService.catalog(
                        101L, "CONTRACT", ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE))
                .thenReturn(catalog(ArchiveFieldDataType.TEXT));
        service =
                new ArchiveRuntimeSnapshotService(
                        schemeRepository,
                        versionRepository,
                        scopeRepository,
                        definitionRepository,
                        actionRepository,
                        definitionService,
                        fieldCatalogService,
                        JsonMapper.builder().findAndAddModules().build(),
                        Clock.fixed(Instant.parse("2026-07-18T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("摘要正确且字段类型兼容时返回稳定字段映射")
    void validSnapshotShouldReturnResolvedMappings() {
        ArchiveRuntimeSnapshot snapshot = service.exportSnapshot(101L);

        var result =
                service.preflight(
                        new ArchiveRuntimeSnapshotPreflightRequest(snapshot, null, null, null));

        assertThat(result.compatible()).isTrue();
        assertThat(result.definitionCount()).isEqualTo(1);
        assertThat(result.fieldMappings())
                .singleElement()
                .satisfies(
                        mapping -> {
                            assertThat(mapping.sourceFieldCode()).isEqualTo("metadata.title");
                            assertThat(mapping.targetFieldCode()).isEqualTo("metadata.title");
                            assertThat(mapping.dataType()).isEqualTo(ArchiveFieldDataType.TEXT);
                        });
    }

    @Test
    @DisplayName("字段类型不兼容和摘要篡改均返回可定位错误")
    void incompatibleFieldAndTamperedDigestShouldBeRejected() {
        ArchiveRuntimeSnapshot snapshot = service.exportSnapshot(101L);
        when(fieldCatalogService.catalog(
                        101L, "CONTRACT", ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE))
                .thenReturn(catalog(ArchiveFieldDataType.INTEGER));

        assertThatThrownBy(
                        () ->
                                service.preflight(
                                        new ArchiveRuntimeSnapshotPreflightRequest(
                                                snapshot, null, null, null)))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        exception ->
                                assertThat(exception.fieldViolations())
                                        .anySatisfy(
                                                violation -> {
                                                    assertThat(violation.field())
                                                            .contains("title-required")
                                                            .contains("metadata.title");
                                                    assertThat(violation.message())
                                                            .contains("类型不兼容");
                                                }));

        ArchiveRuntimeSnapshot tampered =
                new ArchiveRuntimeSnapshot(
                        snapshot.schemaVersion(),
                        snapshot.sourceApplicationVersion(),
                        snapshot.exportedAt(),
                        snapshot.fileName(),
                        snapshot.scheme(),
                        snapshot.definitions(),
                        "0".repeat(64));
        assertThatThrownBy(
                        () ->
                                service.preflight(
                                        new ArchiveRuntimeSnapshotPreflightRequest(
                                                tampered, null, null, null)))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        exception ->
                                assertThat(exception.reason())
                                        .isEqualTo("ARCHIVE_RUNTIME_SNAPSHOT_DIGEST_MISMATCH"));
    }

    @Test
    @DisplayName("未知格式版本与超过一 MiB 的快照在深层预检前拒绝")
    void unsupportedVersionAndOversizedSnapshotShouldBeRejected() {
        ArchiveRuntimeSnapshot snapshot = service.exportSnapshot(101L);
        ArchiveRuntimeSnapshot unsupported =
                new ArchiveRuntimeSnapshot(
                        "999",
                        snapshot.sourceApplicationVersion(),
                        snapshot.exportedAt(),
                        snapshot.fileName(),
                        snapshot.scheme(),
                        snapshot.definitions(),
                        snapshot.sha256());
        assertThatThrownBy(
                        () ->
                                service.preflight(
                                        new ArchiveRuntimeSnapshotPreflightRequest(
                                                unsupported, null, null, null)))
                .hasMessageContaining("不支持");

        ArchiveRuntimeDefinition oversizedDefinition = definition("oversized", "metadata.title");
        oversizedDefinition.setConditionJson(
                Map.of(
                        "field",
                        "metadata.title",
                        "operator",
                        "EQ",
                        "value",
                        "x".repeat(ArchiveRuntimeSnapshotService.MAX_SNAPSHOT_BYTES + 1)));
        when(definitionRepository.findBySchemeVersionId(101L))
                .thenReturn(List.of(oversizedDefinition));
        ArchiveRuntimeSnapshot oversized = service.exportSnapshot(101L);
        assertThatThrownBy(
                        () ->
                                service.preflight(
                                        new ArchiveRuntimeSnapshotPreflightRequest(
                                                oversized, null, null, null)))
                .hasMessageContaining("大小超限");
    }

    private static ArchiveRuntimeDefinition definition(String code, String fieldCode) {
        ArchiveRuntimeDefinition definition = new ArchiveRuntimeDefinition();
        definition.setId(999L);
        definition.setSchemeVersionId(101L);
        definition.setDefinitionKind(ArchiveRuntimeDefinitionKind.CONSTRAINT);
        definition.setDefinitionCode(code);
        definition.setDefinitionName("字段必填");
        definition.setTriggerPoint(ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE);
        definition.setScopeCategoryCode("CONTRACT");
        definition.setScopeArchiveLevel(ArchiveLevel.ITEM);
        definition.setConditionJson(Map.of("field", fieldCode, "operator", "IS_NOT_NULL"));
        definition.setConstraintAction(ArchiveRuntimeActionType.REJECT);
        definition.setConstraintMessage("字段不能为空");
        definition.setStatus(ArchiveRuntimeStatus.DRAFT);
        return definition;
    }

    private static ArchiveRuntimeFieldCatalog catalog(ArchiveFieldDataType type) {
        return new ArchiveRuntimeFieldCatalog(
                101L,
                "CONTRACT",
                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE,
                "signature",
                List.of(
                        new ArchiveRuntimeField(
                                "metadata.title",
                                "题名",
                                type,
                                ArchiveRuntimeFieldSource.METADATA,
                                true,
                                true,
                                "CONTRACT")));
    }
}
