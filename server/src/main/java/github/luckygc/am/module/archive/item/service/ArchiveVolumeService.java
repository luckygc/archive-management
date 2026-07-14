package github.luckygc.am.module.archive.item.service;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.data.Order;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.PageRequest;
import jakarta.data.restrict.Restrict;
import jakarta.data.restrict.Restriction;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ArchiveDataScopeFilter;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeResolutionTypes.ResolvedArchiveDataScope;
import github.luckygc.am.module.archive.authorization.service.ArchiveDataScopeService;
import github.luckygc.am.module.archive.governance.service.ArchiveGovernanceService;
import github.luckygc.am.module.archive.item.ArchiveVolume;
import github.luckygc.am.module.archive.item._ArchiveVolume;
import github.luckygc.am.module.archive.item.repository.ArchiveVolumeDataRepository;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDto;
import github.luckygc.am.module.archive.mapper.ArchiveDataScopeSqlGroup;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsDto;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

@Service
public class ArchiveVolumeService {

    private static final String PERMISSION_ITEM_READ = "archive:item:read";
    private static final String PERMISSION_ITEM_CREATE = "archive:item:create";
    private static final String PERMISSION_ITEM_UPDATE = "archive:item:update";

    private final ArchiveMapper archiveMapper;
    private final ArchiveVolumeDataRepository archiveVolumeRepository;
    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveMetadataReferenceService archiveMetadataReferenceService;
    private final ArchiveCategoryService archiveCategoryService;
    private final ArchiveGovernanceService governanceService;
    private final ArchiveItemReadService archiveItemRoutingService;
    private final AuthorizationPermissionService permissionService;
    private final ArchiveDataScopeService dataScopeService;

