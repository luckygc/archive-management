package github.luckygc.am.module.archive.rule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

import tools.jackson.databind.json.JsonMapper;

@DisplayName("运行时配置快照导出")
class ArchiveRuntimeSnapshotExportTests {

    @Test
    @DisplayName("重复导出规范化配置摘要稳定且不泄露数据库 ID")
    void repeatedExportShouldBeCanonicalAndDatabaseIdFree() throws Exception {
        SnapshotFixture fixture = fixture();
        ArchiveRuntimeSnapshotService first =
                fixture.service(Clock.fixed(Instant.parse("2026-07-18T08:00:00Z"), ZoneOffset.UTC));
        ArchiveRuntimeSnapshotService second =
                fixture.service(Clock.fixed(Instant.parse("2026-07-18T09:00:00Z"), ZoneOffset.UTC));

        var firstSnapshot = first.exportSnapshot(101L);
        var secondSnapshot = second.exportSnapshot(101L);

        assertThat(firstSnapshot.sha256()).hasSize(64).isEqualTo(secondSnapshot.sha256());
        assertThat(firstSnapshot.exportedAt()).isNotEqualTo(secondSnapshot.exportedAt());
        assertThat(firstSnapshot.schemaVersion()).isEqualTo("1");
        assertThat(firstSnapshot.definitions().getFirst().sourceStatus())
                .isEqualTo(ArchiveRuntimeStatus.DRAFT);
        assertThat(firstSnapshot.definitions().getFirst().fieldReferences())
                .extracting(
                        ArchiveRuntimeSnapshotService.SnapshotFieldReference::fieldCode,
                        ArchiveRuntimeSnapshotService.SnapshotFieldReference::dataType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "context.userId", ArchiveFieldDataType.INTEGER));

        String json =
                JsonMapper.builder().findAndAddModules().build().writeValueAsString(firstSnapshot);
        assertThat(json)
                .doesNotContain("\"id\":")
                .doesNotContain("999")
                .doesNotContain("archiveRecords")
                .doesNotContain("permissions")
                .doesNotContain("traces");
    }

    private static SnapshotFixture fixture() {
        ArchiveGovernanceSchemeDataRepository schemeRepository =
                mock(ArchiveGovernanceSchemeDataRepository.class);
        ArchiveGovernanceSchemeVersionDataRepository versionRepository =
                mock(ArchiveGovernanceSchemeVersionDataRepository.class);
        ArchiveGovernanceScopeDataRepository scopeRepository =
                mock(ArchiveGovernanceScopeDataRepository.class);
        ArchiveRuntimeDefinitionDataRepository definitionRepository =
                mock(ArchiveRuntimeDefinitionDataRepository.class);
        ArchiveRuntimeActionDataRepository actionRepository =
                mock(ArchiveRuntimeActionDataRepository.class);
        ArchiveRuntimeDefinitionService definitionService =
                mock(ArchiveRuntimeDefinitionService.class);
        ArchiveRuntimeFieldCatalogService fieldCatalogService =
                mock(ArchiveRuntimeFieldCatalogService.class);
        ArchiveGovernanceScheme scheme = new ArchiveGovernanceScheme();
        scheme.setId(10L);
        scheme.setSchemeCode("runtime-policy");
        scheme.setSchemeName("运行时策略");
        ArchiveGovernanceSchemeVersion version = new ArchiveGovernanceSchemeVersion();
        version.setId(101L);
        version.setSchemeId(10L);
        version.setVersionCode("v1");
        ArchiveRuntimeDefinition definition = new ArchiveRuntimeDefinition();
        definition.setId(999L);
        definition.setSchemeVersionId(101L);
        definition.setDefinitionKind(ArchiveRuntimeDefinitionKind.CONSTRAINT);
        definition.setDefinitionCode("user-required");
        definition.setDefinitionName("用户必须存在");
        definition.setTriggerPoint(ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE);
        definition.setScopeArchiveLevel(ArchiveLevel.ITEM);
        definition.setPriority(20);
        definition.setConditionJson(
                Map.of("field", "context.userId", "operator", "GT", "value", 0));
        definition.setConstraintAction(ArchiveRuntimeActionType.REJECT);
        definition.setConstraintMessage("用户无效");
        definition.setStatus(ArchiveRuntimeStatus.DRAFT);
        when(versionRepository.findById(101L)).thenReturn(java.util.Optional.of(version));
        when(schemeRepository.findById(10L)).thenReturn(java.util.Optional.of(scheme));
        when(scopeRepository.findBySchemeVersionId(101L)).thenReturn(List.of());
        when(definitionRepository.findBySchemeVersionId(101L)).thenReturn(List.of(definition));
        when(actionRepository.findByDefinitionId(999L)).thenReturn(List.of());
        when(fieldCatalogService.catalog(101L, null, ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE))
                .thenReturn(catalog(ArchiveFieldDataType.INTEGER));
        return new SnapshotFixture(
                schemeRepository,
                versionRepository,
                scopeRepository,
                definitionRepository,
                actionRepository,
                definitionService,
                fieldCatalogService);
    }

    private static ArchiveRuntimeFieldCatalog catalog(ArchiveFieldDataType type) {
        return new ArchiveRuntimeFieldCatalog(
                101L,
                null,
                ArchiveRuntimeTriggerPoint.ITEM_BEFORE_CREATE,
                "signature",
                List.of(
                        new ArchiveRuntimeField(
                                "context.userId",
                                "当前用户",
                                type,
                                ArchiveRuntimeFieldSource.CONTEXT,
                                true,
                                false,
                                null)));
    }

    private record SnapshotFixture(
            ArchiveGovernanceSchemeDataRepository schemeRepository,
            ArchiveGovernanceSchemeVersionDataRepository versionRepository,
            ArchiveGovernanceScopeDataRepository scopeRepository,
            ArchiveRuntimeDefinitionDataRepository definitionRepository,
            ArchiveRuntimeActionDataRepository actionRepository,
            ArchiveRuntimeDefinitionService definitionService,
            ArchiveRuntimeFieldCatalogService fieldCatalogService) {

        private ArchiveRuntimeSnapshotService service(Clock clock) {
            return new ArchiveRuntimeSnapshotService(
                    schemeRepository,
                    versionRepository,
                    scopeRepository,
                    definitionRepository,
                    actionRepository,
                    definitionService,
                    fieldCatalogService,
                    JsonMapper.builder().findAndAddModules().build(),
                    clock);
        }
    }
}
