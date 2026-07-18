package github.luckygc.am.module.archive.rule.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.ArchiveLevel;
import github.luckygc.am.module.archive.governance.repository.ArchiveGovernanceSchemeVersionDataRepository;
import github.luckygc.am.module.archive.metadata.ArchiveCategory;
import github.luckygc.am.module.archive.metadata.ArchiveField;
import github.luckygc.am.module.archive.metadata.ArchiveFieldDataType;
import github.luckygc.am.module.archive.metadata.ArchiveFieldScope;
import github.luckygc.am.module.archive.metadata.repository.ArchiveCategoryDataRepository;
import github.luckygc.am.module.archive.metadata.repository.ArchiveFieldDataRepository;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeFieldSource;
import github.luckygc.am.module.archive.rule.ArchiveRuntimeTriggerPoint;

@Service
public class ArchiveRuntimeFieldCatalogService {

    private final ArchiveGovernanceSchemeVersionDataRepository schemeVersionRepository;
    private final ArchiveCategoryDataRepository categoryRepository;
    private final ArchiveFieldDataRepository fieldRepository;

    public ArchiveRuntimeFieldCatalogService(
            ArchiveGovernanceSchemeVersionDataRepository schemeVersionRepository,
            ArchiveCategoryDataRepository categoryRepository,
            ArchiveFieldDataRepository fieldRepository) {
        this.schemeVersionRepository = schemeVersionRepository;
        this.categoryRepository = categoryRepository;
        this.fieldRepository = fieldRepository;
    }

    @Transactional(readOnly = true)
    public ArchiveRuntimeFieldCatalog catalog(
            Long schemeVersionId,
            @Nullable String categoryCode,
            ArchiveRuntimeTriggerPoint triggerPoint) {
        schemeVersionRepository
                .findById(schemeVersionId)
                .orElseThrow(() -> new BadRequestException("治理版本不存在"));
        String normalizedCategoryCode = StringUtils.trimToNull(categoryCode);
        ArchiveCategory category = resolveCategory(normalizedCategoryCode);
        List<ArchiveRuntimeField> fields = new ArrayList<>();
        if (triggerPoint != ArchiveRuntimeTriggerPoint.EXPORT_BEFORE_CREATE) {
            addFixedFields(fields, triggerPoint);
            if (category != null) {
                addDynamicFields(fields, category, triggerPoint);
            }
        }
        addContextFields(fields, triggerPoint);
        List<ArchiveRuntimeField> ordered =
                fields.stream()
                        .sorted(Comparator.comparing(ArchiveRuntimeField::fieldCode))
                        .toList();
        return new ArchiveRuntimeFieldCatalog(
                schemeVersionId, normalizedCategoryCode, triggerPoint, signature(ordered), ordered);
    }

    private @Nullable ArchiveCategory resolveCategory(@Nullable String categoryCode) {
        if (categoryCode == null) {
            return null;
        }
        ArchiveCategory category = categoryRepository.findByCategoryCode(categoryCode);
        if (category == null || !category.isEnabled()) {
            throw new BadRequestException("分类不存在或未启用", "categoryCode", "分类不存在或未启用");
        }
        return category;
    }

    private void addFixedFields(
            List<ArchiveRuntimeField> fields, ArchiveRuntimeTriggerPoint triggerPoint) {
        boolean writable = triggerPoint.fieldAssignmentAllowed();
        String prefix = triggerPoint.archiveLevel() == ArchiveLevel.VOLUME ? "volume." : "item.";
        fields.add(field(prefix + "id", "主键", ArchiveFieldDataType.INTEGER, false));
        fields.add(field(prefix + "fondsCode", "全宗编码", ArchiveFieldDataType.TEXT, false));
        fields.add(field(prefix + "fondsName", "全宗名称", ArchiveFieldDataType.TEXT, false));
        fields.add(field(prefix + "categoryCode", "分类编码", ArchiveFieldDataType.TEXT, false));
        fields.add(field(prefix + "categoryName", "分类名称", ArchiveFieldDataType.TEXT, false));
        fields.add(field(prefix + "archiveNo", "档号", ArchiveFieldDataType.TEXT, writable));
        fields.add(field(prefix + "archiveYear", "归档年度", ArchiveFieldDataType.INTEGER, writable));
        boolean itemWritable = writable && triggerPoint.archiveLevel() == ArchiveLevel.ITEM;
        fields.add(
                field(
                        prefix + "securityLevelId",
                        "密级",
                        ArchiveFieldDataType.REFERENCE,
                        itemWritable));
        fields.add(
                field(
                        prefix + "retentionPeriodId",
                        "保管期限",
                        ArchiveFieldDataType.REFERENCE,
                        itemWritable));
        fields.add(field(prefix + "electronicStatus", "电子状态", ArchiveFieldDataType.ENUM, writable));
        if (triggerPoint == ArchiveRuntimeTriggerPoint.VOLUME_BEFORE_ADD_ITEM) {
            fields.add(field("item.id", "条目主键", ArchiveFieldDataType.INTEGER, false));
            fields.add(field("item.archiveNo", "条目档号", ArchiveFieldDataType.TEXT, false));
            fields.add(field("item.archiveYear", "条目归档年度", ArchiveFieldDataType.INTEGER, false));
        }
    }

