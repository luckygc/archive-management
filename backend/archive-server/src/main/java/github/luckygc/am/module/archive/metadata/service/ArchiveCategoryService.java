package github.luckygc.am.module.archive.metadata.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.data.exceptions.DataException;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.mapper.ArchiveMapper;
import github.luckygc.am.module.archive.metadata.ArchiveCategory;
import github.luckygc.am.module.archive.metadata.ArchiveFonds;
import github.luckygc.am.module.archive.metadata.ArchiveFondsCategoryScope;
import github.luckygc.am.module.archive.metadata.ArchiveManagementMode;
import github.luckygc.am.module.archive.metadata.ArchiveTableStatus;
import github.luckygc.am.module.archive.metadata.repository.ArchiveCategoryDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsCategoryScopeDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFondsDataRepository;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveCategoryRequest;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsCategoryScopeDto;
import github.luckygc.am.module.archive.metadata.service.ArchiveMetadataTypes.ArchiveFondsCategoryScopeRequest;

@Service
public class ArchiveCategoryService {

    private static final String CATEGORY_CODE_UNIQUE_CONSTRAINT = "uk_am_archive_category_code";
    private static final int CATEGORY_CODE_MAX_LENGTH = 100;
    private static final int CATEGORY_NAME_MAX_LENGTH = 255;

    private final ArchiveMapper archiveMapper;
    private final ArchiveFondsDataRepository fondsRepository;
    private final ArchiveFondsCategoryScopeDataRepository fondsCategoryScopeRepository;
    private final ArchiveCategoryDataRepository categoryRepository;

    public ArchiveCategoryService(
            ArchiveMapper archiveMapper,
            ArchiveFondsDataRepository fondsRepository,
            ArchiveFondsCategoryScopeDataRepository fondsCategoryScopeRepository,
            ArchiveCategoryDataRepository categoryRepository) {
        this.archiveMapper = archiveMapper;
        this.fondsRepository = fondsRepository;
        this.fondsCategoryScopeRepository = fondsCategoryScopeRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<ArchiveCategoryDto> listCategories(@Nullable Boolean enabled) {
        List<ArchiveCategory> categories =
                enabled == null ? categoryRepository.list() : categoryRepository.list(enabled);
        return categories.stream().map(this::mapCategory).toList();
    }

    public List<ArchiveCategoryDto> listCategoriesForFonds(
            String fondsCode, @Nullable Boolean enabled) {
        ArchiveFonds fonds = loadEnabledFondsByCode(fondsCode);
        List<ArchiveFondsCategoryScope> scopes =
                fondsCategoryScopeRepository.findByFondsCode(fonds.getFondsCode());
        return scopedCategories(scopes, enabled).stream().map(this::mapCategory).toList();
    }

    @Transactional
    public ArchiveCategoryDto createCategory(ArchiveCategoryRequest request, Long userId) {
        String categoryCode = requireCategoryCode(request.categoryCode());
        String categoryName = requireCategoryName(request.categoryName());
        if (categoryRepository.findByCategoryCode(categoryCode) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "分类编码已存在");
        }
        validateParentCategory(null, request.parentId());
        ArchiveCategory category = new ArchiveCategory();
        category.setParentId(request.parentId());
        category.setCategoryCode(categoryCode);
        category.setCategoryName(categoryName);
        category.setManagementMode(normalizeManagementMode(request.managementMode()));
        category.setEnabled(request.enabled() == null || request.enabled());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        try {
            return mapCategory(categoryRepository.insert(category));
        } catch (DataIntegrityViolationException | DataException exception) {
            if (isCategoryCodeUniqueViolation(exception)) {
                throw categoryCodeConflict(exception);
            }
            throw exception;
        }
    }

    @Transactional
    public ArchiveCategoryDto updateCategory(Long id, ArchiveCategoryRequest request, Long userId) {
        requireId(id);
        ArchiveCategory category =
                categoryRepository.findById(id).orElseThrow(() -> notFound("档案分类不存在"));
        String categoryCode = requireCategoryCode(request.categoryCode());
        if (!category.getCategoryCode().equals(categoryCode)) {
            throw badRequest("分类编码创建后不可修改");
        }
        String categoryName = requireCategoryName(request.categoryName());
        validateParentCategory(id, request.parentId());
        category.setParentId(request.parentId());
        category.setCategoryName(categoryName);
        category.setManagementMode(normalizeManagementMode(request.managementMode()));
        category.setEnabled(request.enabled() == null || request.enabled());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return mapCategory(categoryRepository.update(category));
    }

    @Transactional
    public void deleteCategory(Long id, Long userId) {
        requireId(id);
        if (archiveMapper.countChildCategories(id) > 0) {
            throw badRequest("存在子分类，不能删除");
        }
        ArchiveCategory category =
                categoryRepository.findById(id).orElseThrow(() -> notFound("档案分类不存在"));
        categoryRepository.update(category);
        categoryRepository.delete(category);
    }