    public ArchiveVolumeService(
            ArchiveMapper archiveMapper,
            ArchiveVolumeDataRepository archiveVolumeRepository,
            ArchiveMetadataService archiveMetadataService,
            ArchiveMetadataReferenceService archiveMetadataReferenceService,
            ArchiveCategoryService archiveCategoryService,
            ArchiveGovernanceService governanceService,
            ArchiveItemReadService archiveItemRoutingService,
            AuthorizationPermissionService permissionService,
            ArchiveDataScopeService dataScopeService) {
        this.archiveMapper = archiveMapper;
        this.archiveVolumeRepository = archiveVolumeRepository;
        this.archiveMetadataService = archiveMetadataService;
        this.archiveMetadataReferenceService = archiveMetadataReferenceService;
        this.archiveCategoryService = archiveCategoryService;
        this.governanceService = governanceService;
        this.archiveItemRoutingService = archiveItemRoutingService;
        this.permissionService = permissionService;
        this.dataScopeService = dataScopeService;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ArchiveVolumeResponse> listVolumes(
            @Nullable String fondsCode,
            @Nullable String categoryCode,
            PageRequest pageRequest,
            Long userId) {
        requirePermission(userId, PERMISSION_ITEM_READ);
        String requestedFondsCode = StringUtils.trimToNull(fondsCode);
        String requestedCategoryCode = StringUtils.trimToNull(categoryCode);
        ResolvedArchiveDataScope resolvedScope = dataScopeService.resolveUserDataScope(userId);
        if (resolvedScope.empty()) {
            return emptyVolumePage(pageRequest);
        }
        Restriction<ArchiveVolume> restriction =
                volumeRestriction(requestedFondsCode, requestedCategoryCode, resolvedScope, userId);
        if (restriction == null) {
            return emptyVolumePage(pageRequest);
        }
        CursoredPage<ArchiveVolume> page =
                archiveVolumeRepository.find(
                        restriction,
                        pageRequest,
                        Order.by(_ArchiveVolume.createdAt.desc(), _ArchiveVolume.id.desc()));
        return CursorPageResponse.from(page, pageRequest, this::toVolumeResponse);
    }

    private @Nullable Restriction<ArchiveVolume> volumeRestriction(
            @Nullable String fondsCode,
            @Nullable String categoryCode,
            ResolvedArchiveDataScope resolvedScope,
            Long userId) {
        List<Restriction<ArchiveVolume>> requestRestrictions = new ArrayList<>();
        if (fondsCode != null) {
            requestRestrictions.add(_ArchiveVolume.fondsCode.equalTo(fondsCode));
        }
        if (categoryCode != null) {
            requestRestrictions.add(_ArchiveVolume.categoryCode.equalTo(categoryCode));
        }
        if (resolvedScope.allData()) {
            return allRestrictions(requestRestrictions);
        }
        List<Restriction<ArchiveVolume>> scopeRestrictions = new ArrayList<>();
        for (ArchiveCategoryDto category : archiveCategoryService.listCategories(null)) {
            if (categoryCode != null && !categoryCode.equals(category.categoryCode())) {
                continue;
            }
            ArchiveDataScopeFilter filter =
                    dataScopeService.buildItemFilter(userId, category.id(), fondsCode);
            if (filter.empty()) {
                continue;
            }
            if (filter.allData()) {
                scopeRestrictions.add(_ArchiveVolume.categoryCode.equalTo(category.categoryCode()));
                continue;
            }
            for (ArchiveDataScopeSqlGroup group : filter.groups()) {
                Restriction<ArchiveVolume> groupRestriction =
                        volumeScopeGroupRestriction(category.categoryCode(), group);
                if (groupRestriction != null) {
                    scopeRestrictions.add(groupRestriction);
                }
            }
        }
        if (scopeRestrictions.isEmpty()) {
            return null;
        }
        requestRestrictions.add(Restrict.any(scopeRestrictions));
        return allRestrictions(requestRestrictions);
    }

    private @Nullable Restriction<ArchiveVolume> volumeScopeGroupRestriction(
            String categoryCode, ArchiveDataScopeSqlGroup group) {
        if (!group.conditions().isEmpty()) {
            return null;
        }
        List<Restriction<ArchiveVolume>> restrictions = new ArrayList<>();
        restrictions.add(_ArchiveVolume.categoryCode.equalTo(categoryCode));
        restrictions.add(stringValuesRestriction(_ArchiveVolume.fondsCode, group.fondsCodes()));
        restrictions.add(
                longValuesRestriction(_ArchiveVolume.securityLevelId, group.securityLevelIds()));
        restrictions.add(
                longValuesRestriction(
                        _ArchiveVolume.retentionPeriodId, group.retentionPeriodIds()));
        return allRestrictions(restrictions);
    }

    private Restriction<ArchiveVolume> stringValuesRestriction(
            jakarta.data.metamodel.TextAttribute<ArchiveVolume> attribute, List<String> values) {
        if (values.isEmpty()) {
            return Restrict.unrestricted();
        }
        return Restrict.any(values.stream().map(attribute::equalTo).toList());
    }

    private Restriction<ArchiveVolume> longValuesRestriction(
            jakarta.data.metamodel.NumericAttribute<ArchiveVolume, Long> attribute,
            List<Long> values) {
        if (values.isEmpty()) {
            return Restrict.unrestricted();
        }
        return Restrict.any(values.stream().map(attribute::equalTo).toList());
    }

    private Restriction<ArchiveVolume> allRestrictions(
            List<Restriction<ArchiveVolume>> restrictions) {
        return restrictions.isEmpty() ? Restrict.unrestricted() : Restrict.all(restrictions);
    }

    private CursorPageResponse<ArchiveVolumeResponse> emptyVolumePage(PageRequest pageRequest) {
        return CursorPageResponse.withCursorValues(
                List.of(),
                pageRequest.size(),
                null,
                null,
                null,
                null,
                pageRequest.requestTotal() ? 0L : null);
    }

    private ArchiveVolumeResponse toVolumeResponse(ArchiveVolume volume) {
        return new ArchiveVolumeResponse(
                volume.getId(),
                volume.getFondsCode(),
                volume.getFondsName(),
                volume.getCategoryCode(),
                volume.getCategoryName(),
                volume.getArchiveNo(),
                volume.getElectronicStatus(),
                volume.getArchiveYear(),
                volume.isLockedFlag(),
                volume.getLockReason(),
                volume.getLockedBy(),
                volume.getLockedAt(),
                volume.getCreatedAt());
    }

    public ArchiveVolumeDto getVolume(Long id, Long userId) {
        requirePermission(userId, PERMISSION_ITEM_READ);
        LoadedArchiveVolume volume = loadVolume(id);
        assertVolumeInDataScope(volume, userId);
        return volume.response();
    }

    public void assertVolumeInDataScope(Long id, Long userId) {
        assertVolumeInDataScope(loadVolume(id), userId);
    }

    private LoadedArchiveVolume loadVolume(Long id) {
        Map<String, Object> row = archiveMapper.getArchiveVolume(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "案卷不存在");
        }
        return new LoadedArchiveVolume(
                toVolumeDto(row),
                longOrNull(row, "securityLevelId"),
                longOrNull(row, "retentionPeriodId"));
    }

