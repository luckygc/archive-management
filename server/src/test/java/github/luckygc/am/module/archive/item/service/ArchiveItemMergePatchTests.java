package github.luckygc.am.module.archive.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.item.repository.ArchiveItemAuditDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDetailDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDto;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeExecutionService;
import github.luckygc.am.module.archive.rule.service.ArchiveRuntimeTraceService;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("档案条目 JSON Merge Patch")
class ArchiveItemMergePatchTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ArchiveItemReadService readService = mock(ArchiveItemReadService.class);
    private final ArchiveMetadataReferenceService referenceService =
            mock(ArchiveMetadataReferenceService.class);
    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final ArchiveItemAuditDataRepository auditRepository =
            mock(ArchiveItemAuditDataRepository.class);
    private final AuthorizationPermissionService permissionService =
            mock(AuthorizationPermissionService.class);
    private ArchiveItemService service;
    private ArchiveItemDetailDto before;

    @BeforeEach
    void setUp() {
        service =
                new ArchiveItemService(
                        mock(ArchiveMetadataService.class),
                        referenceService,
                        mock(ArchiveCategoryService.class),
                        archiveMapper,
                        mock(ArchiveItemSearchProjectionSynchronizer.class),
                        mock(ArchiveDataScopeService.class),
                        permissionService,
                        auditRepository,
                        readService,
                        new ArchiveItemFieldValueConverter(),
                        mock(ArchiveRuntimeExecutionService.class),
                        mock(ArchiveRuntimeTraceService.class),
                        jsonMapper);
        ArchiveItemDto item =
                new ArchiveItemDto(
                        7L,
                        null,
                        "F001",
                        "默认全宗",
                        "contract",
                        "合同档案",
                        "A-001",
                        null,
                        null,
                        2026,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null);
        ArchiveCategoryDto category = mock(ArchiveCategoryDto.class);
        when(category.itemTableName()).thenReturn("am_archive_item_contract");
        before =
                new ArchiveItemDetailDto(
                        item, category, List.of(), Map.of("title", "原题名"), List.of(), Map.of());
        when(permissionService.hasPermission(9L, "archive:item:update")).thenReturn(true);
        when(readService.getItemDetail(7L, 9L, ArchiveLayoutSurface.EDIT)).thenReturn(before);
        when(archiveMapper.tableExists("am_archive_item_contract")).thenReturn(1);
    }

    @Test
    @DisplayName("空补丁不写入，不访问全宗")
    void emptyPatchIsNoOp() {
        assertThat(service.patchItem(7L, jsonMapper.readTree("{}"), 9L)).isSameAs(before);
        assertThat(jsonMapper.valueToTree(before.item()).has("securityLevelId")).isFalse();
        verifyNoInteractions(referenceService, archiveMapper, auditRepository);
    }

    @Test
    @DisplayName("详情表示省略空动态字段值")
    void detailRepresentationOmitsNullDynamicValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", null);
        values.put("year", 2026);
        ArchiveItemDetailDto detail =
                new ArchiveItemDetailDto(
                        before.item(), before.category(), List.of(), values, List.of(), Map.of());

        var json = jsonMapper.valueToTree(detail);

        assertThat(json.get("dynamicFields").has("title")).isFalse();
        assertThat(json.get("dynamicFields").get("year").asInt()).isEqualTo(2026);
    }

    @Test
    @DisplayName("固定字段补丁保留未提交的全宗编码")
    void itemPatchPreservesUnmentionedFonds() {
        when(referenceService.getWritableFondsByCode("F001"))
                .thenThrow(new BadRequestException("已进入更新核心"));

        assertThatThrownBy(
                        () ->
                                service.patchItem(
                                        7L,
                                        jsonMapper.readTree("{\"item\":{\"archiveNo\":\"A-002\"}}"),
                                        9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("已进入更新核心");
        verify(referenceService).getWritableFondsByCode("F001");
    }

    @Test
    @DisplayName("必需和只读成员删除及非对象字段集合被拒绝")
    void rejectsInvalidPatch() {
        for (String patch :
                List.of(
                        "{\"item\":{\"fondsCode\":null}}",
                        "{\"item\":{\"id\":null}}",
                        "{\"category\":null}",
                        "{\"dynamicFields\":null}",
                        "{\"physicalFieldValues\":[]}",
                        "[]")) {
            assertThatThrownBy(() -> service.patchItem(7L, jsonMapper.readTree(patch), 9L))
                    .isInstanceOf(BadRequestException.class);
        }
        verifyNoInteractions(referenceService, archiveMapper, auditRepository);
    }
}