    public ArchiveCategoryDto getCategory(Long id) {
        return loadCategory(id);
    }

    public ArchiveCategoryDto getEnabledCategoryByCode(String categoryCode) {
        String normalizedCode = requireCategoryCode(categoryCode);
        ArchiveCategory category = categoryRepository.findByCategoryCode(normalizedCode);
        if (category == null || !category.isEnabled()) {
            throw notFound("档案分类不存在或已禁用");
        }
        return mapCategory(category);
    }

    private ArchiveCategoryDto loadCategory(Long id) {
        requireId(id);
        return categoryRepository
                .findById(id)
                .map(this::mapCategory)
                .orElseThrow(() -> notFound("档案分类不存在"));
    }

    public List<ArchiveFondsCategoryScopeDto> listFondsCategoryScopes(String fondsCode) {
        ArchiveFonds fonds = loadFondsByCode(fondsCode);
        return fondsCategoryScopeRepository.findByFondsCode(fonds.getFondsCode()).stream()
                .map(this::mapFondsCategoryScope)
                .toList();
    }

    @Transactional
    public List<ArchiveFondsCategoryScopeDto> saveFondsCategoryScopes(
            String fondsCode,
            @Nullable List<ArchiveFondsCategoryScopeRequest> requests,
            Long userId) {
        ArchiveFonds fonds = loadFondsByCode(fondsCode);
        List<ArchiveFondsCategoryScopeRequest> normalizedRequests =
                requests == null ? List.of() : requests;
        validateFondsCategoryScopeRequests(normalizedRequests);
        List<ArchiveFondsCategoryScope> existing =
                fondsCategoryScopeRepository.findByFondsCode(fonds.getFondsCode());
        if (!existing.isEmpty()) {
            fondsCategoryScopeRepository.deleteAll(existing);
        }
        if (normalizedRequests.isEmpty()) {
            return List.of();
        }
        List<ArchiveFondsCategoryScope> scopes = new ArrayList<>();
        for (ArchiveFondsCategoryScopeRequest request : normalizedRequests) {
            ArchiveCategory category = loadEnabledScopedCategory(request.categoryId());
            ArchiveFondsCategoryScope scope = new ArchiveFondsCategoryScope();
            scope.setFondsCode(fonds.getFondsCode());
            scope.setCategoryId(category.getId());
            scope.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
            scopes.add(scope);
        }
        return fondsCategoryScopeRepository.insertAll(scopes).stream()
                .map(this::mapFondsCategoryScope)
                .toList();
    }

    public void requireCategoryAvailableForFonds(String fondsCode, Long categoryId) {
        String normalizedFondsCode = StringUtils.trimToNull(fondsCode);
        if (normalizedFondsCode == null) {
            throw new BadRequestException("全宗不能为空", "fondsCode", "全宗不能为空");
        }
        requireId(categoryId);
        if (fondsCategoryScopeRepository
                .findByFondsCodeAndCategoryId(normalizedFondsCode, categoryId)
                .isEmpty()) {
            throw new BadRequestException("该全宗未配置此分类", "categoryId", "该全宗未配置此分类");
        }
    }

    private ArchiveManagementMode normalizeManagementMode(
            @Nullable ArchiveManagementMode managementMode) {
        return managementMode == null ? ArchiveManagementMode.ITEM_ONLY : managementMode;
    }

    private ArchiveFonds loadFondsByCode(String fondsCode) {
        String normalizedCode = StringUtils.trimToNull(fondsCode);
        if (normalizedCode == null) {
            throw badRequest("全宗编码不能为空");
        }
        return fondsRepository.find(normalizedCode).orElseThrow(() -> notFound("全宗不存在"));
    }

    private ArchiveFonds loadEnabledFondsByCode(String fondsCode) {
        String normalizedCode = StringUtils.trimToNull(fondsCode);
        if (normalizedCode == null) {
            throw new BadRequestException("全宗不可用");
        }
        return fondsRepository
                .find(normalizedCode)
                .filter(ArchiveFonds::isEnabled)
                .orElseThrow(() -> new BadRequestException("全宗不可用"));
    }

    private List<ArchiveCategory> scopedCategories(
            List<ArchiveFondsCategoryScope> scopes, @Nullable Boolean enabled) {
        Set<Long> seenCategoryIds = new HashSet<>();
        List<ArchiveCategory> categories = new ArrayList<>();
        for (ArchiveFondsCategoryScope scope : scopes) {
            if (!seenCategoryIds.add(scope.getCategoryId())) {
                continue;
            }
            ArchiveCategory category =
                    categoryRepository.findById(scope.getCategoryId()).orElse(null);
            if (category == null) {
                continue;
            }
            if (enabled != null && category.isEnabled() != enabled) {
                continue;
            }
            categories.add(category);
        }
        return categories;
    }