    @Transactional
    public ArchiveVolumeDto createVolume(CreateArchiveVolumeRequest request, Long userId) {
        requirePermission(userId, PERMISSION_ITEM_CREATE);
        if (request == null) {
            throw new BadRequestException("请求体不能为空");
        }
        if (request.categoryId() == null) {
            throw new BadRequestException("档案分类不能为空");
        }
        if (StringUtils.isBlank(request.fondsCode())) {
            throw new BadRequestException("全宗不能为空");
        }
        ArchiveCategoryDto category = archiveCategoryService.getCategory(request.categoryId());
        if (category.managementMode() != ArchiveManagementMode.VOLUME_ITEM) {
            throw new BadRequestException("该分类未启用案卷管理");
        }
        ArchiveFondsDto fonds =
                archiveMetadataReferenceService.getEnabledFondsByCode(request.fondsCode());
        assertProposedVolumeInDataScope(userId, category, fonds.fondsCode());
        int archiveYear =
                request.archiveYear() == null ? Year.now().getValue() : request.archiveYear();
        String archiveNo = StringUtils.trimToNull(request.archiveNo());
        ensureVolumeArchiveNoUnique(category.categoryCode(), archiveNo);
        Long governanceSchemeVersionId =
                governanceService
                        .requireDefaultVersionForNewArchive(
                                fonds.fondsCode(), category.categoryCode())
                        .getId();
        Long id;
        try {
            id =
                    archiveMapper.insertArchiveVolume(
                            fonds.fondsCode(),
                            fonds.fondsName(),
                            category.categoryCode(),
                            category.categoryName(),
                            archiveNo,
                            StringUtils.defaultIfBlank(request.electronicStatus(), "DRAFT"),
                            archiveYear,
                            governanceSchemeVersionId,
                            userId);
        } catch (DuplicateKeyException exception) {
            throw duplicateArchiveNo();
        }
        LoadedArchiveVolume volume = loadVolume(id);
        assertVolumeInDataScope(volume, userId);
        return volume.response();
    }

    private void ensureVolumeArchiveNoUnique(String categoryCode, @Nullable String archiveNo) {
        if (StringUtils.isBlank(archiveNo)) {
            return;
        }
        if (archiveMapper.countArchiveVolumesByArchiveNo(categoryCode, archiveNo, null) > 0) {
            throw duplicateArchiveNo();
        }
    }

    private BadRequestException duplicateArchiveNo() {
        return new BadRequestException("档号已存在", "archiveNo", "档号已存在");
    }

    @Transactional
    public void addItemToVolume(
            Long volumeId, Long archiveItemId, Integer displayOrder, Long userId) {
        if (archiveItemId == null || archiveItemId <= 0) {
            throw new BadRequestException("档案 ID 必须为正数", "itemId", "档案 ID 必须为正数");
        }
        requirePermission(userId, PERMISSION_ITEM_UPDATE);
        LoadedArchiveVolume loadedVolume = loadVolume(volumeId);
        ArchiveVolumeDto volume = loadedVolume.response();
        assertVolumeInDataScope(loadedVolume, userId);
        archiveItemRoutingService.assertItemInDataScope(archiveItemId, userId);
        archiveItemRoutingService.ensureItemEditable(archiveItemId);
        ArchiveItemDto item = archiveItemRoutingService.getItem(archiveItemId);
        if (!volume.fondsCode().equals(item.fondsCode())
                || !volume.categoryCode().equals(item.categoryCode())) {
            throw new BadRequestException("案卷和档案条目不属于同一全宗和分类");
        }
        int updated =
                archiveMapper.moveItemToVolume(
                        volumeId, archiveItemId, displayOrder == null ? 0 : displayOrder, userId);
        if (updated == 0) {
            throw new BadRequestException("档案条目已锁定或不存在，不能加入案卷");
        }
    }