    private ArchiveRuntimeField field(
            String code, String name, ArchiveFieldDataType dataType, boolean writable) {
        return new ArchiveRuntimeField(
                code, name, dataType, ArchiveRuntimeFieldSource.FIXED, true, writable, null);
    }

    private void addDynamicFields(
            List<ArchiveRuntimeField> fields,
            ArchiveCategory category,
            ArchiveRuntimeTriggerPoint triggerPoint) {
        ArchiveLevel level = triggerPoint.archiveLevel();
        addDynamicFields(fields, category, level, ArchiveFieldScope.METADATA, triggerPoint);
        addDynamicFields(fields, category, level, ArchiveFieldScope.PHYSICAL, triggerPoint);
    }

    private void addDynamicFields(
            List<ArchiveRuntimeField> fields,
            ArchiveCategory category,
            ArchiveLevel level,
            ArchiveFieldScope fieldScope,
            ArchiveRuntimeTriggerPoint triggerPoint) {
        boolean writable = triggerPoint.fieldAssignmentAllowed();
        ArchiveRuntimeFieldSource source =
                fieldScope == ArchiveFieldScope.METADATA
                        ? ArchiveRuntimeFieldSource.METADATA
                        : ArchiveRuntimeFieldSource.PHYSICAL;
        String prefix = fieldScope == ArchiveFieldScope.METADATA ? "metadata." : "physical.";
        for (ArchiveField field : fieldRepository.list(category.getId(), level, fieldScope, true)) {
            fields.add(
                    new ArchiveRuntimeField(
                            prefix + field.getFieldCode(),
                            field.getFieldName(),
                            ArchiveFieldDataType.valueOf(field.getFieldType().name()),
                            source,
                            true,
                            writable && field.isEditVisible(),
                            category.getCategoryCode()));
        }
    }

    private void addContextFields(
            List<ArchiveRuntimeField> fields, ArchiveRuntimeTriggerPoint triggerPoint) {
        fields.add(
                new ArchiveRuntimeField(
                        "context.userId",
                        "当前用户",
                        ArchiveFieldDataType.INTEGER,
                        ArchiveRuntimeFieldSource.CONTEXT,
                        true,
                        false,
                        null));
        fields.add(
                new ArchiveRuntimeField(
                        "context.now",
                        "当前时间",
                        ArchiveFieldDataType.DATETIME,
                        ArchiveRuntimeFieldSource.CONTEXT,
                        true,
                        false,
                        null));
        fields.add(
                new ArchiveRuntimeField(
                        "context.operation",
                        "当前操作",
                        ArchiveFieldDataType.ENUM,
                        ArchiveRuntimeFieldSource.CONTEXT,
                        true,
                        false,
                        null));
        if (triggerPoint == ArchiveRuntimeTriggerPoint.FILE_BEFORE_UPLOAD) {
            fields.add(fileField("file.name", "文件名", ArchiveFieldDataType.TEXT));
            fields.add(fileField("file.contentType", "内容类型", ArchiveFieldDataType.TEXT));
            fields.add(fileField("file.size", "文件大小", ArchiveFieldDataType.INTEGER));
        }
        if (triggerPoint == ArchiveRuntimeTriggerPoint.EXPORT_BEFORE_CREATE) {
            fields.add(exportField("export.itemCount", "导出条目数", ArchiveFieldDataType.INTEGER));
            fields.add(exportField("export.format", "导出格式", ArchiveFieldDataType.ENUM));
        }
    }

    private ArchiveRuntimeField fileField(String code, String name, ArchiveFieldDataType dataType) {
        return new ArchiveRuntimeField(
                code, name, dataType, ArchiveRuntimeFieldSource.FILE, true, false, null);
    }

    private ArchiveRuntimeField exportField(
            String code, String name, ArchiveFieldDataType dataType) {
        return new ArchiveRuntimeField(
                code, name, dataType, ArchiveRuntimeFieldSource.EXPORT, true, false, null);
    }

    private String signature(List<ArchiveRuntimeField> fields) {
        StringBuilder canonical = new StringBuilder();
        for (ArchiveRuntimeField field : fields) {
            canonical
                    .append(field.fieldCode())
                    .append('|')
                    .append(field.dataType())
                    .append('|')
                    .append(field.readable())
                    .append('|')
                    .append(field.writable())
                    .append('\n');
        }
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    public record ArchiveRuntimeFieldCatalog(
            Long schemeVersionId,
            @Nullable String categoryCode,
            ArchiveRuntimeTriggerPoint triggerPoint,
            String signature,
            List<ArchiveRuntimeField> fields) {

        public ArchiveRuntimeFieldCatalog {
            fields = List.copyOf(fields);
        }

        public Map<String, ArchiveRuntimeField> fieldsByCode() {
            Map<String, ArchiveRuntimeField> result = new LinkedHashMap<>();
            fields.forEach(field -> result.put(field.fieldCode(), field));
            return Map.copyOf(result);
        }
    }

    public record ArchiveRuntimeField(
            String fieldCode,
            String fieldName,
            ArchiveFieldDataType dataType,
            ArchiveRuntimeFieldSource source,
            boolean readable,
            boolean writable,
            @Nullable String categoryCode) {}
}
