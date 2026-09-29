package github.luckygc.am.module.archive.metadata.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

import jakarta.data.page.PageRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import github.luckygc.am.common.api.CollectionResponse;
import github.luckygc.am.common.api.CursorPageResponse;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.common.security.AuthenticatedUsers;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.ArchiveFondsStatus;
import github.luckygc.am.module.archive.metadata.ArchiveLayoutSurface;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.service.ArchiveCategoryService;
import github.luckygc.am.module.archive.metadata.service.ArchiveFondsService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataReferenceService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataService;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldLayoutDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldLayoutRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFieldRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsCategoryScopeDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsCategoryScopeRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsEventDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveRetentionPeriodDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveSecurityLevelDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveUniqueConstraintDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveUniqueConstraintRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.AssignArchiveFondsNumberRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.CloseArchiveFondsRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.CreateArchiveFondsRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ReopenArchiveFondsRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveCategoryRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveFondsRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveRetentionPeriodRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.UpdateArchiveSecurityLevelRequest;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionCode;
import github.luckygc.am.module.authorization.service.AuthorizationPermissionService;

import tools.jackson.databind.JsonNode;

@RestController
public class ArchiveMetadataController {

    private static final Set<String> FONDS_PATCH_FIELDS =
            Set.of("fondsName", "startDate", "endDate", "historyNote", "sortOrder");
    private static final Set<String> FONDS_READ_ONLY_FIELDS =
            Set.of(
                    "id",
                    "fondsCode",
                    "fondsNo",
                    "status",
                    "numberAssignedBy",
                    "numberAssignedAt",
                    "closedAt",
                    "closureReason",
                    "createdAt",
                    "updatedAt");
    private static final Set<String> REFERENCE_READ_ONLY_FIELDS =
            Set.of("id", "enabled", "sortOrder", "createdAt", "updatedAt");
    private static final Set<String> CATEGORY_PATCH_FIELDS =
            Set.of(
                    "categoryCode",
                    "categoryName",
                    "parentId",
                    "managementMode",
                    "enabled",
                    "sortOrder");
    private static final Set<String> CATEGORY_READ_ONLY_FIELDS =
            Set.of(
                    "id",
                    "volumeTableName",
                    "itemTableName",
                    "volumePhysicalTableName",
                    "itemPhysicalTableName",
                    "tableStatus",
                    "builtAt",
                    "createdAt",
                    "updatedAt");

    private final ArchiveMetadataService archiveMetadataService;
    private final ArchiveMetadataReferenceService archiveMetadataReferenceService;
    private final ArchiveCategoryService archiveCategoryService;
    private final ArchiveFondsService archiveFondsService;
    private final AuthorizationPermissionService permissionService;

    public ArchiveMetadataController(
            ArchiveMetadataService archiveMetadataService,
            ArchiveMetadataReferenceService archiveMetadataReferenceService,
            ArchiveCategoryService archiveCategoryService,
            ArchiveFondsService archiveFondsService,
            AuthorizationPermissionService permissionService) {
        this.archiveMetadataService = archiveMetadataService;
        this.archiveMetadataReferenceService = archiveMetadataReferenceService;
        this.archiveCategoryService = archiveCategoryService;
        this.archiveFondsService = archiveFondsService;
        this.permissionService = permissionService;
    }

    @GetMapping("/archive-fonds")
    public CollectionResponse<ArchiveFondsDto> listFonds(
            @RequestParam(required = false) ArchiveFondsStatus status) {
        return CollectionResponse.of(archiveFondsService.listFonds(status));
    }

    @PostMapping("/archive-fonds")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveFondsDto createFonds(
            @RequestBody CreateArchiveFondsRequest request, Authentication authentication) {
        return archiveFondsService.createFonds(request, requireMetadataManage(authentication));
    }

    @PatchMapping(value = "/archive-fonds/{id}", consumes = "application/merge-patch+json")
    public ArchiveFondsDto updateFonds(
            @PathVariable Long id, @RequestBody JsonNode request, Authentication authentication) {
        Long userId = requireMetadataManage(authentication);
        return archiveFondsService.updateFonds(id, toFondsPatch(request), userId);
    }