    private ArchiveCategory loadEnabledScopedCategory(@Nullable Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw badRequest("分类不能为空");
        }
        return categoryRepository
                .findById(categoryId)
                .filter(ArchiveCategory::isEnabled)
                .orElseThrow(() -> badRequest("分类不可用"));
    }

    private void validateFondsCategoryScopeRequests(
            List<ArchiveFondsCategoryScopeRequest> requests) {
        Set<Long> categoryIds = new HashSet<>();
        for (ArchiveFondsCategoryScopeRequest request : requests) {
            Long categoryId = request.categoryId();
            if (categoryId == null || categoryId <= 0) {
                throw badRequest("分类不能为空");
            }
            if (!categoryIds.add(categoryId)) {
                throw badRequest("分类不能重复");
            }
        }
    }

    private void validateParentCategory(@Nullable Long categoryId, @Nullable Long parentId) {
        if (parentId == null) {
            return;
        }
        requireId(parentId);
        categoryRepository.findById(parentId).orElseThrow(() -> notFound("档案分类不存在"));
        if (categoryId == null) {
            return;
        }
        if (categoryId.equals(parentId)) {
            throw badRequest("不能将分类自身设置为父级");
        }
        Long currentParentId = parentId;
        while (currentParentId != null) {
            if (categoryId.equals(currentParentId)) {
                throw badRequest("不能将子分类设置为父级");
            }
            currentParentId = archiveMapper.findParentId(currentParentId);
        }
    }

    private String requireCategoryCode(@Nullable String categoryCode) {
        String normalized = StringUtils.stripToNull(categoryCode);
        if (normalized == null) {
            throw badRequest("分类编码不能为空");
        }
        if (normalized.codePointCount(0, normalized.length()) > CATEGORY_CODE_MAX_LENGTH) {
            throw badRequest("分类编码长度不能超过 100");
        }
        return normalized;
    }

    private String requireCategoryName(@Nullable String categoryName) {
        String normalized = StringUtils.stripToNull(categoryName);
        if (normalized == null) {
            throw badRequest("分类名称不能为空");
        }
        if (normalized.codePointCount(0, normalized.length()) > CATEGORY_NAME_MAX_LENGTH) {
            throw badRequest("分类名称长度不能超过 255");
        }
        return normalized;
    }

    private boolean isCategoryCodeUniqueViolation(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && CATEGORY_CODE_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) {
            throw badRequest("ID 不合法");
        }
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException categoryCodeConflict(RuntimeException exception) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "分类编码已存在", exception);
    }

    private ArchiveFondsCategoryScopeDto mapFondsCategoryScope(ArchiveFondsCategoryScope scope) {
        return new ArchiveFondsCategoryScopeDto(
                scope.getId(),
                scope.getFondsCode(),
                scope.getCategoryId(),
                scope.getSortOrder(),
                scope.getCreatedAt(),
                scope.getUpdatedAt());
    }

    private ArchiveCategoryDto mapCategory(Map<String, Object> row) {
        Number parentId = numberOrNull(row, "parentId");
        return new ArchiveCategoryDto(
                number(row, "id").longValue(),
                parentId == null ? null : parentId.longValue(),
                string(row, "categoryCode"),
                string(row, "categoryName"),
                ArchiveManagementMode.fromValue(string(row, "managementMode")),
                string(row, "volumeTableName"),
                string(row, "itemTableName"),
                string(row, "volumePhysicalTableName"),
                string(row, "itemPhysicalTableName"),
                ArchiveTableStatus.fromValue(string(row, "tableStatus")),
                dateTime(row, "builtAt"),
                bool(row, "enabled"),
                number(row, "sortOrder").intValue(),
                dateTime(row, "createdAt"),
                dateTime(row, "updatedAt"));
    }

    private ArchiveCategoryDto mapCategory(ArchiveCategory category) {
        return new ArchiveCategoryDto(
                category.getId(),
                category.getParentId(),
                category.getCategoryCode(),
                category.getCategoryName(),
                category.getManagementMode(),
                category.getVolumeTableName(),
                category.getItemTableName(),
                category.getVolumePhysicalTableName(),
                category.getItemPhysicalTableName(),
                category.getTableStatus(),
                category.getBuiltAt(),
                category.isEnabled(),
                category.getSortOrder(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }

    private @Nullable String string(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private boolean bool(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private Number number(Map<String, @Nullable Object> row, String key) {
        Number number = numberOrNull(row, key);
        if (number == null) {
            throw new IllegalStateException("缺少数值字段：" + key);
        }
        return number;
    }

    private @Nullable Number numberOrNull(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value instanceof Number number ? number : null;
    }

    private @Nullable LocalDateTime dateTime(Map<String, @Nullable Object> row, String key) {
        Object value = value(row, key);
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    private @Nullable Object value(Map<String, @Nullable Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        return row.get(JdbcUtils.convertPropertyNameToUnderscoreName(key));
    }
}
