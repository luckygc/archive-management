package github.luckygc.am.module.archive.item.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import github.luckygc.am.common.api.JsonMergePatch;
import github.luckygc.am.common.exception.BadRequestException;
import github.luckygc.am.module.archive.item.service.ArchiveItemReadService.ArchiveItemDetailDto;
import github.luckygc.am.module.archive.item.service.ArchiveItemService.UpdateArchiveItemRequest;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** 将详情表示的合并补丁转换为候选修改，不读取或写入档案数据。 */
final class ArchiveItemPatchParser {

    private static final Set<String> TOP_FIELDS =
            Set.of("item", "dynamicFields", "physicalFieldValues");
    private static final Set<String> TOP_READ_ONLY_FIELDS =
            Set.of("category", "fields", "physicalFields");
    private static final Set<String> ITEM_FIELDS =
            Set.of(
                    "volumeId",
                    "fondsCode",
                    "archiveNo",
                    "archiveYear",
                    "securityLevelId",
                    "retentionPeriodId");
    private static final Set<String> ITEM_READ_ONLY_FIELDS =
            Set.of(
                    "id",
                    "fondsName",
                    "categoryCode",
                    "categoryName",
                    "lockedFlag",
                    "lockReason",
                    "lockedBy",
                    "lockedAt",
                    "repositoryId",
                    "archivedAt");
    private static final TypeReference<Map<String, @Nullable Object>> FIELD_VALUES_TYPE =
            new TypeReference<>() {};

    private final JsonMapper jsonMapper;

    ArchiveItemPatchParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Nullable PreparedPatch parse(ArchiveItemDetailDto before, JsonNode patch) {
        requirePatchObject(patch, TOP_FIELDS, TOP_READ_ONLY_FIELDS, "");
        JsonNode itemPatch = patch.get("item");
        if (itemPatch != null) {
            requirePatchObject(itemPatch, ITEM_FIELDS, ITEM_READ_ONLY_FIELDS, "item");
        }
        for (String field : List.of("dynamicFields", "physicalFieldValues")) {
            JsonNode fieldPatch = patch.get(field);
            if (fieldPatch != null && !fieldPatch.isObject()) {
                throw new BadRequestException(field + " 补丁必须是对象", field, "字段值必须是对象");
            }
        }
        ObjectNode representation = JsonNodeFactory.instance.objectNode();
        ObjectNode item = (ObjectNode) jsonMapper.valueToTree(before.item());
        for (String field : ITEM_READ_ONLY_FIELDS) {
            item.remove(field);
        }
        representation.set("item", item);
        representation.set(
                "dynamicFields",
                jsonMapper.valueToTree(nonNullFieldValues(before.dynamicFields())));
        representation.set(
                "physicalFieldValues",
                jsonMapper.valueToTree(nonNullFieldValues(before.physicalFieldValues())));
        JsonNode merged = JsonMergePatch.apply(representation, patch);
        if (merged.equals(representation)) {
            return null;
        }
        JsonNode mergedItem = merged.get("item");
        String fondsCode = requiredPatchText(mergedItem, "fondsCode");
        JsonNode yearNode = mergedItem.get("archiveYear");
        if (yearNode == null || !yearNode.isInt()) {
            throw new BadRequestException("archiveYear 不能删除或改变类型", "item.archiveYear", "年度不合法");
        }
        Set<String> removedItemFields = new HashSet<>();
        if (itemPatch != null) {
            for (String field : itemPatch.propertyNames()) {
                if (itemPatch.get(field).isNull()) {
                    removedItemFields.add(field);
                }
            }
        }
        UpdateArchiveItemRequest request =
                new UpdateArchiveItemRequest(
                        optionalPatchLong(mergedItem, "volumeId"),
                        fondsCode,
                        optionalPatchText(mergedItem, "archiveNo"),
                        yearNode.intValue(),
                        optionalPatchLong(mergedItem, "securityLevelId"),
                        optionalPatchLong(mergedItem, "retentionPeriodId"),
                        patch.has("physicalFieldValues")
                                ? patchFieldValues(merged.get("physicalFieldValues"))
                                : null,
                        patch.has("dynamicFields")
                                ? patchFieldValues(merged.get("dynamicFields"))
                                : null);
        return new PreparedPatch(request, Set.copyOf(removedItemFields));
    }

    private void requirePatchObject(
            @Nullable JsonNode patch, Set<String> fields, Set<String> readOnlyFields, String path) {
        if (patch == null || !patch.isObject()) {
            throw new BadRequestException(path + " 补丁必须是对象");
        }
        for (String field : patch.propertyNames()) {
            if (readOnlyFields.contains(field)
                    || (!fields.contains(field) && !patch.get(field).isNull())) {
                throw new BadRequestException(
                        "不支持修改字段 " + field, path.isEmpty() ? field : path + "." + field, "字段不可修改");
            }
        }
    }

    private String requiredPatchText(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null || !value.isTextual()) {
            throw new BadRequestException(field + " 不能删除或改变类型", "item." + field, "字段不合法");
        }
        return value.asText();
    }

    private @Nullable String optionalPatchText(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isTextual()) {
            throw new BadRequestException(field + " 类型不合法", "item." + field, "必须是字符串");
        }
        return value.asText();
    }

    private @Nullable Long optionalPatchLong(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isIntegralNumber()) {
            throw new BadRequestException(field + " 类型不合法", "item." + field, "必须是整数");
        }
        return value.longValue();
    }

    private Map<String, @Nullable Object> patchFieldValues(JsonNode value) {
        try {
            return jsonMapper.readValue(value.toString(), FIELD_VALUES_TYPE);
        } catch (JacksonException exception) {
            throw new BadRequestException("动态字段补丁格式不合法");
        }
    }

    private Map<String, Object> nonNullFieldValues(Map<String, @Nullable Object> values) {
        Map<String, Object> present = new LinkedHashMap<>();
        values.forEach(
                (field, value) -> {
                    if (value != null) {
                        present.put(field, value);
                    }
                });
        return present;
    }

    record PreparedPatch(UpdateArchiveItemRequest request, Set<String> removedItemFields) {}
}