    private void requirePermission(Long userId, String permissionCode) {
        userId = AuthenticatedUsers.requireResolvedUserId(userId);
        if (!permissionService.hasPermission(userId, permissionCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }

    private void assertVolumeInDataScope(LoadedArchiveVolume loadedVolume, Long userId) {
        ArchiveVolumeDto volume = loadedVolume.response();
        ArchiveCategoryDto category = getCategoryByCode(volume.categoryCode());
        ArchiveDataScopeFilter filter =
                dataScopeService.buildItemFilter(userId, category.id(), volume.fondsCode());
        if (filter.empty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
        }
        if (filter.allData()) {
            return;
        }
        if (!matchesFixedVolumeGroup(
                filter,
                volume.fondsCode(),
                loadedVolume.securityLevelId(),
                loadedVolume.retentionPeriodId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
        }
    }

    private void assertProposedVolumeInDataScope(
            Long userId, ArchiveCategoryDto category, String fondsCode) {
        ArchiveDataScopeFilter filter =
                dataScopeService.buildItemFilter(userId, category.id(), fondsCode);
        if (filter.empty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
        }
        if (filter.allData()) {
            return;
        }
        if (!matchesFixedVolumeGroup(filter, fondsCode, null, null)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "数据范围不足");
        }
    }

    private boolean matchesFixedVolumeGroup(
            ArchiveDataScopeFilter filter,
            String fondsCode,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId) {
        return filter.groups().stream()
                .filter(group -> group.conditions().isEmpty())
                .anyMatch(
                        group ->
                                matchesAllowedValue(group.fondsCodes(), fondsCode)
                                        && matchesAllowedValue(
                                                group.securityLevelIds(), securityLevelId)
                                        && matchesAllowedValue(
                                                group.retentionPeriodIds(), retentionPeriodId));
    }

    private boolean matchesAllowedValue(List<?> allowedValues, @Nullable Object actualValue) {
        return allowedValues.isEmpty()
                || (actualValue != null && allowedValues.contains(actualValue));
    }

    private ArchiveCategoryDto getCategoryByCode(String categoryCode) {
        return archiveCategoryService.listCategories(null).stream()
                .filter(category -> category.categoryCode().equals(categoryCode))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "档案分类不存在"));
    }

    private ArchiveVolumeDto toVolumeDto(Map<String, Object> row) {
        return new ArchiveVolumeDto(
                number(row, "id").longValue(),
                string(row, "fondsCode"),
                string(row, "fondsName"),
                string(row, "categoryCode"),
                string(row, "categoryName"),
                string(row, "archiveNo"),
                string(row, "electronicStatus"),
                number(row, "archiveYear").intValue(),
                bool(row, "lockedFlag"),
                string(row, "lockReason"),
                longOrNull(row, "lockedBy"),
                dateTime(row, "lockedAt"));
    }

    private Number number(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("缺少数值字段：" + key);
    }

    private boolean bool(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return Boolean.TRUE.equals(value);
    }

    private String string(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private Long longOrNull(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private LocalDateTime dateTime(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    private Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }

    public record CreateArchiveVolumeRequest(
            Long categoryId,
            String fondsCode,
            String archiveNo,
            Integer archiveYear,
            String electronicStatus) {}

    public record AddItemToVolumeRequest(Long itemId, Integer displayOrder) {}

    public record ArchiveVolumeDto(
            Long id,
            String fondsCode,
            String fondsName,
            String categoryCode,
            String categoryName,
            String archiveNo,
            String electronicStatus,
            int archiveYear,
            boolean lockedFlag,
            String lockReason,
            Long lockedBy,
            LocalDateTime lockedAt) {}

    public record ArchiveVolumeResponse(
            Long id,
            String fondsCode,
            String fondsName,
            String categoryCode,
            String categoryName,
            @Nullable String archiveNo,
            String electronicStatus,
            int archiveYear,
            boolean lockedFlag,
            @Nullable String lockReason,
            @Nullable Long lockedBy,
            @Nullable LocalDateTime lockedAt,
            LocalDateTime createdAt) {}

    private record LoadedArchiveVolume(
            ArchiveVolumeDto response,
            @Nullable Long securityLevelId,
            @Nullable Long retentionPeriodId) {}
}