    private UpdateArchiveFondsRequest toFondsPatch(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new BadRequestException("全宗补丁必须是对象");
        }
        for (String fieldName : request.propertyNames()) {
            if (!FONDS_PATCH_FIELDS.contains(fieldName)
                    && (FONDS_READ_ONLY_FIELDS.contains(fieldName)
                            || !request.get(fieldName).isNull())) {
                throw new BadRequestException("不支持修改字段 " + fieldName, fieldName, "字段不可修改");
            }
        }
        return new UpdateArchiveFondsRequest(
                fondsText(request, "fondsName", false),
                request.has("startDate"),
                fondsDate(request, "startDate"),
                request.has("endDate"),
                fondsDate(request, "endDate"),
                request.has("historyNote"),
                fondsText(request, "historyNote", true),
                fondsInteger(request, "sortOrder"));
    }

    private @Nullable String fondsText(JsonNode request, String field, boolean removable) {
        JsonNode value = request.get(field);
        if (value == null || (value.isNull() && removable)) {
            return null;
        }
        if (value.isNull() || !value.isTextual()) {
            throw new BadRequestException(field + " 不合法", field, field + " 不合法");
        }
        return value.asText();
    }

    private @Nullable LocalDate fondsDate(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new BadRequestException(field + " 不合法", field, field + " 不合法");
        }
        try {
            return LocalDate.parse(value.asText());
        } catch (DateTimeParseException exception) {
            throw new BadRequestException(field + " 不合法", field, field + " 不合法");
        }
    }

    private @Nullable Integer fondsInteger(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null) {
            return null;
        }
        if (value.isNull() || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new BadRequestException(field + " 不合法", field, field + " 不合法");
        }
        return value.asInt();
    }

    @PostMapping("/archive-fonds/{id}:assignNumber")
    public ArchiveFondsDto assignFondsNumber(
            @PathVariable Long id,
            @RequestBody AssignArchiveFondsNumberRequest request,
            Authentication authentication) {
        return archiveFondsService.assignNumber(id, request, requireMetadataManage(authentication));
    }

    @PostMapping("/archive-fonds/{id}:close")
    public ArchiveFondsDto closeFonds(
            @PathVariable Long id,
            @RequestBody CloseArchiveFondsRequest request,
            Authentication authentication) {
        return archiveFondsService.closeFonds(id, request, requireMetadataManage(authentication));
    }

    @PostMapping("/archive-fonds/{id}:reopen")
    public ArchiveFondsDto reopenFonds(
            @PathVariable Long id,
            @RequestBody ReopenArchiveFondsRequest request,
            Authentication authentication) {
        return archiveFondsService.reopenFonds(id, request, requireMetadataManage(authentication));
    }

    @GetMapping("/archive-fonds/{id}/events")
    public CursorPageResponse<ArchiveFondsEventDto> listFondsEvents(
            @PathVariable Long id, PageRequest pageRequest) {
        return archiveFondsService.listEvents(id, pageRequest);
    }

    @GetMapping("/archive-fonds/{fondsCode}/category-scopes")
    public CollectionResponse<ArchiveFondsCategoryScopeDto> listFondsCategoryScopes(
            @PathVariable String fondsCode) {
        return CollectionResponse.of(archiveCategoryService.listFondsCategoryScopes(fondsCode));
    }

    @PutMapping("/archive-fonds/{fondsCode}/category-scopes")
    public CollectionResponse<ArchiveFondsCategoryScopeDto> saveFondsCategoryScopes(
            @PathVariable String fondsCode,
            @RequestBody java.util.List<ArchiveFondsCategoryScopeRequest> requests,
            Authentication authentication) {
        return CollectionResponse.of(
                archiveCategoryService.saveFondsCategoryScopes(
                        fondsCode, requests, requireMetadataManage(authentication)));
    }

    @GetMapping("/archive-fonds/{fondsCode}/categories")
    public CollectionResponse<ArchiveCategoryDto> listCategoriesForFonds(
            @PathVariable String fondsCode, Boolean enabled) {
        return CollectionResponse.of(
                archiveCategoryService.listCategoriesForFonds(fondsCode, enabled));
    }

    @GetMapping("/archive-security-levels")
    public CollectionResponse<ArchiveSecurityLevelDto> listSecurityLevels(Boolean enabled) {
        return CollectionResponse.of(archiveMetadataReferenceService.listSecurityLevels(enabled));
    }

    @PatchMapping(
            value = "/archive-security-levels/{id}",
            consumes = "application/merge-patch+json")
    public ArchiveSecurityLevelDto updateSecurityLevel(
            @PathVariable Long id, @RequestBody JsonNode request, Authentication authentication) {
        requireMetadataManage(authentication);
        return archiveMetadataReferenceService.updateSecurityLevel(
                id,
                new UpdateArchiveSecurityLevelRequest(referenceNamePatch(request, "levelName")));
    }

    @GetMapping("/archive-retention-periods")
    public CollectionResponse<ArchiveRetentionPeriodDto> listRetentionPeriods(Boolean enabled) {
        return CollectionResponse.of(archiveMetadataReferenceService.listRetentionPeriods(enabled));
    }

    @PatchMapping(
            value = "/archive-retention-periods/{id}",
            consumes = "application/merge-patch+json")
    public ArchiveRetentionPeriodDto updateRetentionPeriod(
            @PathVariable Long id, @RequestBody JsonNode request, Authentication authentication) {
        requireMetadataManage(authentication);
        return archiveMetadataReferenceService.updateRetentionPeriod(
                id,
                new UpdateArchiveRetentionPeriodRequest(referenceNamePatch(request, "periodName")));
    }

    private @Nullable String referenceNamePatch(JsonNode request, String nameField) {
        if (request == null || !request.isObject()) {
            throw new BadRequestException("补丁必须是对象");
        }
        for (String fieldName : request.propertyNames()) {
            if (!fieldName.equals(nameField)
                    && (REFERENCE_READ_ONLY_FIELDS.contains(fieldName)
                            || !request.get(fieldName).isNull())) {
                throw new BadRequestException("不支持修改字段 " + fieldName, fieldName, "字段不可修改");
            }
        }
        JsonNode value = request.get(nameField);
        if (value == null) {
            return null;
        }
        if (value.isNull() || !value.isTextual()) {
            throw new BadRequestException(nameField + " 不合法", nameField, nameField + " 不合法");
        }
        return value.asText();
    }

    @GetMapping("/archive-categories")
    public CollectionResponse<ArchiveCategoryDto> listCategories(Boolean enabled) {
        return CollectionResponse.of(archiveCategoryService.listCategories(enabled));
    }

    @PostMapping("/archive-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveCategoryDto createCategory(
            @RequestBody ArchiveCategoryRequest request, Authentication authentication) {
        return archiveCategoryService.createCategory(
                request, requireMetadataManage(authentication));
    }

    @PatchMapping(value = "/archive-categories/{id}", consumes = "application/merge-patch+json")
    public ArchiveCategoryDto updateCategory(
            @PathVariable Long id, @RequestBody JsonNode request, Authentication authentication) {
        Long operatorUserId = requireMetadataManage(authentication);
        return archiveCategoryService.updateCategory(id, toCategoryPatch(request), operatorUserId);
    }

    private UpdateArchiveCategoryRequest toCategoryPatch(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new BadRequestException("档案分类补丁必须是对象");
        }
        for (String field : request.propertyNames()) {
            if (!CATEGORY_PATCH_FIELDS.contains(field)
                    && (CATEGORY_READ_ONLY_FIELDS.contains(field)
                            || !request.get(field).isNull())) {
                throw new BadRequestException("不支持修改字段 " + field, field, "字段不可修改");
            }
        }
        return new UpdateArchiveCategoryRequest(
                fondsText(request, "categoryCode", false),
                fondsText(request, "categoryName", false),
                request.has("parentId"),
                categoryParentId(request),
                categoryManagementMode(request),
                categoryEnabled(request),
                fondsInteger(request, "sortOrder"));
    }

    private @Nullable Long categoryParentId(JsonNode request) {
        JsonNode value = request.get("parentId");
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new BadRequestException("parentId 不合法", "parentId", "parentId 不合法");
        }
        return value.longValue();
    }

    private @Nullable ArchiveManagementMode categoryManagementMode(JsonNode request) {
        JsonNode value = request.get("managementMode");
        if (value == null) {
            return null;
        }
        if (value.isNull() || !value.isTextual()) {
            throw new BadRequestException(
                    "managementMode 不合法", "managementMode", "managementMode 不合法");
        }
        try {
            return ArchiveManagementMode.valueOf(value.asText());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "managementMode 不合法", "managementMode", "managementMode 不合法");
        }
    }

    private @Nullable Boolean categoryEnabled(JsonNode request) {
        JsonNode value = request.get("enabled");
        if (value == null) {
            return null;
        }
        if (value.isNull() || !value.isBoolean()) {
            throw new BadRequestException("enabled 不合法", "enabled", "enabled 不合法");
        }
        return value.asBoolean();
    }

    @DeleteMapping("/archive-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id, Authentication authentication) {
        archiveCategoryService.deleteCategory(id, requireMetadataManage(authentication));
    }

    @GetMapping("/archive-categories/{categoryId}/fields")
    public CollectionResponse<ArchiveFieldDto> listFields(
            @PathVariable Long categoryId,
            @RequestParam(required = false) ArchiveLevel archiveLevel) {
        return CollectionResponse.of(
                archiveLevel == null
                        ? archiveMetadataService.listFields(categoryId)
                        : archiveMetadataService.listFields(categoryId, archiveLevel));
    }

    @PostMapping("/archive-categories/{categoryId}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveFieldDto createField(
            @PathVariable Long categoryId,
            @RequestBody ArchiveFieldRequest request,
            Authentication authentication) {
        return archiveMetadataService.createField(
                categoryId, request, requireMetadataManage(authentication));
    }

    @PatchMapping(
            value = "/archive-categories/{categoryId}/fields/{fieldId}",
            consumes = "application/merge-patch+json")
    public ArchiveFieldDto updateField(
            @PathVariable Long categoryId,
            @PathVariable Long fieldId,
            @RequestBody JsonNode request,
            Authentication authentication) {
        return archiveMetadataService.patchField(
                categoryId, fieldId, request, requireMetadataManage(authentication));
    }

    @DeleteMapping("/archive-categories/{categoryId}/fields/{fieldId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteField(
            @PathVariable Long categoryId,
            @PathVariable Long fieldId,
            Authentication authentication) {
        archiveMetadataService.deleteField(
                categoryId, fieldId, requireMetadataManage(authentication));
    }

    @GetMapping("/archive-categories/{categoryId}/layouts/{surface}")
    public ArchiveFieldLayoutDto getFieldLayout(
            @PathVariable Long categoryId,
            @PathVariable ArchiveLayoutSurface surface,
            @RequestParam(required = false) ArchiveLevel archiveLevel,
            @RequestParam(required = false) ArchiveFieldScope fieldScope) {
        return archiveMetadataService.getFieldLayout(categoryId, archiveLevel, fieldScope, surface);
    }

    @PutMapping("/archive-categories/{categoryId}/layouts/{surface}")
    public ArchiveFieldLayoutDto savePublicFieldLayout(
            @PathVariable Long categoryId,
            @PathVariable ArchiveLayoutSurface surface,
            @RequestParam(required = false) ArchiveLevel archiveLevel,
            @RequestParam(required = false) ArchiveFieldScope fieldScope,
            @RequestBody ArchiveFieldLayoutRequest request,
            Authentication authentication) {
        return archiveMetadataService.savePublicFieldLayout(
                categoryId,
                archiveLevel,
                fieldScope,
                surface,
                request,
                requireMetadataManage(authentication));
    }

    @PostMapping("/archive-categories/{id}:buildTable")
    public ArchiveCategoryDto buildTable(
            @PathVariable Long id,
            @RequestParam(required = false) ArchiveLevel archiveLevel,
            @RequestParam(required = false) ArchiveFieldScope fieldScope,
            Authentication authentication) {
        return archiveMetadataService.buildTable(
                id, archiveLevel, fieldScope, requireMetadataManage(authentication));
    }

    @GetMapping("/archive-categories/{categoryId}/unique-constraints")
    public CollectionResponse<ArchiveUniqueConstraintDto> listUniqueConstraints(
            @PathVariable Long categoryId) {
        return CollectionResponse.of(archiveMetadataService.listUniqueConstraints(categoryId));
    }

    @PostMapping("/archive-categories/{categoryId}/unique-constraints")
    @ResponseStatus(HttpStatus.CREATED)
    public ArchiveUniqueConstraintDto createUniqueConstraint(
            @PathVariable Long categoryId,
            @RequestBody ArchiveUniqueConstraintRequest request,
            Authentication authentication) {
        return archiveMetadataService.createUniqueConstraint(
                categoryId, request, requireMetadataManage(authentication));
    }

    @PatchMapping(
            value = "/archive-categories/{categoryId}/unique-constraints/{constraintId}",
            consumes = "application/merge-patch+json")
    public ArchiveUniqueConstraintDto updateUniqueConstraint(
            @PathVariable Long categoryId,
            @PathVariable Long constraintId,
            @RequestBody JsonNode request,
            Authentication authentication) {
        return archiveMetadataService.patchUniqueConstraint(
                categoryId, constraintId, request, requireMetadataManage(authentication));
    }

    @DeleteMapping("/archive-categories/{categoryId}/unique-constraints/{constraintId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUniqueConstraint(
            @PathVariable Long categoryId,
            @PathVariable Long constraintId,
            Authentication authentication) {
        archiveMetadataService.deleteUniqueConstraint(
                categoryId, constraintId, requireMetadataManage(authentication));
    }

    private Long requireMetadataManage(Authentication authentication) {
        Long userId =
                AuthenticatedUsers.requireUserId(
                        authentication == null ? null : authentication.getPrincipal());
        permissionService.requirePermission(
                userId, AuthorizationPermissionCode.ARCHIVE_METADATA_MANAGE);
        return userId;
    }
}
